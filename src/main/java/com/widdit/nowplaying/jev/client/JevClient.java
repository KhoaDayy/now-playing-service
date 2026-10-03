package com.widdit.nowplaying.jev.client;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.jev.config.JevProperties;
import com.widdit.nowplaying.jev.model.JevAnswer;
import com.widdit.nowplaying.jev.model.JevQuestion;
import com.widdit.nowplaying.jev.model.JevRequest;
import com.widdit.nowplaying.jev.model.JevResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client trung tâm giao tiếp với TypeSafe Jev System One.
 * Tách biệt hoàn toàn việc kết nối, xác thực, HTTP request, timeout, in-memory cache,
 * và xử lý lỗi khỏi các tầng nghiệp vụ (Title Parser, Song Matching, v.v.).
 */
@Service
@Slf4j
public class JevClient {

    private final JevProperties properties;
    private HttpClient httpClient;
    // Serialized snapshots keep callers from mutating answers held in the cache.
    private Cache<String, String> cache;
    private final Map<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong retryAfterTimeMs = new AtomicLong();

    @Autowired
    public JevClient(JevProperties properties) {
        this.properties = properties;
        init();
    }

    @PostConstruct
    public synchronized void init() {
        if (this.httpClient != null) {
            return;
        }
        properties.init();
        int timeoutMs = properties.getTimeoutMs();

        // Khởi tạo HTTP Client chuẩn Java 11 với connection timeout
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // Khởi tạo In-memory LRU Cache có TTL
        if (properties.isCacheEnabled()) {
            long ttlMs = (long) Math.max(properties.getCacheTtlMinutes(), 1) * 60 * 1000;
            int capacity = Math.max(properties.getCacheCapacity(), 1);
            this.cache = CacheUtil.newLRUCache(capacity, ttlMs);
            log.info("JevClient: Đã kích hoạt LRU cache (Sức chứa: {}, TTL: {} phút)",
                    capacity, properties.getCacheTtlMinutes());
        }

        if (properties.isOperational()) {
            log.info("JevClient: Sẵn sàng hoạt động (Model: {}, Endpoint: {}, Timeout: {}ms)",
                    properties.getModel(), properties.getApiUrl(), properties.getTimeoutMs());
        } else {
            log.info("JevClient: Tạm thời vô hiệu hóa hoặc chưa có API Key. Tất cả yêu cầu sẽ tự động dùng Fallback.");
        }
    }

    /**
     * Kiểm tra nhanh xem JevClient có thể nhận request không.
     */
    public boolean isAvailable() {
        return properties != null && properties.isOperational();
    }

    /**
     * Gửi yêu cầu đánh giá đa câu hỏi tới Jev.
     * Tự động kiểm tra cache, kiểm tra timeout, parse typed answers và xử lý lỗi mà không ném exception ra ngoài.
     */
    public JevResponse evaluate(JevRequest request) {
        if (!isAvailable()) {
            return JevResponse.failure("JevClient chưa được kích hoạt hoặc thiếu API Key", 0);
        }

        if (request == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            return JevResponse.failure("Request rỗng hoặc không có câu hỏi nào", 0);
        }

        long startTime = System.nanoTime();
        CompletableFuture<String> pending = null;
        String cacheKey = null;
        try {
            // Snapshot the request once, without changing the caller's model or maps.
            JevRequest snapshot = JevRequest.builder()
                    .state(request.getState())
                    .model(request.getModel() == null || request.getModel().isBlank()
                            ? properties.getModel() : request.getModel())
                    .questions(request.getQuestions())
                    .build();
            String jsonPayload = JSON.toJSONString(canonicalize(JSON.toJSON(snapshot)));
            JevRequest wireRequest = JSON.parseObject(jsonPayload, JevRequest.class);
            validateRequest(wireRequest);
            cacheKey = generateCacheKey(jsonPayload);

            String cached = getCached(cacheKey);
            if (cached != null) {
                return readSnapshot(cached, true);
            }

            CompletableFuture<String> newPending = new CompletableFuture<>();
            CompletableFuture<String> existing = inFlight.putIfAbsent(cacheKey, newPending);
            if (existing != null) {
                return readSnapshot(existing.get(properties.getTimeoutMs(), TimeUnit.MILLISECONDS), false);
            }
            pending = newPending;
            // The previous owner may have filled the cache between our first check and registration.
            cached = getCached(cacheKey);
            if (cached != null) {
                pending.complete(cached);
                return readSnapshot(cached, true);
            }
            if (System.currentTimeMillis() < retryAfterTimeMs.get()) {
                JevResponse failure = JevResponse.failure("Jev API đang tạm nghỉ sau lỗi dịch vụ", 0);
                pending.complete(JSON.toJSONString(failure));
                return failure;
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getApiUrl()))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("User-Agent", "NowPlayingService-JevClient/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = elapsedMs(startTime);

            int statusCode = httpResponse.statusCode();
            String responseBody = httpResponse.body();

            JevResponse response;
            if (statusCode == 200) {
                response = parseResponseBody(responseBody, latencyMs, wireRequest);
                if (response.isSuccess() && properties.isCacheEnabled() && cache != null && cacheKey != null) {
                    cache.put(cacheKey, JSON.toJSONString(response));
                }
                if (!response.isSuccess()) {
                    pauseRemoteCalls(0);
                }
            } else {
                // Response bodies can echo input or credentials; log only the status.
                log.warn("JevClient: Lỗi HTTP {} từ API TypeSafe ({}ms)", statusCode, latencyMs);
                pauseRemoteCalls(retryDelayMs(httpResponse));
                response = JevResponse.failure("HTTP error " + statusCode, latencyMs);
            }
            pending.complete(JSON.toJSONString(response));
            return response;

        } catch (HttpTimeoutException e) {
            pauseRemoteCalls(0);
            return completeFailure(pending, "Timeout", startTime);

        } catch (IOException e) {
            pauseRemoteCalls(0);
            return completeFailure(pending, "Lỗi kết nối Jev API", startTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return completeFailure(pending, "Request bị gián đoạn", startTime);

        } catch (Exception e) {
            log.debug("JevClient: Không thể xử lý request ({})", e.getClass().getSimpleName());
            return completeFailure(pending, "Request hoặc phản hồi Jev không hợp lệ", startTime);
        } finally {
            if (pending != null) {
                inFlight.remove(cacheKey, pending);
            }
        }
    }

    /**
     * Tiện ích gửi state và danh sách câu hỏi.
     */
    public JevResponse evaluate(Object state, Map<String, JevQuestion> questions) {
        JevRequest request = JevRequest.builder()
                .state(state)
                .model(properties.getModel())
                .questions(questions)
                .build();
        return evaluate(request);
    }

    /**
     * MẪU TÍCH HỢP TỰ ĐỘNG FALLBACK (An toàn tuyệt đối cho Business Logic):
     * Nếu Jev bị tắt, hết hạn API, timeout hoặc gặp bất kỳ lỗi nào,
     * tự động gọi fallbackSupplier mà không làm gián đoạn luồng xử lý.
     *
     * @param state Bối cảnh đánh giá
     * @param questions Tập câu hỏi
     * @param fallbackSupplier Hàm sinh kết quả dự phòng khi Jev không khả dụng
     * @param mapper Hàm biến đổi kết quả JevResponse thành đầu ra mong muốn.
     *               Nếu mapper trả về null (ví dụ: confidence không đủ cao), sẽ tự động gọi fallbackSupplier.
     */
    public <T> T evaluateWithFallback(
            Object state,
            Map<String, JevQuestion> questions,
            Supplier<T> fallbackSupplier,
            Function<JevResponse, T> mapper
    ) {
        if (!isAvailable()) {
            return fallbackSupplier.get();
        }

        T result = null;
        try {
            JevResponse response = evaluate(state, questions);
            if (response.isSuccess()) {
                result = mapper.apply(response);
            }
        } catch (Exception e) {
            log.debug("JevClient: Không thể sử dụng kết quả Jev, chuyển sang Fallback");
        }
        // Supplier failures belong to the caller; never run a failing fallback twice.
        return result != null ? result : fallbackSupplier.get();
    }

    /**
     * Tiện ích gọi nhanh 1 câu hỏi Choice.
     */
    public JevAnswer askChoice(Object state, String questionText, Map<String, ?> criteria) {
        Map<String, JevQuestion> questions = Collections.singletonMap(
                "q", JevQuestion.choice(questionText, criteria)
        );
        JevResponse response = evaluate(state, questions);
        return response.getAnswer("q");
    }

    /**
     * Tiện ích gọi nhanh 1 câu hỏi Noul (Yes/No).
     */
    public Double askNoul(Object state, String questionText) {
        Map<String, JevQuestion> questions = Collections.singletonMap(
                "q", JevQuestion.noul(questionText)
        );
        JevResponse response = evaluate(state, questions);
        return response.getNoul("q");
    }

    /**
     * Tiện ích gọi nhanh 1 câu hỏi Noul kèm tiêu chí true/false.
     */
    public Double askNoul(Object state, String questionText, Object criteria) {
        Map<String, JevQuestion> questions = Collections.singletonMap(
                "q", JevQuestion.noul(questionText, criteria)
        );
        JevResponse response = evaluate(state, questions);
        return response.getNoul("q");
    }

    /**
     * Tiện ích gọi nhanh 1 câu hỏi Score.
     */
    public JevAnswer askScore(Object state, String questionText, List<?> criteria) {
        Map<String, JevQuestion> questions = Collections.singletonMap(
                "q", JevQuestion.score(questionText, criteria)
        );
        JevResponse response = evaluate(state, questions);
        return response.getAnswer("q");
    }

    /**
     * Xóa toàn bộ bộ nhớ đệm cache.
     */
    public void clearCache() {
        if (cache != null) {
            cache.clear();
            log.info("JevClient: Đã xóa toàn bộ in-memory cache");
        }
    }

    /**
     * Lấy kích thước cache hiện tại.
     */
    public int getCacheSize() {
        return cache != null ? cache.size() : 0;
    }

    // ==================== Các phương thức nội bộ ====================

    /**
     * Phân tích phản hồi JSON từ TypeSafe Jev System One.
     */
    private JevResponse parseResponseBody(String responseBody, long latencyMs, JevRequest request) {
        try {
            JSONObject root = JSON.parseObject(responseBody);
            String model = root.getString("model");
            JSONObject answersObj = root.getJSONObject("answers");
            JSONObject usageObj = root.getJSONObject("usage");

            if (answersObj == null || !answersObj.keySet().equals(request.getQuestions().keySet())) {
                throw new IllegalArgumentException("Response must answer exactly the requested questions");
            }

            Map<String, JevAnswer> answerMap = new HashMap<>();
            if (answersObj != null) {
                for (String qId : answersObj.keySet()) {
                    JSONObject ansJson = answersObj.getJSONObject(qId);
                    if (ansJson == null) throw new IllegalArgumentException("Missing answer");
                    validateAnswer(ansJson, request.getQuestions().get(qId));

                    Map<String, String> legend = null;
                    JSONObject legendObj = ansJson.getJSONObject("legend");
                    if (legendObj != null) {
                        legend = new HashMap<>();
                        for (String lKey : legendObj.keySet()) {
                            legend.put(lKey, legendObj.getString(lKey));
                        }
                    }

                    JevAnswer answer = JevAnswer.builder()
                            .type(ansJson.getString("type"))
                            .choice(ansJson.getString("choice"))
                            .noul(ansJson.getDouble("noul"))
                            .score(ansJson.getDouble("score"))
                            .confidence(ansJson.getDouble("confidence"))
                            .probabilities(ansJson.get("probabilities"))
                            .legend(legend)
                            .build();

                    answerMap.put(qId, answer);
                }
            }

            Map<String, Integer> usage = null;
            if (usageObj != null) {
                usage = new HashMap<>();
                usage.put("input_tokens", usageObj.getInteger("input_tokens"));
                usage.put("output_tokens", usageObj.getInteger("output_tokens"));
            }

            return JevResponse.builder()
                    .success(true)
                    .model(model)
                    .answers(answerMap)
                    .usage(usage)
                    .latencyMs(latencyMs)
                    .fromCache(false)
                    .build();

        } catch (Exception e) {
            log.debug("JevClient: Phản hồi không khớp hợp đồng API");
            return JevResponse.failure("Phản hồi không khớp hợp đồng Jev API", latencyMs);
        }
    }

    /**
     * Tạo khóa băm SHA-256 an toàn cho bộ nhớ đệm cache.
     */
    private String generateCacheKey(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String getCached(String key) {
        // Hutool's default get refreshes TTL. Expiry must be measured from inference time.
        return properties.isCacheEnabled() && cache != null ? cache.get(key, false) : null;
    }

    private JevResponse readSnapshot(String snapshot, boolean fromCache) {
        JevResponse response = JSON.parseObject(snapshot, JevResponse.class);
        response.setFromCache(fromCache);
        if (fromCache) response.setLatencyMs(0);
        return response;
    }

    private JevResponse completeFailure(CompletableFuture<String> pending, String message, long startTime) {
        JevResponse response = JevResponse.failure(message, elapsedMs(startTime));
        if (pending != null) pending.complete(JSON.toJSONString(response));
        return response;
    }

    private long elapsedMs(long startTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
    }

    private void pauseRemoteCalls(long serverDelayMs) {
        long delay = Math.max(Math.max(0, properties.getFailureCooldownMs()), serverDelayMs);
        long until = System.currentTimeMillis() + delay;
        retryAfterTimeMs.accumulateAndGet(until, Math::max);
    }

    private long retryDelayMs(HttpResponse<?> response) {
        long delay = 0;
        try {
            delay = Math.max(0, Long.parseLong(response.headers().firstValue("retry-after-ms").orElse("0")));
        } catch (NumberFormatException ignored) {
        }
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try {
            delay = Math.max(delay, Math.multiplyExact(Long.parseLong(retryAfter), 1000L));
        } catch (NumberFormatException e) {
            try {
                long deadline = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                delay = Math.max(delay, deadline - System.currentTimeMillis());
            } catch (Exception ignored) {
            }
        } catch (ArithmeticException ignored) {
        }
        return Math.min(Math.max(0, delay), TimeUnit.DAYS.toMillis(1));
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map) {
            Map<String, Object> sorted = new TreeMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof List) {
            List<Object> items = new ArrayList<>();
            for (Object item : (List<?>) value) items.add(canonicalize(item));
            return items;
        }
        return value;
    }

    private void validateRequest(JevRequest request) {
        if (request.getState() == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("State and questions are required");
        }
        request.getQuestions().forEach((id, question) -> {
            if (id == null || id.isBlank() || question == null || question.getInstructions() == null) {
                throw new IllegalArgumentException("Invalid question");
            }
            switch (question.getType() == null ? "" : question.getType()) {
                case "choice":
                    if (!(question.getCriteria() instanceof Map) || ((Map<?, ?>) question.getCriteria()).isEmpty()) {
                        throw new IllegalArgumentException("Choice requires options");
                    }
                    break;
                case "score":
                    if (!(question.getCriteria() instanceof List) || ((List<?>) question.getCriteria()).isEmpty()) {
                        throw new IllegalArgumentException("Score requires levels");
                    }
                    break;
                case "noul":
                    break;
                default:
                    throw new IllegalArgumentException("Unknown question type");
            }
        });
    }

    private void validateAnswer(JSONObject answer, JevQuestion question) {
        if (!question.getType().equals(answer.getString("type"))) {
            throw new IllegalArgumentException("Wrong answer type");
        }
        if ("noul".equals(question.getType())) {
            requireNumber(answer.get("noul"), 0, 1);
            return;
        }
        requireNumber(answer.get("confidence"), 0, 1);
        Set<String> options = new HashSet<>();
        if ("choice".equals(question.getType())) {
            for (Object option : ((Map<?, ?>) question.getCriteria()).keySet()) options.add(String.valueOf(option));
            if (!(answer.get("choice") instanceof String) || !options.contains(answer.getString("choice"))) {
                throw new IllegalArgumentException("Unknown choice");
            }
        } else {
            int levels = ((List<?>) question.getCriteria()).size();
            requireNumber(answer.get("score"), 0, levels - 1);
            for (int i = 0; i < levels; i++) options.add(String.valueOf(i));
            JSONObject legend = answer.getJSONObject("legend");
            if (legend == null || !legend.keySet().equals(options)) {
                throw new IllegalArgumentException("Invalid score legend");
            }
        }
        Object probabilities = answer.get("probabilities");
        if (!(probabilities instanceof Map) || !((Map<?, ?>) probabilities).keySet().equals(options)) {
            throw new IllegalArgumentException("Invalid probability distribution");
        }
        double total = 0;
        for (Object probability : ((Map<?, ?>) probabilities).values()) total += requireNumber(probability, 0, 1);
        if (Math.abs(total - 1.0) > 0.01) throw new IllegalArgumentException("Probabilities must sum to one");
    }

    private double requireNumber(Object raw, double min, double max) {
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Expected numeric answer");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Answer outside allowed range");
        }
        return value;
    }
}

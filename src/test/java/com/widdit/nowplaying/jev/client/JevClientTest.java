package com.widdit.nowplaying.jev.client;

import com.alibaba.fastjson.JSON;
import com.widdit.nowplaying.jev.config.JevProperties;
import com.widdit.nowplaying.jev.model.JevQuestion;
import com.widdit.nowplaying.jev.model.JevRequest;
import com.widdit.nowplaying.jev.model.JevResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JevClientTest {

    private static final String VALID_CHOICE = "{\"model\":\"test-model\",\"answers\":{\"q\":{"
            + "\"type\":\"choice\",\"choice\":\"a\",\"confidence\":0.9,"
            + "\"probabilities\":{\"a\":0.9,\"b\":0.1}}},"
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicLong delayMs = new AtomicLong();
    private final AtomicReference<String> responseBody = new AtomicReference<>(VALID_CHOICE);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void startLocalEndpoint() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/evaluate", this::respond);
        server.start();
    }

    @AfterEach
    void stopLocalEndpoint() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void acceptsAllDocumentedAnswerTypes() {
        responseBody.set("{\"model\":\"test-model\",\"answers\":{"
                + "\"choice\":{\"type\":\"choice\",\"choice\":\"a\",\"confidence\":0.9,"
                + "\"probabilities\":{\"a\":0.9,\"b\":0.1}},"
                + "\"noul\":{\"type\":\"noul\",\"noul\":0.8},"
                + "\"score\":{\"type\":\"score\",\"score\":1.05,\"confidence\":0.92,"
                + "\"probabilities\":{\"0\":0.0,\"1\":0.95,\"2\":0.05},"
                + "\"legend\":{\"0\":\"Calm\",\"1\":\"Frustrated\",\"2\":\"Very angry\"}}}}" );
        Map<String, JevQuestion> questions = new LinkedHashMap<>();
        questions.put("choice", choiceQuestion());
        questions.put("noul", JevQuestion.noul("Is it the right song?"));
        questions.put("score", JevQuestion.score("How angry?",
                Arrays.asList("Calm", "Frustrated", "Very angry")));

        JevResponse result = newClient(0, 1000).evaluate("track", questions);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("a", result.getChoice("choice"));
        assertEquals(0.8, result.getNoul("noul"));
        assertEquals(1.05, result.getScore("score"));
        assertEquals("Frustrated", result.getAnswer("score").getLegend().get("1"));
    }

    @ParameterizedTest(name = "Reject invalid choice payload: {0}")
    @MethodSource("invalidChoicePayloads")
    void invalidResponsesFailAndAreNeverCached(String description, String payload) {
        JevClient client = newClient(0, 1000);
        responseBody.set(payload);

        JevResponse invalid = client.evaluate("track", choiceQuestions());

        assertFalse(invalid.isSuccess(), description);
        assertEquals(0, client.getCacheSize());
        responseBody.set(VALID_CHOICE);
        JevResponse recovered = client.evaluate("track", choiceQuestions());
        assertTrue(recovered.isSuccess(), recovered.getErrorMessage());
        assertFalse(recovered.isFromCache());
        assertEquals(2, calls.get());
    }

    static Stream<Arguments> invalidChoicePayloads() {
        return Stream.of(
                Arguments.of("malformed JSON", "not-json"),
                Arguments.of("missing answers", "{}"),
                Arguments.of("missing requested answer", "{\"answers\":{}}"),
                Arguments.of("wrong answer type", answer("{\"type\":\"noul\",\"noul\":0.9}")),
                Arguments.of("unknown choice", choiceAnswer("unknown", "0.9", "{\"a\":0.9,\"b\":0.1}")),
                Arguments.of("missing confidence", answer("{\"type\":\"choice\",\"choice\":\"a\","
                        + "\"probabilities\":{\"a\":0.9,\"b\":0.1}}")),
                Arguments.of("confidence above one", choiceAnswer("a", "1.2", "{\"a\":0.9,\"b\":0.1}")),
                Arguments.of("negative confidence", choiceAnswer("a", "-0.1", "{\"a\":0.9,\"b\":0.1}")),
                Arguments.of("missing probabilities", answer("{\"type\":\"choice\",\"choice\":\"a\",\"confidence\":0.9}")),
                Arguments.of("probability above one", choiceAnswer("a", "0.9", "{\"a\":1.2,\"b\":0.1}")),
                Arguments.of("negative probability", choiceAnswer("a", "0.9", "{\"a\":1.1,\"b\":-0.1}")),
                Arguments.of("unknown probability key", choiceAnswer("a", "0.9", "{\"a\":0.8,\"b\":0.1,\"c\":0.1}")),
                Arguments.of("missing probability key", choiceAnswer("a", "0.9", "{\"a\":1.0}")),
                Arguments.of("probability sum invalid", choiceAnswer("a", "0.9", "{\"a\":0.2,\"b\":0.2}"))
        );
    }

    @ParameterizedTest(name = "Reject invalid typed answer: {0}")
    @MethodSource("invalidTypedPayloads")
    void rejectsOutOfRangeNoulAndScore(String description, JevQuestion question, String payload) {
        responseBody.set(payload);
        JevClient client = newClient(0, 1000);

        JevResponse result = client.evaluate("track", Collections.singletonMap("q", question));

        assertFalse(result.isSuccess(), description);
        assertEquals(0, client.getCacheSize());
    }

    static Stream<Arguments> invalidTypedPayloads() {
        JevQuestion noul = JevQuestion.noul("Is this true?");
        JevQuestion score = JevQuestion.score("Rate it", Arrays.asList("Low", "Medium", "High"));
        return Stream.of(
                Arguments.of("noul above one", noul, answer("{\"type\":\"noul\",\"noul\":1.1}")),
                Arguments.of("negative noul", noul, answer("{\"type\":\"noul\",\"noul\":-0.1}")),
                Arguments.of("missing noul", noul, answer("{\"type\":\"noul\"}")),
                Arguments.of("score exceeds rubric", score, scoreAnswer("999", "{\"0\":0.0,\"1\":1.0,\"2\":0.0}")),
                Arguments.of("negative score", score, scoreAnswer("-1", "{\"0\":0.0,\"1\":1.0,\"2\":0.0}")),
                Arguments.of("score probability array", score, scoreAnswer("1", "[0.0,1.0,0.0]"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void cachedResponsesAreDeepCopiesIncludingNestedAnswersAndUsage() {
        JevClient client = newClient(0, 1000);
        JevResponse first = client.evaluate("track", choiceQuestions());
        first.getAnswer("q").setChoice("b");
        ((Map<String, Object>) first.getAnswer("q").getProbabilities()).put("a", 0.0);
        first.getUsage().put("input_tokens", 999);

        JevResponse second = client.evaluate("track", choiceQuestions());

        assertTrue(second.isFromCache());
        assertEquals("a", second.getChoice("q"));
        assertEquals(0.9, ((Map<String, Number>) second.getAnswer("q").getProbabilities()).get("a").doubleValue());
        assertEquals(10, second.getUsage().get("input_tokens"));
        assertNotSame(first.getAnswers(), second.getAnswers());
        second.getAnswers().clear();
        JevResponse third = client.evaluate("track", choiceQuestions());
        assertEquals("a", third.getChoice("q"));
        assertEquals(1, calls.get());
    }

    @Test
    void concurrentIdenticalRequestsUseOneCallAndReturnIndependentResponses() throws Exception {
        JevClient client = newClient(0, 2000);
        delayMs.set(150);
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JevResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return client.evaluate("track", choiceQuestions());
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<JevResponse> results = new ArrayList<>();
            for (Future<JevResponse> future : futures) {
                JevResponse result = future.get(5, TimeUnit.SECONDS);
                assertTrue(result.isSuccess(), result.getErrorMessage());
                results.add(result);
            }
            assertEquals(1, calls.get());
            results.get(0).getAnswer("q").setChoice("b");
            for (int i = 1; i < results.size(); i++) {
                assertEquals("a", results.get(i).getChoice("q"));
                assertNotSame(results.get(0), results.get(i));
                assertNotSame(results.get(0).getAnswer("q"), results.get(i).getAnswer("q"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void canonicalCacheKeyIgnoresMapOrderAndPreservesArrayOrder() {
        JevClient client = newClient(0, 1000);
        Map<String, Object> firstState = new LinkedHashMap<>();
        firstState.put("title", "Track");
        firstState.put("artists", Arrays.asList("Singer A", "Singer B"));
        Map<String, Object> reversedMap = new LinkedHashMap<>();
        reversedMap.put("artists", Arrays.asList("Singer A", "Singer B"));
        reversedMap.put("title", "Track");
        Map<String, String> reversedCriteria = new LinkedHashMap<>();
        reversedCriteria.put("b", "Second track");
        reversedCriteria.put("a", "First track");

        assertTrue(client.evaluate(firstState, choiceQuestions()).isSuccess());
        JevResponse equivalent = client.evaluate(reversedMap, Collections.singletonMap("q",
                JevQuestion.choice("Choose the song", reversedCriteria)));
        assertTrue(equivalent.isFromCache());
        assertEquals(1, calls.get());

        reversedMap.put("artists", Arrays.asList("Singer B", "Singer A"));
        assertFalse(client.evaluate(reversedMap, choiceQuestions()).isFromCache());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void transientHttpFailuresEnterCooldownWhileCachedResultsRemainAvailable(int httpStatus) throws Exception {
        JevClient client = newClient(1000, 1000);
        assertTrue(client.evaluate("cached-track", choiceQuestions()).isSuccess());
        status.set(httpStatus);

        assertFalse(client.evaluate("new-track", choiceQuestions()).isSuccess());
        assertFalse(client.evaluate("new-track", choiceQuestions()).isSuccess());
        JevResponse cached = client.evaluate("cached-track", choiceQuestions());

        assertTrue(cached.isSuccess());
        assertTrue(cached.isFromCache());
        assertEquals(2, calls.get());
        status.set(200);
        Thread.sleep(1100);
        assertTrue(client.evaluate("new-track", choiceQuestions()).isSuccess());
        assertEquals(3, calls.get());
    }

    @Test
    void timeoutEntersCooldownAndAvoidsAnotherHttpCall() {
        JevClient client = newClient(1000, 200);
        delayMs.set(1500);

        JevResponse timeout = client.evaluate("track", choiceQuestions());
        JevResponse cooldown = client.evaluate("track", choiceQuestions());

        assertFalse(timeout.isSuccess());
        assertFalse(cooldown.isSuccess());
        assertEquals(1, calls.get());
        assertEquals(0, client.getCacheSize());
    }

    @Test
    void serializationFailureReturnsFailureWithoutCallingEndpoint() {
        JevClient client = newClient(0, 1000);

        JevResponse response = assertDoesNotThrow(() -> client.evaluate(new BrokenPayload(), choiceQuestions()));

        assertFalse(response.isSuccess());
        assertEquals(0, calls.get());
    }

    @Test
    void fallbackSupplierExceptionIsPropagatedWithoutCallingItTwice() {
        JevClient client = newClient(0, 1000);
        status.set(503);
        AtomicInteger fallbackCalls = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("fallback failed");

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> client.evaluateWithFallback("track", choiceQuestions(), () -> {
                    fallbackCalls.incrementAndGet();
                    throw expected;
                }, response -> "unused"));

        assertSame(expected, actual);
        assertEquals(1, fallbackCalls.get());
    }

    @Test
    void requestWithoutModelUsesConfiguredModel() {
        JevProperties properties = properties(0, 1000);
        properties.setModel("configured-model");
        JevClient client = new JevClient(properties);
        JevRequest request = JevRequest.builder().model(null).state("track").questions(choiceQuestions()).build();

        assertTrue(client.evaluate(request).isSuccess());

        assertEquals("configured-model", JSON.parseObject(receivedBody.get()).getString("model"));
    }

    private JevClient newClient(long cooldownMs, int timeoutMs) {
        return new JevClient(properties(cooldownMs, timeoutMs));
    }

    private JevProperties properties(long cooldownMs, int timeoutMs) {
        JevProperties properties = new JevProperties();
        properties.setApiKey("local-test-key");
        properties.setEnabled(true);
        properties.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/evaluate");
        properties.setTimeoutMs(timeoutMs);
        properties.setFailureCooldownMs((int) cooldownMs);
        properties.setCacheEnabled(true);
        return properties;
    }

    private void respond(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            Thread.sleep(delayMs.get());
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private static Map<String, JevQuestion> choiceQuestions() {
        return Collections.singletonMap("q", choiceQuestion());
    }

    private static JevQuestion choiceQuestion() {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("a", "First track");
        criteria.put("b", "Second track");
        return JevQuestion.choice("Choose the song", criteria);
    }

    private static String answer(String value) {
        return "{\"answers\":{\"q\":" + value + "}}";
    }

    private static String choiceAnswer(String choice, String confidence, String probabilities) {
        return answer("{\"type\":\"choice\",\"choice\":\"" + choice + "\",\"confidence\":"
                + confidence + ",\"probabilities\":" + probabilities + "}");
    }

    private static String scoreAnswer(String score, String probabilities) {
        return answer("{\"type\":\"score\",\"score\":" + score + ",\"confidence\":0.9,"
                + "\"probabilities\":" + probabilities + ",\"legend\":{\"0\":\"Low\",\"1\":\"Medium\",\"2\":\"High\"}}");
    }

    public static class BrokenPayload {
        public String getValue() {
            throw new IllegalStateException("deliberate serialization failure");
        }
    }
}

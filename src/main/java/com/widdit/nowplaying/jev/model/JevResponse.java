package com.widdit.nowplaying.jev.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * Phản hồi hoàn chỉnh từ Jev System One hoặc từ Fallback/Cache.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JevResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Trạng thái thành công hay thất bại.
     */
    @Builder.Default
    private boolean success = true;

    /**
     * Tên model đã thực thi.
     */
    private String model;

    /**
     * Bản đồ câu trả lời theo từng questionId.
     */
    @Builder.Default
    private Map<String, JevAnswer> answers = Collections.emptyMap();

    /**
     * Thống kê token đã dùng (input_tokens, output_tokens).
     */
    private Map<String, Integer> usage;

    /**
     * Thông điệp lỗi nếu thất bại (timeout, lỗi mạng, HTTP 4xx/5xx, v.v.).
     */
    private String errorMessage;

    /**
     * Thời gian thực thi (mili-giây).
     */
    private long latencyMs;

    /**
     * Kết quả có phải được lấy từ in-memory cache hay không.
     */
    @Builder.Default
    private boolean fromCache = false;

    /**
     * Lấy JevAnswer theo questionId.
     */
    public JevAnswer getAnswer(String questionId) {
        if (answers == null) return null;
        return answers.get(questionId);
    }

    /**
     * Tiện ích lấy lựa chọn Choice nhanh.
     */
    public String getChoice(String questionId) {
        JevAnswer answer = getAnswer(questionId);
        return answer != null ? answer.getChoice() : null;
    }

    /**
     * Tiện ích lấy giá trị Noul nhanh.
     */
    public Double getNoul(String questionId) {
        JevAnswer answer = getAnswer(questionId);
        return answer != null ? answer.getNoul() : null;
    }

    /**
     * Tiện ích lấy giá trị Score nhanh.
     */
    public Double getScore(String questionId) {
        JevAnswer answer = getAnswer(questionId);
        return answer != null ? answer.getScore() : null;
    }

    /**
     * Tiện ích lấy độ tin cậy nhanh.
     */
    public Double getConfidence(String questionId) {
        JevAnswer answer = getAnswer(questionId);
        return answer != null ? answer.getConfidence() : null;
    }

    /**
     * Kiểm tra nhanh độ tin cậy của một câu hỏi có đạt chuẩn không.
     */
    public boolean isConfident(String questionId, double minConfidence) {
        JevAnswer answer = getAnswer(questionId);
        return answer != null && answer.isConfident(minConfidence);
    }

    /**
     * Tạo một đối tượng JevResponse đại diện cho trường hợp thất bại.
     */
    public static JevResponse failure(String errorMessage, long latencyMs) {
        return JevResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .latencyMs(latencyMs)
                .answers(Collections.emptyMap())
                .fromCache(false)
                .build();
    }
}

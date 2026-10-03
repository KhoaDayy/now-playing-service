package com.widdit.nowplaying.jev.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Request payload gửi tới endpoint TypeSafe Jev System One evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JevRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Dữ liệu bối cảnh cần đánh giá (chuỗi văn bản hoặc JSON object).
     */
    private Object state;

    /**
     * Tên model: khi bỏ trống, client sử dụng model đã cấu hình.
     */
    private String model;

    /**
     * Danh sách các câu hỏi định kiểu (questionId -> JevQuestion).
     */
    @Builder.Default
    private Map<String, JevQuestion> questions = new HashMap<>();

    /**
     * Thêm một câu hỏi vào request.
     */
    public JevRequest addQuestion(String questionId, JevQuestion question) {
        if (this.questions == null) {
            this.questions = new HashMap<>();
        }
        this.questions.put(questionId, question);
        return this;
    }
}

package com.widdit.nowplaying.jev.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Đại diện cho một câu hỏi định kiểu (typed question) gửi tới TypeSafe Jev.
 * Hỗ trợ 3 kiểu nguyên thủy của Jev:
 * - "choice": Chọn một lựa chọn từ danh mục kèm xác suất và độ tin cậy.
 * - "noul": Đánh giá xác thực Có/Không (0.0 đến 1.0).
 * - "score": Đánh giá theo dải tiêu chí định sẵn (rubric levels).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JevQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Kiểu câu hỏi: "noul", "choice", hoặc "score".
     */
    private String type;

    /**
     * Chỉ dẫn đánh giá: Chuỗi văn bản hoặc Object JSON có cấu trúc.
     */
    private Object instructions;

    /**
     * Tiêu chí đánh giá:
     * - Choice: Map<String, Object> mô tả các lựa chọn.
     * - Score: List<String> danh sách các mức độ.
     * - Noul: Map với "true" / "false" (tùy chọn).
     */
    private Object criteria;

    /**
     * Tạo câu hỏi Noul (Yes/No).
     */
    public static JevQuestion noul(Object instructions) {
        return JevQuestion.builder()
                .type("noul")
                .instructions(instructions)
                .build();
    }

    /**
     * Tạo câu hỏi Noul (Yes/No) kèm tiêu chí mô tả hai vế true/false.
     */
    public static JevQuestion noul(Object instructions, Object criteria) {
        return JevQuestion.builder()
                .type("noul")
                .instructions(instructions)
                .criteria(criteria)
                .build();
    }

    /**
     * Tạo câu hỏi Choice để chọn 1 trong các phương án.
     */
    public static JevQuestion choice(Object instructions, Map<String, ?> criteria) {
        return JevQuestion.builder()
                .type("choice")
                .instructions(instructions)
                .criteria(criteria)
                .build();
    }

    /**
     * Tạo câu hỏi Score để chấm điểm theo các mức tiêu chuẩn.
     */
    public static JevQuestion score(Object instructions, List<?> criteria) {
        return JevQuestion.builder()
                .type("score")
                .instructions(instructions)
                .criteria(criteria)
                .build();
    }
}

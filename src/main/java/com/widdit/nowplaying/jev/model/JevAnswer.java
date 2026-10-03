package com.widdit.nowplaying.jev.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Kết quả trả về cho từng câu hỏi từ Jev.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JevAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Kiểu câu trả lời: "choice", "noul", hoặc "score".
     */
    private String type;

    /**
     * Lựa chọn tốt nhất (dành cho Choice).
     */
    private String choice;

    /**
     * Xác suất câu trả lời là YES (dành cho Noul, 0.0 đến 1.0).
     */
    private Double noul;

    /**
     * Điểm số theo thang đánh giá (dành cho Score).
     */
    private Double score;

    /**
     * Độ tự tin của mô hình (0.0 đến 1.0, có trên Choice và Score).
     */
    private Double confidence;

    /**
     * Phân phối xác suất (Map<String, Double> cho Choice và Score).
     */
    private Object probabilities;

    /**
     * Mô tả các mức điểm (dành cho Score).
     */
    private Map<String, String> legend;

    /**
     * Kiểm tra độ tự tin có vượt ngưỡng không.
     */
    public boolean isConfident(double minConfidence) {
        return isProbability(this.confidence) && isProbability(minConfidence)
                && this.confidence >= minConfidence;
    }

    /**
     * Đối với Noul: kiểm tra xem câu trả lời có đạt ngưỡng YES hay không.
     */
    public boolean isYes(double threshold) {
        return isProbability(this.noul) && isProbability(threshold) && this.noul >= threshold;
    }

    /**
     * Đối với Noul: kiểm tra xem câu trả lời có đạt ngưỡng NO hay không.
     */
    public boolean isNo(double threshold) {
        return isProbability(this.noul) && isProbability(threshold) && this.noul <= (1.0 - threshold);
    }

    private static boolean isProbability(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 1;
    }

    public double getConfidenceOrDefault(double defaultValue) {
        return this.confidence != null ? this.confidence : defaultValue;
    }

    public double getNoulOrDefault(double defaultValue) {
        return this.noul != null ? this.noul : defaultValue;
    }

    public double getScoreOrDefault(double defaultValue) {
        return this.score != null ? this.score : defaultValue;
    }

    public String getChoiceOrDefault(String defaultValue) {
        return this.choice != null && !this.choice.isBlank() ? this.choice : defaultValue;
    }
}

package com.widdit.nowplaying.jev.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Đại diện cho một bài hát ứng viên (Candidate) thu được từ kết quả tìm kiếm của các nền tảng âm nhạc.
 *
 * @param <T> Kiểu đối tượng gốc (JSONObject, Lyric, Track, v.v.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SongCandidate<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID định danh ứng viên trong danh sách đánh giá (ví dụ: "cand_0", "cand_1").
     */
    private String id;

    /**
     * Tiêu đề bài hát.
     */
    private String title;

    /**
     * Tên ca sĩ / nghệ sĩ.
     */
    private String artist;

    /**
     * Tên Album (nếu có).
     */
    private String album;

    /**
     * Thời lượng bài hát (giây).
     */
    private Integer durationSeconds;

    /**
     * Điểm tương đồng truyền thống tính từ SongMatchingUtil (0 - 100).
     */
    private int traditionalScore;

    /** True only when a confident Jev answer selected this candidate in the current evaluation. */
    private boolean semanticMatchConfirmed;

    /**
     * Đối tượng dữ liệu gốc từ nền tảng.
     */
    private T rawObject;
}

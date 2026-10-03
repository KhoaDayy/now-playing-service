package com.widdit.nowplaying.jev.rerank;

import com.widdit.nowplaying.jev.client.JevClient;
import com.widdit.nowplaying.jev.model.JevAnswer;
import com.widdit.nowplaying.jev.model.JevQuestion;
import com.widdit.nowplaying.jev.model.JevResponse;
import com.widdit.nowplaying.util.SongMatchingUtil;
import com.widdit.nowplaying.util.SongUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service Reranker sử dụng TypeSafe Jev System One để lựa chọn bài hát tối ưu nhất
 * từ danh sách kết quả tìm kiếm (NetEase, QQ Music, LRCLIB, Kugou).
 *
 * Jev đóng vai trò là Semantic Reranker:
 * 1. Phân biệt chính xác giữa bản Gốc (Original), Remix, Live biểu diễn, Acoustic, Cover, Speed-up.
 * 2. Xử lý tên ca sĩ phụ (feat, ft, ft.), nghệ danh đa ngôn ngữ, dịch tên tiếng Trung/Việt/Anh/Nhật.
 * 3. Nếu Jev chưa cấu hình, mất mạng, timeout hoặc độ tự tin < 60%, tự động fallback về SongMatchingUtil.
 */
@Service
@Slf4j
public class JevSongReranker {

    private final JevClient jevClient;

    /**
     * Ngưỡng độ tự tin tối thiểu để chấp nhận phán đoán của Jev.
     */
    private static final double CONFIDENCE_THRESHOLD = 0.60;

    @Autowired
    public JevSongReranker(JevClient jevClient) {
        this.jevClient = jevClient;
    }

    /**
     * Chọn candidate tốt nhất từ danh sách ứng viên.
     *
     * @param localTitle Tiêu đề bài hát đang phát
     * @param localArtist Tên ca sĩ đang phát
     * @param candidates Danh sách các candidate đã được chấm điểm truyền thống
     * @param <T> Kiểu đối tượng gốc của candidate
     * @return Candidate tối ưu nhất hoặc null nếu danh sách rỗng
     */
    public <T> SongCandidate<T> selectBestCandidate(
            String localTitle,
            String localArtist,
            List<SongCandidate<T>> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // Sắp xếp bản sao để giữ thứ tự và ID của dữ liệu thuộc bên gọi.
        // List.sort giữ nguyên thứ tự đầu vào khi hai ứng viên có cùng điểm.
        List<SongCandidate<T>> rankedCandidates = new ArrayList<>();
        for (SongCandidate<T> candidate : candidates) {
            if (candidate != null) {
                candidate.setSemanticMatchConfirmed(false);
                rankedCandidates.add(candidate);
            }
        }
        if (rankedCandidates.isEmpty()) {
            return null;
        }
        rankedCandidates.sort(Comparator.comparingInt(
                (SongCandidate<T> candidate) -> candidate.getTraditionalScore()).reversed());
        SongCandidate<T> traditionalBest = rankedCandidates.get(0);

        // 2. Nếu Jev không khả dụng, dùng ngay fallback truyền thống
        if (!jevClient.isAvailable()) {
            return selectTraditionalFallback(traditionalBest);
        }

        // Nếu chỉ có 1 ứng viên duy nhất và điểm số truyền thống đã rất cao (>= 90), không cần gọi Jev
        if (rankedCandidates.size() == 1 && traditionalBest.getTraditionalScore() >= 90) {
            return traditionalBest;
        }

        // Giới hạn tối đa 8 ứng viên gửi tới Jev để tiết kiệm token và đảm bảo độ chính xác
        int countToEvaluate = Math.min(rankedCandidates.size(), 8);
        List<SongCandidate<T>> evalList = rankedCandidates.subList(0, countToEvaluate);

        // 3. Chuẩn bị State và Criteria cho Jev
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, String> nowPlayingMap = new LinkedHashMap<>();
        String effectiveArtist = localArtist != null ? localArtist.trim() : "";
        if (!effectiveArtist.isBlank() && SongUtil.isChannelOrNoise(effectiveArtist)) {
            effectiveArtist = "";
        }
        nowPlayingMap.put("title", localTitle != null ? localTitle : "");
        nowPlayingMap.put("artist", effectiveArtist);
        state.put("now_playing", nowPlayingMap);

        List<Map<String, Object>> candidateListState = new ArrayList<>();
        Map<String, String> criteria = new LinkedHashMap<>();
        Map<String, SongCandidate<T>> candidateLookup = new HashMap<>();

        for (int i = 0; i < evalList.size(); i++) {
            SongCandidate<T> c = evalList.get(i);
            String cid = "cand_" + i;
            candidateLookup.put(cid, c);

            Map<String, Object> cState = new LinkedHashMap<>();
            cState.put("id", cid);
            cState.put("title", c.getTitle() != null ? c.getTitle() : "");
            cState.put("artist", c.getArtist() != null ? c.getArtist() : "");
            if (c.getAlbum() != null && !c.getAlbum().isBlank()) {
                cState.put("album", c.getAlbum());
            }
            if (c.getDurationSeconds() != null && c.getDurationSeconds() > 0) {
                cState.put("duration_sec", c.getDurationSeconds());
            }
            candidateListState.add(cState);

            StringBuilder rubric = new StringBuilder();
            rubric.append("Title: '").append(c.getTitle()).append("'");
            if (c.getArtist() != null && !c.getArtist().isBlank()) {
                rubric.append(", Artist: '").append(c.getArtist()).append("'");
            }
            if (c.getAlbum() != null && !c.getAlbum().isBlank()) {
                rubric.append(", Album: '").append(c.getAlbum()).append("'");
            }
            criteria.put(cid, rubric.toString());
        }

        // Thêm tùy chọn "none_of_above"
        criteria.put("none_of_above", "None of the candidates match the song's composition or provide valid lyrics for `now_playing`");
        state.put("candidates", candidateListState);

        String instructions = "Which candidate in `candidates` provides the correct lyric source for the song in `now_playing`?\n" +
                "- Goal: Find the correct composition and lyric source. Primary & featured artists, aliases, and translations (Vietnamese/Chinese/English/Japanese/Korean) should be matched.\n" +
                "- Missing / Channel Artist: Karaoke, instrumental, or YouTube tracks often omit the artist completely or only list a karaoke channel/beat maker. When `now_playing` artist is empty (or a channel/beat maker), choose the candidate by the original artist or primary composer/singer who popularized the song composition (e.g. for title='Nàng Thơ', pick 'Hoàng Dũng').\n" +
                "- Swapped Title/Artist: Video titles often have title and artist swapped (e.g. now_playing title='Justa Tee' and artist='2AM', but the real song is '2AM' by 'Justa Tee'). If a candidate matches the song composition with title and artist swapped, that IS the correct lyric source.\n" +
                "- Shared Lyrics: Studio original, Live performances, Acoustic, Remixes, Radio/Album edits, and Instrumental/Beat tracks generally share the same lyrics as the original song composition. Any candidate sharing the composition and lyrics is a valid lyric source.\n" +
                "- Different Lyrics: You MUST distinguish versions with substantially different lyrics (e.g. 10 Minute Version vs standard version, parodies, or different songs).\n" +
                "- Artist Collision: Do NOT match completely different songs that only share the title by different artists (e.g. Adele - Hello vs Lionel Richie - Hello), UNLESS now_playing artist is empty/missing.\n" +
                "- If no candidate matches the song's composition or provides valid lyrics, select 'none_of_above'.";

        try {
            JevResponse response = jevClient.evaluate(
                    state,
                    Collections.singletonMap("best_match", JevQuestion.choice(instructions, criteria))
            );

            if (response.isSuccess()) {
                JevAnswer answer = response.getAnswer("best_match");
                if (answer != null && "choice".equals(answer.getType())
                        && answer.isConfident(CONFIDENCE_THRESHOLD)) {
                    String choice = answer.getChoice();

                    if ("none_of_above".equals(choice)) {
                        log.info("Jev Reranker phán đoán KHÔNG có ứng viên nào khớp lyric với '{} - {}' (Độ tự tin: {}%)",
                                localTitle, localArtist, (int) (answer.getConfidence() * 100));
                        return null;
                    }

                    SongCandidate<T> chosen = candidateLookup.get(choice);
                    if (chosen != null) {
                        chosen.setSemanticMatchConfirmed(true);
                        log.info("Jev Reranker đã chọn ứng viên '{}' ({}) với độ tự tin {}% trong {}ms [Score cũ: {}]",
                                chosen.getTitle(), chosen.getArtist(), (int) (answer.getConfidence() * 100),
                                response.getLatencyMs(), chosen.getTraditionalScore());
                        return chosen;
                    }
                } else {
                    log.debug("Jev Reranker: Độ tự tin chưa đạt ngưỡng ({}), kích hoạt Fallback",
                            answer != null ? answer.getConfidence() : "null");
                }
            } else {
                log.debug("Jev Reranker gọi thất bại ({}), chuyển sang Fallback", response.getErrorMessage());
            }

        } catch (Exception e) {
            log.warn("Jev Reranker gặp ngoại lệ, kích hoạt Fallback an toàn: {}", e.getMessage());
        }

        return selectTraditionalFallback(traditionalBest);
    }

    // Cùng một ngưỡng cho Jev bị tắt, lỗi mạng, câu trả lời sai kiểu và độ tin cậy thấp.
    private <T> SongCandidate<T> selectTraditionalFallback(SongCandidate<T> traditionalBest) {
        if (traditionalBest.getTraditionalScore() >= SongMatchingUtil.ALTERNATE_VERSION_THRESHOLD) {
            log.debug("Jev Reranker sử dụng Fallback SongMatchingUtil: '{}' - '{}' (Điểm: {})",
                    traditionalBest.getTitle(), traditionalBest.getArtist(), traditionalBest.getTraditionalScore());
            return traditionalBest;
        }
        log.debug("Jev Reranker: Fallback truyền thống có điểm số quá thấp ({}), loại bỏ",
                traditionalBest.getTraditionalScore());
        return null;
    }
}

package com.widdit.nowplaying.jev.benchmark;

import com.widdit.nowplaying.jev.client.JevClient;
import com.widdit.nowplaying.jev.config.JevProperties;
import com.widdit.nowplaying.jev.model.JevAnswer;
import com.widdit.nowplaying.jev.model.JevQuestion;
import com.widdit.nowplaying.jev.model.JevResponse;
import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import com.widdit.nowplaying.util.SongMatchingUtil;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * Adversarial Benchmark: Lyrics Matching & Lyric Source Identification
 * Đo lường định lượng trên 35 ca thử nghiệm thuộc 11 nhóm (Group A -> Group K)
 * Đánh giá trên tiêu chí Lyric Identity (khả năng tìm đúng nguồn lyrics của composition)
 */
public class LyricIdentityBenchmark {

    public static class TestCase {
        public int id;
        public String group;
        public String playingTitle;
        public String playingArtist;
        public List<SongCandidate<String>> candidates;
        public Set<String> expectedLyricSources; // Chứa các candidate ID hợp lệ hoặc "none_of_above"
        public String expectedDescription;
        public String note;

        public TestCase(int id, String group, String playingTitle, String playingArtist,
                        List<SongCandidate<String>> candidates, Set<String> expectedLyricSources,
                        String expectedDescription, String note) {
            this.id = id;
            this.group = group;
            this.playingTitle = playingTitle;
            this.playingArtist = playingArtist;
            this.candidates = candidates;
            this.expectedLyricSources = expectedLyricSources;
            this.expectedDescription = expectedDescription;
            this.note = note;
        }
    }

    public static class CaseResult {
        public int id;
        public String group;
        public String playingTrack;
        public String expectedDescription;

        public String traditionalChoice;
        public int traditionalScore;
        public boolean traditionalCorrect;

        public String jevChoice;
        public double jevConfidence;
        public long jevLatency;
        public boolean jevCorrect;

        public String hybridChoice;
        public boolean hybridCorrect;
        public boolean fallbackUsed;

        public String classification; // "Both Correct", "Jev Correct / Trad Wrong", "Trad Correct / Jev Wrong", "Both Wrong"
    }

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("  STARTING ADVERSARIAL BENCHMARK: LYRICS MATCHING & LYRIC SOURCE IDENTIFICATION");
        System.out.println("===============================================================================");

        JevProperties props = new JevProperties();
        props.init();
        String apiKey = System.getenv("TYPESAFE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("JEV_API_KEY");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            props.setApiKey(apiKey);
            props.setEnabled(true);
        }
        props.setTimeoutMs(5000);

        JevClient client = new JevClient(props);
        client.init();
        JevSongReranker reranker = new JevSongReranker(client);

        List<TestCase> testCases = createTestCases();
        List<CaseResult> results = new ArrayList<>();

        int tradCorrectCount = 0;
        int jevCorrectCount = 0;
        int hybridCorrectCount = 0;

        int tradWrongSelections = 0;
        int jevWrongSelections = 0;
        int hybridWrongSelections = 0;

        int tradFalseAcceptance = 0;
        int jevFalseAcceptance = 0;
        int hybridFalseAcceptance = 0;

        int tradCorrectNone = 0;
        int jevCorrectNone = 0;
        int hybridCorrectNone = 0;

        int tradFalseNone = 0;
        int jevFalseNone = 0;
        int hybridFalseNone = 0;

        long totalJevLatency = 0;
        double totalJevConf = 0;
        int fallbackCount = 0;

        for (TestCase tc : testCases) {
            CaseResult cr = new CaseResult();
            cr.id = tc.id;
            cr.group = tc.group;
            cr.playingTrack = tc.playingTitle + " - " + tc.playingArtist;
            cr.expectedDescription = tc.expectedDescription;

            // 1. Traditional-only Strategy
            // Tính điểm cho từng candidate
            SongCandidate<String> tradBest = null;
            int maxScore = -1;
            for (SongCandidate<String> c : tc.candidates) {
                int score = SongMatchingUtil.calculateSimilarity(tc.playingTitle, tc.playingArtist, c.getTitle(), c.getArtist());
                c.setTraditionalScore(score);
                if (score > maxScore) {
                    maxScore = score;
                    tradBest = c;
                }
            }
            cr.traditionalScore = maxScore;
            // Áp dụng ngưỡng ALTERNATE_VERSION_THRESHOLD = 60
            if (maxScore >= SongMatchingUtil.ALTERNATE_VERSION_THRESHOLD && tradBest != null) {
                cr.traditionalChoice = tradBest.getId();
            } else {
                cr.traditionalChoice = "none_of_above";
            }
            cr.traditionalCorrect = tc.expectedLyricSources.contains(cr.traditionalChoice);
            if (cr.traditionalCorrect) {
                tradCorrectCount++;
                if (tc.expectedLyricSources.contains("none_of_above")) {
                    tradCorrectNone++;
                }
            } else {
                if (tc.expectedLyricSources.contains("none_of_above")) {
                    tradFalseAcceptance++;
                } else if ("none_of_above".equals(cr.traditionalChoice)) {
                    tradFalseNone++;
                } else {
                    tradWrongSelections++;
                }
            }

            // 2. Jev-only Strategy
            Map<String, Object> state = new LinkedHashMap<>();
            Map<String, String> nowPlayingMap = new LinkedHashMap<>();
            nowPlayingMap.put("title", tc.playingTitle);
            nowPlayingMap.put("artist", tc.playingArtist);
            state.put("now_playing", nowPlayingMap);

            List<Map<String, Object>> candidateListState = new ArrayList<>();
            Map<String, String> criteria = new LinkedHashMap<>();
            for (SongCandidate<String> c : tc.candidates) {
                Map<String, Object> cMap = new LinkedHashMap<>();
                cMap.put("id", c.getId());
                cMap.put("title", c.getTitle());
                cMap.put("artist", c.getArtist());
                if (c.getAlbum() != null && !c.getAlbum().isBlank()) {
                    cMap.put("album", c.getAlbum());
                }
                candidateListState.add(cMap);

                StringBuilder rubric = new StringBuilder();
                rubric.append("Title: '").append(c.getTitle()).append("'");
                if (c.getArtist() != null && !c.getArtist().isBlank()) {
                    rubric.append(", Artist: '").append(c.getArtist()).append("'");
                }
                criteria.put(c.getId(), rubric.toString());
            }
            criteria.put("none_of_above", "None of the candidates match the song's composition or provide valid lyrics for `now_playing`");
            state.put("candidates", candidateListState);

            String instructions = "Which candidate in `candidates` provides the correct lyric source for the song in `now_playing`?\n" +
                    "- Goal: Find the correct composition and lyric source. Primary & featured artists, aliases, and translations (Vietnamese/Chinese/English/Japanese/Korean) should be matched.\n" +
                    "- Shared Lyrics: Studio original, Live performances, Acoustic, Remixes, Radio/Album edits, and Instrumental/Beat tracks generally share the same lyrics as the original song composition. Any candidate sharing the composition and lyrics is a valid lyric source.\n" +
                    "- Different Lyrics: You MUST distinguish versions with substantially different lyrics (e.g. 10 Minute Version vs standard version, parodies, or different songs).\n" +
                    "- Artist Collision: Do NOT match completely different songs that only share the title by different artists (e.g. Adele - Hello vs Lionel Richie - Hello).\n" +
                    "- If no candidate matches the song's composition or provides valid lyrics, select 'none_of_above'.";

            JevResponse resp = client.evaluate(state, Collections.singletonMap("best_match", JevQuestion.choice(instructions, criteria)));
            if (resp.isSuccess() && resp.getAnswer("best_match") != null) {
                JevAnswer ans = resp.getAnswer("best_match");
                cr.jevChoice = ans.getChoice();
                cr.jevConfidence = ans.getConfidence();
                cr.jevLatency = resp.getLatencyMs();
            } else {
                cr.jevChoice = "none_of_above";
                cr.jevConfidence = 0.0;
                cr.jevLatency = resp.getLatencyMs();
            }
            totalJevLatency += cr.jevLatency;
            totalJevConf += cr.jevConfidence;

            cr.jevCorrect = tc.expectedLyricSources.contains(cr.jevChoice);
            if (cr.jevCorrect) {
                jevCorrectCount++;
                if (tc.expectedLyricSources.contains("none_of_above")) {
                    jevCorrectNone++;
                }
            } else {
                if (tc.expectedLyricSources.contains("none_of_above")) {
                    jevFalseAcceptance++;
                } else if ("none_of_above".equals(cr.jevChoice)) {
                    jevFalseNone++;
                } else {
                    jevWrongSelections++;
                }
            }

            // 3. Hybrid Strategy (JevSongReranker)
            SongCandidate<String> hybridChosen = reranker.selectBestCandidate(tc.playingTitle, tc.playingArtist, tc.candidates);
            if (hybridChosen == null) {
                cr.hybridChoice = "none_of_above";
            } else {
                cr.hybridChoice = hybridChosen.getId();
            }

            // Fallback checking
            if (resp.isSuccess() && resp.getAnswer("best_match") != null && resp.getAnswer("best_match").isConfident(0.60)) {
                cr.fallbackUsed = false;
            } else {
                cr.fallbackUsed = true;
                fallbackCount++;
            }

            cr.hybridCorrect = tc.expectedLyricSources.contains(cr.hybridChoice);
            if (cr.hybridCorrect) {
                hybridCorrectCount++;
                if (tc.expectedLyricSources.contains("none_of_above")) {
                    hybridCorrectNone++;
                }
            } else {
                if (tc.expectedLyricSources.contains("none_of_above")) {
                    hybridFalseAcceptance++;
                } else if ("none_of_above".equals(cr.hybridChoice)) {
                    hybridFalseNone++;
                } else {
                    hybridWrongSelections++;
                }
            }

            // Classification
            if (cr.traditionalCorrect && cr.jevCorrect) {
                cr.classification = "Both Correct";
            } else if (!cr.traditionalCorrect && cr.jevCorrect) {
                cr.classification = "Jev Correct / Trad Wrong";
            } else if (cr.traditionalCorrect && !cr.jevCorrect) {
                cr.classification = "Trad Correct / Jev Wrong";
            } else {
                cr.classification = "Both Wrong";
            }

            results.add(cr);
            System.out.printf("[%02d] %-8s | %-35s | Trad: %-12s (%3d, %s) | Jev: %-12s (%.2f, %4dms, %s) | Hybrid: %-12s (%s) | %s%n",
                    cr.id, cr.group,
                    (cr.playingTrack.length() > 35 ? cr.playingTrack.substring(0, 32) + "..." : cr.playingTrack),
                    cr.traditionalChoice, cr.traditionalScore, (cr.traditionalCorrect ? "PASS" : "FAIL"),
                    cr.jevChoice, cr.jevConfidence, cr.jevLatency, (cr.jevCorrect ? "PASS" : "FAIL"),
                    cr.hybridChoice, (cr.hybridCorrect ? "PASS" : "FAIL"),
                    cr.classification);
        }

        // Output summary calculations
        int total = testCases.size();
        System.out.println("\n===============================================================================");
        System.out.println("  SUMMARY RESULTS");
        System.out.println("===============================================================================");
        System.out.printf("Total Cases: %d%n", total);
        System.out.printf("Traditional Accuracy : %d/%d (%.1f%%)%n", tradCorrectCount, total, (tradCorrectCount * 100.0 / total));
        System.out.printf("Jev Accuracy         : %d/%d (%.1f%%)%n", jevCorrectCount, total, (jevCorrectCount * 100.0 / total));
        System.out.printf("Hybrid Accuracy      : %d/%d (%.1f%%)%n", hybridCorrectCount, total, (hybridCorrectCount * 100.0 / total));
        System.out.printf("Average Latency      : %.1f ms%n", (totalJevLatency * 1.0 / total));
        System.out.printf("Average Confidence   : %.2f%n", (totalJevConf / total));
        System.out.printf("Fallback Used        : %d/%d (%.1f%%)%n", fallbackCount, total, (fallbackCount * 100.0 / total));

        // In chi tiết phân nhóm
        long jevWinCount = results.stream().filter(r -> "Jev Correct / Trad Wrong".equals(r.classification)).count();
        long tradWinCount = results.stream().filter(r -> "Trad Correct / Jev Wrong".equals(r.classification)).count();
        long bothWrongCount = results.stream().filter(r -> "Both Wrong".equals(r.classification)).count();
        long bothCorrectCount = results.stream().filter(r -> "Both Correct".equals(r.classification)).count();

        System.out.printf("Both Correct                 : %d%n", bothCorrectCount);
        System.out.printf("Jev Correct / Trad Wrong     : %d%n", jevWinCount);
        System.out.printf("Trad Correct / Jev Wrong     : %d%n", tradWinCount);
        System.out.printf("Both Wrong                   : %d%n", bothWrongCount);

        // Xuất file JSON kết quả chi tiết
        try (PrintWriter writer = new PrintWriter(new FileWriter("target/benchmark_lyric_identity_results.tsv"))) {
            writer.println("id\tgroup\tplaying_track\texpected\ttrad_choice\ttrad_score\ttrad_correct\tjev_choice\tjev_conf\tjev_latency\tjev_correct\thybrid_choice\thybrid_correct\tfallback\tclassification");
            for (CaseResult r : results) {
                writer.printf("%d\t%s\t%s\t%s\t%s\t%d\t%s\t%s\t%.2f\t%d\t%s\t%s\t%s\t%s\t%s%n",
                        r.id, r.group, r.playingTrack, r.expectedDescription,
                        r.traditionalChoice, r.traditionalScore, r.traditionalCorrect,
                        r.jevChoice, r.jevConfidence, r.jevLatency, r.jevCorrect,
                        r.hybridChoice, r.hybridCorrect, r.fallbackUsed, r.classification);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<TestCase> createTestCases() {
        List<TestCase> list = new ArrayList<>();

        // Group A - Basic exact matching (3 cases)
        list.add(new TestCase(
                1, "Group A", "Shape of You", "Ed Sheeran",
                Arrays.asList(
                        candidate("cand_0", "Shape of You", "Ed Sheeran", "÷ (Divide)"),
                        candidate("cand_1", "Shape of You (Karaoke)", "Ed Sheeran", "")
                ),
                Set.of("cand_0", "cand_1"),
                "Shape of You - Ed Sheeran",
                "Exact title and artist, both provide valid lyrics."
        ));

        list.add(new TestCase(
                2, "Group A", "Can't Stop the Feeling!", "Justin Timberlake",
                Arrays.asList(
                        candidate("cand_0", "Cant Stop The Feeling", "Justin Timberlake", ""),
                        candidate("cand_1", "Can't Feel My Face", "The Weeknd", "")
                ),
                Set.of("cand_0"),
                "Can't Stop the Feeling! - Justin Timberlake",
                "Punctuation difference (apostrophe & exclamation mark)."
        ));

        list.add(new TestCase(
                3, "Group A", "STAY", "The Kid LAROI / Justin Bieber",
                Arrays.asList(
                        candidate("cand_0", "stay", "The Kid LAROI, Justin Bieber", ""),
                        candidate("cand_1", "Stay", "Rihanna", "")
                ),
                Set.of("cand_0"),
                "Stay - The Kid LAROI & Justin Bieber",
                "Case differences and multi-artist delimiter variation."
        ));

        // Group B - Live / Remix / Acoustic normalization (5 cases)
        // QUY TẮC: Không phạt Traditional vì chọn bản gốc thay vì version!
        list.add(new TestCase(
                4, "Group B", "Fix You (Live in Buenos Aires)", "Coldplay",
                Arrays.asList(
                        candidate("cand_0", "Fix You", "Coldplay", "X&Y"),
                        candidate("cand_1", "Fix You (Live in Buenos Aires)", "Coldplay", "Live in Buenos Aires"),
                        candidate("cand_2", "Yellow", "Coldplay", "Parachutes")
                ),
                Set.of("cand_0", "cand_1"),
                "Fix You - Coldplay (Studio or Live)",
                "Live performance shares the composition and lyrics with the studio original."
        ));

        list.add(new TestCase(
                5, "Group B", "Hotel California (Live on MTV 1994)", "Eagles",
                Arrays.asList(
                        candidate("cand_0", "Hotel California", "Eagles", "Hotel California"),
                        candidate("cand_1", "Desperado", "Eagles", "Desperado")
                ),
                Set.of("cand_0"),
                "Hotel California - Eagles (Original Studio)",
                "Only studio version is available; it is a 100% valid lyric source."
        ));

        list.add(new TestCase(
                6, "Group B", "Take On Me (Acoustic Version)", "a-ha",
                Arrays.asList(
                        candidate("cand_0", "Take On Me", "a-ha", "Hunting High and Low"),
                        candidate("cand_1", "The Sun Always Shines on T.V.", "a-ha", "")
                ),
                Set.of("cand_0"),
                "Take On Me - a-ha",
                "Acoustic version shares identical lyrics with original."
        ));

        list.add(new TestCase(
                7, "Group B", "Levitating (The Blessed Madonna Remix)", "Dua Lipa / Madonna",
                Arrays.asList(
                        candidate("cand_0", "Levitating", "Dua Lipa", "Future Nostalgia"),
                        candidate("cand_1", "Levitating (The Blessed Madonna Remix)", "Dua Lipa feat. Madonna", "Club Future Nostalgia"),
                        candidate("cand_2", "Physical", "Dua Lipa", "Future Nostalgia")
                ),
                Set.of("cand_0", "cand_1"),
                "Levitating - Dua Lipa",
                "Remix shares the core lyrics; both original and remix candidate pass."
        ));

        list.add(new TestCase(
                8, "Group B", "Fast Car (Acoustic Cover)", "Boyce Avenue",
                Arrays.asList(
                        candidate("cand_0", "Fast Car", "Tracy Chapman", "Tracy Chapman"),
                        candidate("cand_1", "Fast Car (Acoustic Cover)", "Boyce Avenue", "Cover Sessions"),
                        candidate("cand_2", "Baby Can I Hold You", "Tracy Chapman", "")
                ),
                Set.of("cand_0", "cand_1"),
                "Fast Car (Composition by Tracy Chapman)",
                "In lyric identity benchmark, both original composer and cover artist pass."
        ));

        // Group C - Instrumental / Karaoke / Beat (3 cases)
        // QUY TẮC: Candidate vocal/original = PASS
        list.add(new TestCase(
                9, "Group C", "Vệ Tinh (Instrumental)", "HIEUTHUHAI",
                Arrays.asList(
                        candidate("cand_0", "Vệ Tinh", "HIEUTHUHAI", ""),
                        candidate("cand_1", "Ngủ Một Mình", "HIEUTHUHAI", "")
                ),
                Set.of("cand_0"),
                "Vệ Tinh - HIEUTHUHAI (Vocal Original)",
                "Playing instrumental, retrieving vocal lyrics for display is valid."
        ));

        list.add(new TestCase(
                10, "Group C", "Nơi Này Có Anh (Beat / Karaoke)", "Sơn Tùng M-TP",
                Arrays.asList(
                        candidate("cand_0", "Nơi Này Có Anh", "Sơn Tùng M-TP", ""),
                        candidate("cand_1", "Lạc Trôi", "Sơn Tùng M-TP", "")
                ),
                Set.of("cand_0"),
                "Nơi Này Có Anh - Sơn Tùng M-TP",
                "Beat/Karaoke uses official vocal track as lyric source."
        ));

        list.add(new TestCase(
                11, "Group C", "River Flows In You (Piano Instrumental)", "Yiruma",
                Arrays.asList(
                        candidate("cand_0", "River Flows in You", "Yiruma", "First Love"),
                        candidate("cand_1", "Kiss The Rain", "Yiruma", "First Love")
                ),
                Set.of("cand_0"),
                "River Flows In You - Yiruma",
                "Instrumental tag normalized."
        ));

        // Group D - Translation / Multilingual Title (4 cases)
        list.add(new TestCase(
                12, "Group D", "Sứ Thanh Hoa", "Châu Kiệt Luân",
                Arrays.asList(
                        candidate("cand_0", "青花瓷", "周杰伦", "我很忙"),
                        candidate("cand_1", "七里香", "周杰伦", "七里香")
                ),
                Set.of("cand_0"),
                "青花瓷 - 周杰伦 (Sứ Thanh Hoa - Jay Chou)",
                "Sino-Vietnamese title 'Sứ Thanh Hoa' corresponds to Chinese '青花瓷'."
        ));

        list.add(new TestCase(
                13, "Group D", "Yoru ni Kakeru", "YOASOBI",
                Arrays.asList(
                        candidate("cand_0", "夜に駆ける", "YOASOBI", "THE BOOK"),
                        candidate("cand_1", "群青", "YOASOBI", "THE BOOK")
                ),
                Set.of("cand_0"),
                "夜に駆ける - YOASOBI",
                "Romaji title matches Japanese Kanji/Hiragana title."
        ));

        list.add(new TestCase(
                14, "Group D", "See You Again", "Wiz Khalifa / Charlie Puth",
                Arrays.asList(
                        candidate("cand_0", "再见", "张震岳", "OK"),
                        candidate("cand_1", "See You Again", "Wiz Khalifa feat. Charlie Puth", "Furious 7")
                ),
                Set.of("cand_1"),
                "See You Again - Wiz Khalifa ft. Charlie Puth",
                "False translation trap: '再见' is an unrelated Chinese song."
        ));

        list.add(new TestCase(
                15, "Group D", "Lemon", "Kenshi Yonezu",
                Arrays.asList(
                        candidate("cand_0", "Lemon", "米津玄師", "STRAY SHEEP"),
                        candidate("cand_1", "LOSER", "米津玄師", "BOOTLEG")
                ),
                Set.of("cand_0"),
                "Lemon - 米津玄師",
                "English transliteration artist 'Kenshi Yonezu' matches Kanji '米津玄師'."
        ));

        // Group E - Artist Alias / Localized Artist Name (3 cases)
        list.add(new TestCase(
                16, "Group E", "Twilight", "JJ Lin",
                Arrays.asList(
                        candidate("cand_0", "不为谁而作的歌", "林俊杰", "和自己对话"),
                        candidate("cand_1", "修炼爱情", "林俊杰", "因你而在")
                ),
                Set.of("cand_0"),
                "不为谁而作的歌 - 林俊杰 (Twilight - JJ Lin)",
                "English alias 'JJ Lin' / 'Twilight' maps to Chinese '林俊杰' / '不为谁而作的歌'."
        ));

        list.add(new TestCase(
                17, "Group E", "Em Của Ngày Hôm Qua", "M-TP",
                Arrays.asList(
                        candidate("cand_0", "Em Của Ngày Hôm Qua", "Sơn Tùng M-TP", ""),
                        candidate("cand_1", "Cơn Mưa Ngang Qua", "Sơn Tùng M-TP", "")
                ),
                Set.of("cand_0"),
                "Em Của Ngày Hôm Qua - Sơn Tùng M-TP",
                "Artist abbreviation 'M-TP' maps to 'Sơn Tùng M-TP'."
        ));

        list.add(new TestCase(
                18, "Group E", "Untitled, 2014", "G-Dragon",
                Arrays.asList(
                        candidate("cand_0", "무제(無題) (Untitled, 2014)", "권지용 (G-DRAGON)", "KWON JI YONG"),
                        candidate("cand_1", "Crooked", "G-DRAGON", "COUP D'ETAT")
                ),
                Set.of("cand_0"),
                "Untitled, 2014 - G-Dragon / Kwon Ji Yong",
                "Stage name G-Dragon matches Korean real name 권지용."
        ));

        // Group F - Featured Artists (3 cases)
        list.add(new TestCase(
                19, "Group F", "Sunflower", "Post Malone",
                Arrays.asList(
                        candidate("cand_0", "Sunflower (Spider-Man: Into the Spider-Verse)", "Post Malone, Swae Lee", "Hollywood's Bleeding"),
                        candidate("cand_1", "Circles", "Post Malone", "Hollywood's Bleeding")
                ),
                Set.of("cand_0"),
                "Sunflower - Post Malone & Swae Lee",
                "Missing co-artist Swae Lee in target should still match."
        ));

        list.add(new TestCase(
                20, "Group F", "Love The Way You Lie", "Eminem feat. Rihanna",
                Arrays.asList(
                        candidate("cand_0", "Love The Way You Lie", "Eminem", "Recovery"),
                        candidate("cand_1", "Not Afraid", "Eminem", "Recovery")
                ),
                Set.of("cand_0"),
                "Love The Way You Lie - Eminem",
                "Candidate missing feat artist Rihanna is valid lyric source."
        ));

        list.add(new TestCase(
                21, "Group F", "Industry Baby", "Lil Nas X & Jack Harlow",
                Arrays.asList(
                        candidate("cand_0", "INDUSTRY BABY", "Lil Nas X feat. Jack Harlow", "MONTERO"),
                        candidate("cand_1", "MONTERO (Call Me By Your Name)", "Lil Nas X", "MONTERO")
                ),
                Set.of("cand_0"),
                "Industry Baby - Lil Nas X & Jack Harlow",
                "Delimiter '&' matches 'feat.'."
        ));

        // Group G - Different Artists, Same Title (3 cases)
        // QUY TẮC: BẮT BUỘC đúng artist + composition!
        list.add(new TestCase(
                22, "Group G", "Hello", "Lionel Richie",
                Arrays.asList(
                        candidate("cand_0", "Hello", "Adele", "25"),
                        candidate("cand_1", "Hello", "Lionel Richie", "Can't Slow Down"),
                        candidate("cand_2", "All Night Long (All Night)", "Lionel Richie", "Can't Slow Down")
                ),
                Set.of("cand_1"),
                "Hello - Lionel Richie",
                "Title collision: Must pick Lionel Richie, not Adele."
        ));

        list.add(new TestCase(
                23, "Group G", "Creep", "TLC",
                Arrays.asList(
                        candidate("cand_0", "Creep", "Radiohead", "Pablo Honey"),
                        candidate("cand_1", "Creep", "TLC", "CrazySexyCool"),
                        candidate("cand_2", "Waterfalls", "TLC", "CrazySexyCool")
                ),
                Set.of("cand_1"),
                "Creep - TLC",
                "Title collision: Must pick TLC, not Radiohead."
        ));

        list.add(new TestCase(
                24, "Group G", "Memories", "David Guetta feat. Kid Cudi",
                Arrays.asList(
                        candidate("cand_0", "Memories", "Maroon 5", "Jordi"),
                        candidate("cand_1", "Memories", "David Guetta", "One Love"),
                        candidate("cand_2", "Titanium", "David Guetta", "Nothing but the Beat")
                ),
                Set.of("cand_1"),
                "Memories - David Guetta",
                "Title collision: Must pick David Guetta, not Maroon 5."
        ));

        // Group H - Similar Titles / False Positive (3 cases)
        list.add(new TestCase(
                25, "Group H", "Love Story", "Taylor Swift",
                Arrays.asList(
                        candidate("cand_0", "Love Song", "Sara Bareilles", "Little Voice"),
                        candidate("cand_1", "Lover", "Taylor Swift", "Lover"),
                        candidate("cand_2", "Blank Space", "Taylor Swift", "1989")
                ),
                Set.of("none_of_above"),
                "none_of_above",
                "Target is Love Story, candidates are Love Song / Lover. Must reject all."
        ));

        list.add(new TestCase(
                26, "Group H", "Wrong Times", "Puppy",
                Arrays.asList(
                        candidate("cand_0", "Wrong Side", "Puppy", "The Great Silence"),
                        candidate("cand_1", "Good Times", "All Time Low", "Last Young Renegade")
                ),
                Set.of("none_of_above"),
                "none_of_above",
                "Candidate has similar words but different song. Must reject all."
        ));

        list.add(new TestCase(
                27, "Group H", "Bad Guy", "Billie Eilish",
                Arrays.asList(
                        candidate("cand_0", "Bad Romance", "Lady Gaga", "The Fame Monster"),
                        candidate("cand_1", "Bad Liar", "Imagine Dragons", "Origins"),
                        candidate("cand_2", "bad guy", "Billie Eilish", "WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?")
                ),
                Set.of("cand_2"),
                "bad guy - Billie Eilish",
                "Distinguish from other 'Bad *' titles and pick exact match."
        ));

        // Group I - Taylor's Version / Re-recording (2 cases)
        // QUY TẮC: Cùng lyric identity (lời giống hệt nhau)
        list.add(new TestCase(
                28, "Group I", "Love Story (Taylor's Version)", "Taylor Swift",
                Arrays.asList(
                        candidate("cand_0", "Love Story", "Taylor Swift", "Fearless (2008)"),
                        candidate("cand_1", "Love Story (Taylor's Version)", "Taylor Swift", "Fearless (Taylor's Version)"),
                        candidate("cand_2", "You Belong With Me", "Taylor Swift", "Fearless")
                ),
                Set.of("cand_0", "cand_1"),
                "Love Story - Taylor Swift (Original or TV)",
                "Lyrics are identical; either recording candidate provides valid lyric identity."
        ));

        list.add(new TestCase(
                29, "Group I", "Fearless", "Taylor Swift",
                Arrays.asList(
                        candidate("cand_0", "Fearless (Taylor's Version)", "Taylor Swift", "Fearless (Taylor's Version)"),
                        candidate("cand_1", "Fifteen", "Taylor Swift", "Fearless")
                ),
                Set.of("cand_0"),
                "Fearless - Taylor Swift",
                "TV recording provides valid lyrics for playing original."
        ));

        // Group J - Different Lyric Versions (3 cases)
        // QUY TẮC: BẮT BUỘC PHÂN BIỆT vì lyrics khác nhau đáng kể!
        list.add(new TestCase(
                30, "Group J", "All Too Well (10 Minute Version) (Taylor's Version)", "Taylor Swift",
                Arrays.asList(
                        candidate("cand_0", "All Too Well", "Taylor Swift", "Red"),
                        candidate("cand_1", "All Too Well (10 Minute Version)", "Taylor Swift", "Red (Taylor's Version)"),
                        candidate("cand_2", "Red", "Taylor Swift", "Red")
                ),
                Set.of("cand_1"),
                "All Too Well (10 Minute Version) - Taylor Swift",
                "10-minute version has substantially extended lyrics. Standard version is INVALID."
        ));

        list.add(new TestCase(
                31, "Group J", "All Too Well", "Taylor Swift",
                Arrays.asList(
                        candidate("cand_0", "All Too Well", "Taylor Swift", "Red"),
                        candidate("cand_1", "All Too Well (10 Minute Version)", "Taylor Swift", "Red (Taylor's Version)")
                ),
                Set.of("cand_0"),
                "All Too Well (Standard Version) - Taylor Swift",
                "Standard 5-minute song cannot use 10-minute extended lyrics."
        ));

        list.add(new TestCase(
                32, "Group J", "White & Nerdy", "Weird Al Yankovic",
                Arrays.asList(
                        candidate("cand_0", "Ridin'", "Chamillionaire feat. Krayzie Bone", "The Sound of Revenge"),
                        candidate("cand_1", "White & Nerdy", "Weird Al Yankovic", "Straight Outta Lynwood")
                ),
                Set.of("cand_1"),
                "White & Nerdy - Weird Al Yankovic",
                "Parody song: Must NOT pick original track 'Ridin'' because lyrics are completely different."
        ));

        // Group K - Completely Wrong Candidates (3 cases)
        // QUY TẮC: All candidates wrong => none_of_above
        list.add(new TestCase(
                33, "Group K", "Vết Mưa", "Vũ Cát Tường",
                Arrays.asList(
                        candidate("cand_0", "Attention", "Charlie Puth", "Voicenotes"),
                        candidate("cand_1", "Shape of You", "Ed Sheeran", "÷"),
                        candidate("cand_2", "Lover", "Taylor Swift", "Lover")
                ),
                Set.of("none_of_above"),
                "none_of_above",
                "Completely irrelevant candidate list. Must select none_of_above."
        ));

        list.add(new TestCase(
                34, "Group K", "Chúng Ta Của Hiện Tại", "Sơn Tùng M-TP",
                Arrays.asList(
                        candidate("cand_0", "Nàng Thơ", "Hoàng Dũng", "25"),
                        candidate("cand_1", "Gặp Nhưng Không Ở Lại", "Hiền Hồ", ""),
                        candidate("cand_2", "Bước Qua Nhau", "Vũ", "Một Vạn Năm")
                ),
                Set.of("none_of_above"),
                "none_of_above",
                "Completely unrelated Vietnamese tracks. Must reject."
        ));

        list.add(new TestCase(
                35, "Group K", "Dusk Till Dawn", "ZAYN feat. Sia",
                Arrays.asList(
                        candidate("cand_0", "Chandelier", "Sia", "1000 Forms of Fear"),
                        candidate("cand_1", "Pillowtalk", "ZAYN", "Mind of Mine")
                ),
                Set.of("none_of_above"),
                "none_of_above",
                "Candidates are other songs by the same artists. Target composition not present. Must reject."
        ));

        return list;
    }

    private static SongCandidate<String> candidate(String id, String title, String artist, String album) {
        SongCandidate<String> c = new SongCandidate<>();
        c.setId(id);
        c.setTitle(title);
        c.setArtist(artist);
        c.setAlbum(album);
        return c;
    }
}

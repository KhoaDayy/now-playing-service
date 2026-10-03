package com.widdit.nowplaying.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongUtil {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    // 常见越南语音译/翻唱中文歌映射字典 (Normalized Vietnamese -> Chinese)
    private static final Map<String, String> CPOP_MAPPING = new HashMap<>();

    static {
        CPOP_MAPPING.put("mang chung", "芒种");
        CPOP_MAPPING.put("do ta khong do nang", "渡我不渡她");
        CPOP_MAPPING.put("tay trai chi trang", "左手指月");
        CPOP_MAPPING.put("thoi khong sai lech", "错位时空");
        CPOP_MAPPING.put("phi dieu va ve sau", "飞鸟和蝉");
        CPOP_MAPPING.put("anh trang sang va not chu sa", "白月光与朱砂痣");
        CPOP_MAPPING.put("yen vo hiet", "燕无歇");
        CPOP_MAPPING.put("da vu", "夜舞");
        CPOP_MAPPING.put("khoi phong lieu", "起风了");
        CPOP_MAPPING.put("hoc meo keu", "学猫叫");
        CPOP_MAPPING.put("xich linh", "赤伶");
        CPOP_MAPPING.put("tuyet lac ha dich thanh am", "雪落下的声音");
        CPOP_MAPPING.put("thap nien nhan gian", "十年人间");
        CPOP_MAPPING.put("ha giang nam", "下江南");
        CPOP_MAPPING.put("cau kinh thuong", "句号");
        CPOP_MAPPING.put("tieu tao hoa", "笑纳");
        CPOP_MAPPING.put("thanh ti", "青丝");
        CPOP_MAPPING.put("son ha lenh", "山河令");
    }

    /**
     * 将窗口标题解析为单独的歌名和歌手名（原生保留）
     */
    public static String[] parseWindowTitle(String windowTitle) {
        String pivot = " - ";
        String title;
        String author;

        if (windowTitle.contains(pivot)) {
            int pos = windowTitle.lastIndexOf(pivot);
            title = windowTitle.substring(0, pos).trim();
            author = windowTitle.substring(pos + pivot.length()).trim();
        } else {
            title = windowTitle;
            author = "";
        }

        return new String[] {title, author};
    }

    /**
     * 判断某个分段是否完全是噪音（如 "KARAOKE LIVE VERSION", "Beat Chuẩn Tone Nam", "Chạy chữ dễ hát 2026"）
     */
    public static boolean isPureNoiseSegment(String text) {
        if (text == null || text.isBlank()) return true;
        String s = text.trim();
        String stripped = s.replaceAll("(?i)\\b(karaoke|instrumental|off\\s*vocal|backing\\s*track|acapella|beat\\s*phối\\s*chuẩn|beat\\s*phoi\\s*chuan|beat\\s*chuẩn|beat\\s*chuan|beat\\s*gốc|beat\\s*goc|beat\\s*phối|beat\\s*phoi|beat\\s*hay|beat\\s*vip|phối\\s*chuẩn|phoi\\s*chuan|chuẩn\\s*tone|chuan\\s*tone|tone\\s*chuẩn|tone\\s*chuan|tone\\s*nam|tone\\s*nữ|tone\\s*nu|tone\\s*song\\s*ca|hạ\\s*tone|tăng\\s*tone|ha\\s*tone|tang\\s*tone|organ\\s*nhạc\\s*sống|organ\\s*nhac\\s*song|nhạc\\s*sống|nhac\\s*song|chạy\\s*chữ\\s*dễ\\s*hát|chay\\s*chu\\s*de\\s*hat|chạy\\s*chữ|chay\\s*chu|dễ\\s*hát|de\\s*hat|beat|tone|chuẩn|chuan|phối|phoi|gốc|goc|organ|official\\s*music\\s*video|official\\s*mv|official\\s*audio|official\\s*video|official|mv|lyric\\s*video|lyrics\\s*video|video\\s*lyric|audio\\s*lyrics|lyrics\\s*audio|visualizer|full\\s*hd|live\\s*version|hd|4k|1080p|720p)\\b", " ");
        stripped = stripped.replaceAll("(?i)(消音伴奏|卡拉OK|伴奏版|伴奏|KTV版|KTV)", " ");
        stripped = stripped.replaceAll("\\b(19|20)\\d{2}\\b", " ");
        stripped = stripped.replaceAll("[^\\p{L}\\p{N}]", "").trim();
        return stripped.isEmpty();
    }

    /**
     * 解析并清洗窗口标题，剔除 Karaoke / Beat / Tone / YouTube 噪音
     * 返回 [cleanTitle, cleanAuthor]
     */
    /**
     * 解析并清洗窗口标题，剔除 Karaoke / Beat / Tone / YouTube 噪音
     * 返回 [cleanTitle, cleanAuthor]
     */
    public static String[] parseCleanTitle(String windowTitle) {
        if (windowTitle == null || windowTitle.isBlank()) {
            return new String[] {"", ""};
        }

        // 0. Unicode NFC 规范化（解决 YouTube 越南语 decomposed NFD 字符导致正则失效的问题）
        String raw = Normalizer.normalize(windowTitle.trim(), Normalizer.Form.NFC);

        // 1. 如果包含中文字符，优先尝试提取中文歌名与歌手
        String chinese = extractChineseQuery(raw);
        if (chinese.length() >= 2) {
            String[] rawParts = parseWindowTitle(raw);
            String chineseTitle = extractChineseQuery(rawParts[0]);
            String chineseAuthor = extractChineseQuery(rawParts[1]);

            if (!chineseTitle.isBlank()) {
                return new String[] {chineseTitle, chineseAuthor};
            }
        }

        // 2. 清洗行内噪音与视频标签（如 [MV #RI7], (Official Music Video), [Karaoke] 等），同时保留 (feat. ...) 等有效信息
        String cleaned = cleanKaraokeTitle(raw);

        // 3. 收集所有有效分段（按管道符、斜杠、破折号分割）
        String[] rawSplits = cleaned.split("(?:\\s+[-—–]+\\s+|[|/\\\\]+)");
        java.util.List<String> validParts = new java.util.ArrayList<>();
        for (String p : rawSplits) {
            String pt = p.trim();
            if (!pt.isBlank() && !isPureNoiseSegment(pt)) {
                validParts.add(pt);
            }
        }

        // 4. 智能且非硬编码地解析歌名与歌手
        String[] resolved = resolveTitleAndArtist(validParts);
        String title = resolved[0];
        String author = resolved[1];

        // 5. 对歌手名进行清理：去除括号中的作曲者/备注，如 "KHẢI ĐĂNG (THANH HƯNG)" -> "KHẢI ĐĂNG"
        if (!author.isBlank()) {
            author = author.replaceAll("[\\[(【〔（][^\\])）】〕]*[\\])）】〕]", " ")
                           .replaceAll("\\s+", " ").trim();
            if (isChannelOrNoise(author)) {
                author = "";
            }
        }

        // 6. 对歌名进行二次清理：去除残留的括号噪音或作曲备注
        title = title.replaceAll("(?i)[\\[(【〔（][^\\])）】〕]*(?:karaoke|beat|tone|mv|hd|visualizer|chạy\\s*chữ|dễ\\s*hát)[^\\])）】〕]*[\\])）】〕]", " ");
        if (author.isBlank() && title.contains("(") && title.endsWith(")")) {
            int openIdx = title.lastIndexOf('(');
            String noteInParen = title.substring(openIdx + 1, title.length() - 1).trim();
            if (noteInParen.length() >= 2 && noteInParen.length() <= 30 && !isPureNoiseSegment(noteInParen)) {
                author = noteInParen;
                title = title.substring(0, openIdx).trim();
            }
        }

        // 6.1 提取并剥离歌名中的伴唱/合作艺术家（支持括号内如 "(feat. Bảo Anh)" 与行内如 "ft Pinny"）
        Pattern featPattern = Pattern.compile("(?i)(?:\\s+[\\[(【〔（]?|[\\[(【〔（])(?:feat\\.?|ft\\.?|featuring|cùng\\s+với)\\s+([^\\])）】〕]+)[\\])）】〕]?");
        Matcher featMatcher = featPattern.matcher(title);
        if (featMatcher.find()) {
            String featArtist = featMatcher.group(1).trim();
            title = title.substring(0, featMatcher.start()).trim();
            if (!featArtist.isBlank() && !isPureNoiseSegment(featArtist)) {
                featArtist = featArtist.replaceAll("(?i)\\b(official|mv|audio|video|lyric|lyrics)\\b", "").trim();
                featArtist = featArtist.replaceAll("\\s*,\\s*", " / ");
                if (!featArtist.isBlank()) {
                    if (author.isBlank()) {
                        author = featArtist;
                    } else if (!isSameEntity(author, featArtist) && !author.toLowerCase().contains(featArtist.toLowerCase())) {
                        author = author + " / " + featArtist;
                    }
                }
            }
        }

        // 6.2 提取并剥离歌名中的制作人标注（如 "(prod. Masew)" 或 "prod. by Masew"）
        Pattern prodPattern = Pattern.compile("(?i)(?:\\s+[\\[(【〔（]?|[\\[(【〔（])(?:prod\\.?|produced\\s+by)\\s+([^\\])）】〕]+)[\\])）】〕]?");
        Matcher prodMatcher = prodPattern.matcher(title);
        if (prodMatcher.find()) {
            String prodArtist = prodMatcher.group(1).trim();
            title = title.substring(0, prodMatcher.start()).trim();
            if (!prodArtist.isBlank() && !isPureNoiseSegment(prodArtist)) {
                prodArtist = prodArtist.replaceAll("(?i)\\b(official|mv|audio|video|lyric|lyrics)\\b", "").trim();
                prodArtist = prodArtist.replaceAll("\\s*,\\s*", " / ");
                if (!prodArtist.isBlank()) {
                    if (author.isBlank()) {
                        author = prodArtist;
                    } else if (!isSameEntity(author, prodArtist) && !author.toLowerCase().contains(prodArtist.toLowerCase())) {
                        author = author + " / " + prodArtist;
                    }
                }
            }
        }

        title = stripQuotes(title).replaceAll("\\s+", " ").trim();

        // 7. 检查歌名是否匹配 C-Pop 越南语音译字典
        String mappedChinese = lookupCpopMapping(title);
        if (mappedChinese != null && !mappedChinese.isBlank()) {
            title = mappedChinese;
        }

        return new String[] {title, author};
    }

    /**
     * 智能解析多段 YouTube / 窗口分段，避免硬编码偏见
     */
    public static String[] resolveTitleAndArtist(java.util.List<String> validParts) {
        if (validParts == null || validParts.isEmpty()) {
            return new String[] {"", ""};
        }
        if (validParts.size() == 1) {
            return new String[] {stripQuotes(validParts.get(0)), ""};
        }

        if (validParts.size() >= 3) {
            String part0 = validParts.get(0).trim();
            String part1 = validParts.get(1).trim();
            String lastPart = validParts.get(validParts.size() - 1).trim();

            // 规则 A: lastPart 与 part0 是同一实体（如 "LIL SHADY" 与 "Lil Shady"）
            // 说明 part0 为歌手/频道名，part1 为真实歌名！
            if (isSameEntity(part0, lastPart)) {
                return new String[] {part1, part0};
            }

            // 规则 B: lastPart 与 part1 是同一实体（如 "Sơn Tùng M-TP" 与 "Son Tung M-TP Official"）
            // 说明 part1 为歌手/频道名，part0 为真实歌名！
            if (isSameEntity(part1, lastPart)) {
                return new String[] {part0, part1};
            }

            // 规则 C: lastPart 本身是纯频道/厂牌名（如 "CT Bắp Official", "VEVO", "Warner Music", "Topic"）
            // 剔除 lastPart 后对前两段进行多维度智能仲裁
            if (isChannelOrNoise(lastPart) || lastPart.equalsIgnoreCase("topic") || lastPart.toLowerCase().endsWith("topic")) {
                // YouTube Topic 频道格式极其严格："Song Title - Artist - Topic" (如 "Wrong Times (Beat) - puppy - Topic")
                if (lastPart.equalsIgnoreCase("topic") || lastPart.toLowerCase().contains("topic")) {
                    return new String[] {part0, part1};
                }
                return determineTitleAndArtist(part0, part1);
            }

            // 其他 3 段式：对 part0 和 part1 进行多维度智能仲裁
            return determineTitleAndArtist(part0, part1);
        }

        // 恰好 2 段：进行多维度特征打分仲裁
        return determineTitleAndArtist(validParts.get(0).trim(), validParts.get(1).trim());
    }

    /**
     * 多维度特征打分仲裁两段中哪一段是歌名、哪一段是歌手
     */
    public static String[] determineTitleAndArtist(String pA, String pB) {
        if (pA == null || pA.isBlank()) return new String[] {pB != null ? stripQuotes(pB) : "", ""};
        if (pB == null || pB.isBlank()) return new String[] {stripQuotes(pA), ""};

        int scoreATitle = 0;
        int scoreBTitle = 0;

        // 1. 引号标记（极强烈的歌名信号："..." 或 「...」 或 '...'）
        if (hasQuotes(pA)) scoreATitle += 25;
        if (hasQuotes(pB)) scoreBTitle += 25;

        // 2. 伴唱/合作与制作信息：
        // 2.1 括号内的伴唱/制作标注 (如 "(feat. ...)", "(prod. ...)") 多依附于歌名
        if (hasFeatInParentheses(pA)) scoreATitle += 15;
        if (hasFeatInParentheses(pB)) scoreBTitle += 15;

        // 2.2 行内无括号的艺人合作标识 (如 "A ft. B", "A x B", "A & B") 是强烈的【歌手列表】信号
        // 包含此类合作词的一侧几乎必定是歌手，另一侧则是歌名！
        boolean bareCollabA = hasBareArtistCollab(pA);
        boolean bareCollabB = hasBareArtistCollab(pB);
        if (bareCollabA && !bareCollabB) scoreBTitle += 30;
        if (bareCollabB && !bareCollabA) scoreATitle += 30;

        // 3. 其他非噪音括号内容（如副标题）
        if (hasParentheses(pA)) scoreATitle += 5;
        if (hasParentheses(pB)) scoreBTitle += 5;

        // 4. 版本/音乐修饰词（Remix, Cover, Live, Acoustic 等）几乎必定修饰歌名
        if (hasSongKeywords(pA)) scoreATitle += 15;
        if (hasSongKeywords(pB)) scoreBTitle += 15;

        // 5. 频道/厂牌/发布者词汇（Official, Records, Entertainment, Channel, Topic）修饰歌手
        if (isChannelOrNoise(pA)) scoreBTitle += 20;
        if (isChannelOrNoise(pB)) scoreATitle += 20;

        // 6. 多词全大写对比（YouTube 常见全大写歌名如 "RAIN IN 7", "SEE TÌNH"）
        boolean multiWordA = isAllCaps(pA) && pA.split("\\s+").length >= 2;
        boolean multiWordB = isAllCaps(pB) && pB.split("\\s+").length >= 2;
        if (multiWordA && !multiWordB) scoreATitle += 10;
        if (multiWordB && !multiWordA) scoreBTitle += 10;

        // 单字全大写缩写/艺名（如 MONO, BTS, IU, EDEN, CL）倾向于歌手
        boolean singleWordUpperA = isAllCaps(pA) && pA.split("\\s+").length == 1;
        boolean singleWordUpperB = isAllCaps(pB) && pB.split("\\s+").length == 1;
        if (singleWordUpperA) scoreBTitle += 8;
        if (singleWordUpperB) scoreATitle += 8;

        // 7. 词数对比：长短句对比（多词更可能是歌名，但若多词一方是合作歌手列表则不加分）
        int wordsA = pA.split("\\s+").length;
        int wordsB = pB.split("\\s+").length;
        if (!bareCollabA && !bareCollabB) {
            if (wordsA >= 2 && wordsB == 1) scoreATitle += 8;
            else if (wordsB >= 2 && wordsA == 1) scoreBTitle += 8;
            else if (wordsA >= 3 && wordsB <= 2) scoreATitle += 5;
            else if (wordsB >= 3 && wordsA <= 2) scoreBTitle += 5;
        }

        // 综合打分决策
        if (scoreATitle > scoreBTitle) {
            return new String[] {stripQuotes(pA), pB};
        } else if (scoreBTitle > scoreATitle) {
            return new String[] {stripQuotes(pB), pA};
        } else {
            // 平局：在没有任何特征指征时，遵循 YouTube/V-Pop 最广泛的标准格式 "Title - Artist" (如 "Wrong Times - puppy", "In Love - Low G")
            return new String[] {stripQuotes(pA), pB};
        }
    }

    /**
     * 获取可能候选组合（[Title, Artist] 以及反转的 [Artist, Title]），供云端 API 双向比对
     */
    public static java.util.List<String[]> getPossibleCandidates(String windowTitle) {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        String[] primary = parseCleanTitle(windowTitle);
        list.add(primary);

        String title = primary[0];
        String author = primary[1];
        if (!author.isBlank() && !title.isBlank() && !title.equalsIgnoreCase(author)) {
            list.add(new String[] {author, title});
        }
        return list;
    }

    /**
     * 剥离歌名中的伴唱/合作标注（如 "(feat. ...)", "[ft. ...]", "ft. ...", "(prod. ...)" 等）
     */
    public static String stripFeatAnnotations(String title) {
        if (title == null || title.isBlank()) return "";
        // 1. 去除括号内的伴唱/制作/合作标签
        String s = title.replaceAll("(?i)\\s*[\\[(【〔（][^\\])）】〕]*(?:feat\\.?|ft\\.?|featuring|cùng\\s+với|x|&|prod\\.?|produced\\s+by)[^\\])）】〕]*[\\])）】〕]", " ");
        // 2. 去除行内的伴唱/制作/合作标签（如 " ft Pinny", " feat. Artist", " prod. Producer"）
        s = s.replaceAll("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|cùng\\s+với|prod\\.?|produced\\s+by)\\b.*$", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * 判断两个实体是否指向同一人（去除声调与大小写后匹配，或包含频道后缀）
     */
    public static boolean isSameEntity(String s1, String s2) {
        if (s1 == null || s2 == null) return false;
        String c1 = s1.replaceAll("(?i)\\b(official|channel|media|records|entertainment|vevo|studio|music)\\b", "").trim();
        String c2 = s2.replaceAll("(?i)\\b(official|channel|media|records|entertainment|vevo|studio|music)\\b", "").trim();
        String n1 = removeAccents((!c1.isBlank() ? c1 : s1).toLowerCase()).replaceAll("[^a-z0-9]", "");
        String n2 = removeAccents((!c2.isBlank() ? c2 : s2).toLowerCase()).replaceAll("[^a-z0-9]", "");
        if (n1.isEmpty() || n2.isEmpty()) return false;
        if (n1.equals(n2)) return true;
        if (n1.contains(n2) || n2.contains(n1)) {
            int minLen = Math.min(n1.length(), n2.length());
            int maxLen = Math.max(n1.length(), n2.length());
            if (minLen >= 4 && (double) minLen / maxLen >= 0.4) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQuotes(String s) {
        if (s == null) return false;
        String t = s.trim();
        return (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2)
                || (t.startsWith("'") && t.endsWith("'") && t.length() >= 2)
                || (t.startsWith("“") && t.endsWith("”"))
                || (t.startsWith("「") && t.endsWith("」"))
                || (t.startsWith("『") && t.endsWith("』"))
                || (t.startsWith("《") && t.endsWith("》"));
    }

    public static String stripQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (hasQuotes(t) && t.length() >= 2) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static boolean hasFeatInParentheses(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.matches(".*[\\[(【〔（].*?(?:feat\\.?|ft\\.?|prod\\.?|produced\\s+by).*?[\\])）】〕].*");
    }

    private static boolean hasBareArtistCollab(String s) {
        if (s == null) return false;
        String stripped = s.replaceAll("[\\[(【〔（][^\\])）】〕]*[\\])）】〕]", " ").toLowerCase();
        return stripped.matches(".*\\b(?:ft\\.?|feat\\.?|featuring|cùng\\s+với|x|&)\\b.*");
    }

    private static boolean hasParentheses(String s) {
        if (s == null) return false;
        return (s.contains("(") && s.contains(")")) || (s.contains("[") && s.contains("]"));
    }

    private static boolean hasSongKeywords(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.contains("remix") || lower.contains("cover")
                || lower.contains("live") || lower.contains("acoustic")
                || lower.contains("version") || lower.contains("ver.")
                || lower.contains("beat") || lower.contains("tone");
    }

    private static boolean isAllCaps(String s) {
        if (s == null || s.length() < 3) return false;
        int upperCount = 0;
        int letterCount = 0;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) {
                    upperCount++;
                }
            }
        }
        return letterCount >= 3 && upperCount == letterCount;
    }

    /**
     * 获取最适合云端/API 搜索的关键词
     */
    public static String getBestSearchKeyword(String windowTitle) {
        if (windowTitle == null || windowTitle.isBlank()) {
            return "";
        }

        // 优先提取中文字符（针对双语标题：如 "Mang Chủng (芒种) - Triệu Phương Tịnh (赵方婧)"）
        String chinese = extractChineseQuery(windowTitle);
        if (chinese.length() >= 2) {
            return chinese;
        }

        String[] parts = parseCleanTitle(windowTitle);
        String title = parts[0];
        String author = parts[1];

        if (!title.isBlank() && !author.isBlank()) {
            String cleanTitle = stripFeatAnnotations(title);
            String primaryArtist = author.split("(?i)\\s*(?:x|&|ft\\.?|feat\\.?|featuring|và|/|•|,|;)\\s*")[0].trim();
            return cleanTitle + " " + primaryArtist;
        }
        return !title.isBlank() ? title : windowTitle;
    }

    /**
     * 清洗 YouTube / Karaoke 标题噪音
     */
    public static String cleanKaraokeTitle(String text) {
        if (text == null) return "";

        String s = text;

        // 去除方括号/圆括号内的噪音标签，例如 [Karaoke], (Beat Chuẩn), 【Tone Nam】, (KARAOKE LIVE VERSION), (Visualizer)
        s = s.replaceAll("(?i)[\\[(【〔（][^\\])）】〕]*(?:karaoke|beat|tone|instrumental|off\\s*vocal|backing\\s*track|acapella|mv|hd|4k|official|lyrics|lyric|lời|phối|gốc|nhạc\\s*sống|nhac\\s*song|organ|live|version|ver|visualizer|audio|video|chạy\\s*chữ|dễ\\s*hát)[^\\])）】〕]*[\\])）】〕]", " ");

        // 替换分隔管道符和多余斜杠
        s = s.replaceAll("[|/\\\\]+", " - ");

        // 去除常见的行内噪音词汇
        s = s.replaceAll("(?i)\\b(karaoke|instrumental|off\\s*vocal|backing\\s*track|acapella|beat\\s*phối\\s*chuẩn|beat\\s*phoi\\s*chuan|beat\\s*chuẩn|beat\\s*chuan|beat\\s*gốc|beat\\s*goc|beat\\s*phối|beat\\s*phoi|beat\\s*hay|beat\\s*vip|phối\\s*chuẩn|phoi\\s*chuan|chuẩn\\s*tone|chuan\\s*tone|tone\\s*chuẩn|tone\\s*chuan|tone\\s*nam|tone\\s*nữ|tone\\s*nu|tone\\s*song\\s*ca|hạ\\s*tone|tăng\\s*tone|ha\\s*tone|tang\\s*tone|organ\\s*nhạc\\s*sống|organ\\s*nhac\\s*song|nhạc\\s*sống|nhac\\s*song|chạy\\s*chữ\\s*dễ\\s*hát|chay\\s*chu\\s*de\\s*hat|chạy\\s*chữ|chay\\s*chu|dễ\\s*hát|de\\s*hat|tone\\s*nam|tone\\s*nữ|tone|chuẩn\\b(?!\\s*bị)|chuan\\b(?!\\s*bi)|official\\s*music\\s*video|official\\s*mv|official\\s*audio|official\\s*video|official|mv|lyric\\s*video|lyrics\\s*video|video\\s*lyric|audio\\s*lyrics|lyrics\\s*audio|visualizer|full\\s*hd|live\\s*version|hd|4k|1080p|720p)\\b", " ");

        // 去除中文伴奏标签
        s = s.replaceAll("(?i)(消音伴奏|卡拉OK|伴奏版|伴奏|KTV版|KTV)", " ");

        // 清理末尾年份如 2024, 2025, 2026
        s = s.replaceAll("\\b(19|20)\\d{2}\\b", " ");

        // 清理两端可能残留的破折号和多余空格
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("^[\\-\\s]+|[\\-\\s]+$", "");

        return s;
    }

    /**
     * 提取文本中的所有中文字符序列（拼接为空格分隔）
     */
    public static String extractChineseQuery(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher m = CHINESE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(m.group());
        }
        return sb.toString().trim();
    }

    /**
     * 判断文本是否包含明显的 Karaoke / Beat / 伴奏 关键词
     */
    public static boolean isKaraokeOrNoise(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        return lower.contains("karaoke") || lower.contains("beat") || lower.contains("tone ")
                || lower.contains("instrumental") || lower.contains("off vocal")
                || lower.contains("backing track") || lower.contains("nhạc sống")
                || lower.contains("chạy chữ") || lower.contains("chay chu")
                || lower.contains("dễ hát") || lower.contains("de hat")
                || lower.contains("伴奏") || lower.contains("卡拉ok") || lower.contains("ktv");
    }

    /**
     * 判断是否是频道名或噪音（避免将 YouTube 频道名误判为歌手）
     */
    public static boolean isChannelOrNoise(String author) {
        if (author == null || author.isBlank()) return false;
        String lower = author.toLowerCase();
        return lower.contains("karaoke") || lower.contains("beat") || lower.contains("channel")
                || lower.contains("kênh") || lower.contains("kenh") || lower.contains("official")
                || lower.contains("tone nam") || lower.contains("tone nữ") || lower.contains("tone nu") || lower.contains("tone song ca")
                || lower.contains("hạ tone") || lower.contains("tăng tone") || lower.contains("chuẩn tone") || lower.contains("tone chuẩn")
                || lower.contains("studio") || lower.contains("records") || lower.contains("media")
                || lower.contains("entertainment") || lower.contains("music official")
                || lower.contains("topic") || lower.contains("organ")
                || lower.contains("mv") || lower.contains("hát cùng") || lower.contains("hat cung")
                || lower.contains("cover") || lower.contains("nhạc sống") || lower.contains("nhac song")
                || lower.contains("phối chuẩn") || lower.contains("phoi chuan")
                || lower.contains("acapella") || lower.contains("instrumental")
                || lower.contains("backing track") || lower.contains("karafun")
                || lower.contains("arirang") || lower.contains("santaclara")
                || lower.contains("chuẩn") || lower.contains("chuan")
                || lower.contains("ktv") || lower.contains("vocal cover");
    }

    /**
     * 查表：越南语音译 -> 中文歌名
     */
    public static String lookupCpopMapping(String vietnameseTitle) {
        if (vietnameseTitle == null || vietnameseTitle.isBlank()) return null;
        String normalized = removeAccents(vietnameseTitle.toLowerCase()).replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
        return CPOP_MAPPING.get(normalized);
    }

    /**
     * 去除越南语声调辅助函数
     */
    public static String removeAccents(String text) {
        if (text == null) return "";
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }
}

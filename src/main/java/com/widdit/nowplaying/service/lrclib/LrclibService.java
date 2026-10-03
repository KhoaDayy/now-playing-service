package com.widdit.nowplaying.service.lrclib;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.util.SongMatchingUtil;
import com.widdit.nowplaying.util.SongUtil;
import com.widdit.nowplaying.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Slf4j
public class LrclibService {

    @Autowired(required = false)
    private JevSongReranker jevSongReranker;

    private static final String USER_AGENT = "NowPlayingService/1.0 (https://github.com/Widdit/now-playing-service)";
    private static final int TIMEOUT_MS = 6000;

    /**
     * 根据关键词获取歌词（优先返回 Synced 歌词）
     *
     * @param keyword 窗口标题或歌曲关键词
     * @return Lyric 对象
     */
    public Lyric getLyric(String keyword) {
        return getLyric(keyword, null);
    }

    /**
     * 根据关键词和时长获取歌词（严格优先返回 Synced 歌词）
     */
    public Lyric getLyric(String keyword, Integer duration) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String[] parseResult = SongUtil.parseCleanTitle(keyword);
        String title = parseResult[0];
        String artist = parseResult[1];

        if (title.isBlank()) {
            title = keyword;
        }

        return getLyric(title, artist, duration);
    }

    /**
     * 根据歌名、歌手和时长获取歌词（严格优先 Synced 同步歌词）
     */
    public Lyric getLyric(String trackName, String artistName, Integer duration) {
        if (trackName == null || trackName.isBlank()) {
            return null;
        }

        log.info("尝试从 LRCLIB 获取歌词: title='{}', artist='{}', duration={}", trackName, artistName, duration);

        String primaryArtist = (artistName != null && !artistName.isBlank())
                ? artistName.split("(?i)\\s*(?:x|&|ft\\.?|feat\\.?|featuring|và|/|•|,|;)\\s*")[0].trim()
                : "";

        // 提取去除 feat / ft 等标注后的纯净基础标题
        String cleanBaseTitle = SongUtil.stripFeatAnnotations(trackName);

        Lyric fallbackPlainLyric = null;

        // 1. 先尝试精确匹配接口 /api/get（仅当包含 Synced 歌词时直接采纳）
        try {
            StringBuilder getUrl = new StringBuilder("https://lrclib.net/api/get?");
            getUrl.append("track_name=").append(URLEncoder.encode(trackName, StandardCharsets.UTF_8));
            if (!primaryArtist.isBlank()) {
                getUrl.append("&artist_name=").append(URLEncoder.encode(primaryArtist, StandardCharsets.UTF_8));
            }
            if (duration != null && duration > 0) {
                getUrl.append("&duration=").append(duration);
            }

            String respBody = sendGet(getUrl.toString());

            // 若原标题未精确匹配，且 cleanBaseTitle 与 trackName 不同，再尝试 cleanBaseTitle
            if ((respBody == null || respBody.isBlank() || respBody.contains("\"error\""))
                    && !cleanBaseTitle.isBlank() && !cleanBaseTitle.equalsIgnoreCase(trackName)) {
                StringBuilder getUrlClean = new StringBuilder("https://lrclib.net/api/get?");
                getUrlClean.append("track_name=").append(URLEncoder.encode(cleanBaseTitle, StandardCharsets.UTF_8));
                if (!primaryArtist.isBlank()) {
                    getUrlClean.append("&artist_name=").append(URLEncoder.encode(primaryArtist, StandardCharsets.UTF_8));
                }
                if (duration != null && duration > 0) {
                    getUrlClean.append("&duration=").append(duration);
                }
                respBody = sendGet(getUrlClean.toString());
            }

            if (respBody != null && !respBody.isBlank()) {
                JSONObject obj = JSON.parseObject(respBody);
                if (obj != null && !obj.containsKey("error")) {
                    if (isSynced(obj)) {
                        Lyric lyric = buildLyricFromJsonObject(obj);
                        if (lyric != null && lyric.getHasLyric()) {
                            log.info("LRCLIB /api/get 精确匹配成功 (Synced): {} - {}", lyric.getTitle(), lyric.getAuthor());
                            return lyric;
                        }
                    } else {
                        // 暂存 Plain 歌词作为兜底备选，绝不提前返回，继续通过 search 查找是否有 synced 版本
                        Lyric plainLyric = buildLyricFromJsonObject(obj);
                        if (plainLyric != null && plainLyric.getHasLyric()) {
                            log.info("LRCLIB /api/get 仅匹配到 Plain 歌词，暂存为兜底，继续检索 Synced 歌词: {} - {}", trackName, primaryArtist);
                            fallbackPlainLyric = plainLyric;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("LRCLIB /api/get 查询未命中: {}", e.getMessage());
        }

        // 2. 构建多轮候选搜索词（全面覆盖：基础纯净标题+歌手、原始标题+歌手、基础标题、原始标题）
        List<String> searchQueries = new ArrayList<>();
        if (!primaryArtist.isBlank()) {
            if (!cleanBaseTitle.isBlank() && !cleanBaseTitle.equalsIgnoreCase(trackName)) {
                searchQueries.add(cleanBaseTitle + " " + primaryArtist);
            }
            searchQueries.add(trackName + " " + primaryArtist);
            // 双向搜索容错：歌手在前、歌名在后
            if (!cleanBaseTitle.isBlank() && !cleanBaseTitle.equalsIgnoreCase(trackName)) {
                searchQueries.add(primaryArtist + " " + cleanBaseTitle);
            }
            searchQueries.add(primaryArtist + " " + trackName);
        }
        if (!cleanBaseTitle.isBlank() && !cleanBaseTitle.equalsIgnoreCase(trackName)) {
            searchQueries.add(cleanBaseTitle);
        }
        searchQueries.add(trackName);

        // 依次执行搜索，严格优先查找 Synced 歌词
        for (String query : searchQueries) {
            try {
                String searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
                String respBody = sendGet(searchUrl);
                if (respBody != null && !respBody.isBlank()) {
                    JSONArray list = JSON.parseArray(respBody);
                    if (list != null && !list.isEmpty()) {
                        Lyric syncedLyric = selectBestSyncedLyric(list, trackName, primaryArtist, duration);
                        if (syncedLyric != null) {
                            log.info("LRCLIB 搜索命中 Synced 歌词 (query='{}'): {} - {}", query, syncedLyric.getTitle(), syncedLyric.getAuthor());
                            return syncedLyric;
                        }

                        // 如果之前没有暂存 Plain 歌词，可顺便暂存一个最匹配的 Plain 歌词备用
                        if (fallbackPlainLyric == null) {
                            fallbackPlainLyric = selectBestPlainLyric(list, trackName, primaryArtist);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("LRCLIB 搜索失败 (query='{}'): {}", query, e.getMessage());
            }
        }

        // 3. 所有搜索均未找到 Synced 歌词，最后降级使用 Plain 歌词
        if (fallbackPlainLyric != null) {
            log.info("LRCLIB 未检索到 Synced 歌词，降级使用 Plain 歌词: {} - {}",
                    fallbackPlainLyric.getTitle(), fallbackPlainLyric.getAuthor());
            return fallbackPlainLyric;
        }

        return null;
    }

    /**
     * 从搜索结果中筛选最匹配的 Synced 同步歌词
     */
    Lyric selectBestSyncedLyric(JSONArray list, String expectedTitle, String expectedArtist, Integer expectedDuration) {
        if (list == null || list.isEmpty()) return null;

        JSONObject bestSynced = null;
        int bestSyncedScore = -1;
        boolean rerankerAccepted = false;
        List<SongCandidate<JSONObject>> candidateList = new ArrayList<>();

        String cleanExpTitle = SongUtil.stripFeatAnnotations(expectedTitle);
        String normExpectedTitle = SongUtil.removeAccents(cleanExpTitle.toLowerCase()).replaceAll("[^a-z0-9]", "");
        String normExpectedArtist = expectedArtist != null ? SongUtil.removeAccents(expectedArtist.toLowerCase()).replaceAll("[^a-z0-9]", "") : "";

        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            if (!isSynced(item)) {
                continue; // 仅筛选具有 Synced 歌词的项
            }

            String title = item.getString("trackName");
            if (title == null || title.isBlank()) {
                title = item.getString("name");
            }
            String artist = item.getString("artistName");

            int similarity = SongMatchingUtil.calculateSimilarity(expectedTitle, expectedArtist, title, artist);

            // 支持交换歌名与歌手进行匹配（针对部分 YouTube 视频标题采用 "Artist - Title"）
            if (similarity < 60 && expectedArtist != null && !expectedArtist.isBlank()) {
                int swappedSimilarity = SongMatchingUtil.calculateSimilarity(expectedArtist, expectedTitle, title, artist);
                similarity = Math.max(similarity, swappedSimilarity);
            }

            // 基础歌名相似或相互包含时，给予匹配加分
            if (title != null) {
                String cleanItemTitle = SongUtil.stripFeatAnnotations(title);
                String normItemTitle = SongUtil.removeAccents(cleanItemTitle.toLowerCase()).replaceAll("[^a-z0-9]", "");
                if (!normExpectedTitle.isBlank() && !normItemTitle.isBlank()) {
                    if (normItemTitle.equals(normExpectedTitle)) {
                        similarity = Math.max(similarity, 85);
                        if (artist != null && !normExpectedArtist.isBlank()) {
                            String normArtist = SongUtil.removeAccents(artist.toLowerCase()).replaceAll("[^a-z0-9]", "");
                            if (normArtist.equals(normExpectedArtist) || normArtist.contains(normExpectedArtist) || normExpectedArtist.contains(normArtist)) {
                                similarity = Math.max(similarity, 95);
                            }
                        }
                    } else if (normExpectedTitle.contains(normItemTitle) || normItemTitle.contains(normExpectedTitle)) {
                        int minLen = Math.min(normExpectedTitle.length(), normItemTitle.length());
                        int maxLen = Math.max(normExpectedTitle.length(), normItemTitle.length());
                        if (minLen >= 3 && (double) minLen / maxLen >= 0.35) {
                            similarity = Math.max(similarity, 75);
                            if (artist != null && !normExpectedArtist.isBlank()) {
                                String normArtist = SongUtil.removeAccents(artist.toLowerCase()).replaceAll("[^a-z0-9]", "");
                                if (normArtist.equals(normExpectedArtist) || normArtist.contains(normExpectedArtist) || normExpectedArtist.contains(normArtist)) {
                                    similarity = Math.max(similarity, 90);
                                }
                            }
                        }
                    }
                }
            }

            // 如果有时长信息，且时长误差在 3 秒内，增加权重
            int itemDuration = 0;
            if (expectedDuration != null && expectedDuration > 0) {
                itemDuration = (int) Math.round(item.getDoubleValue("duration"));
                if (Math.abs(itemDuration - expectedDuration) <= 3) {
                    similarity += 15;
                }
            }

            candidateList.add(SongCandidate.<JSONObject>builder()
                    .id("cand_" + i)
                    .title(title)
                    .artist(artist)
                    .album(item.getString("albumName"))
                    .durationSeconds(itemDuration)
                    .traditionalScore(similarity)
                    .rawObject(item)
                    .build());

            if (similarity > bestSyncedScore) {
                bestSyncedScore = similarity;
                bestSynced = item;
            }
        }

        // Tầng 1: Sử dụng JevSongReranker làm Semantic Reranker thông minh
        if (jevSongReranker != null && !candidateList.isEmpty()) {
            SongCandidate<JSONObject> reranked = jevSongReranker.selectBestCandidate(expectedTitle, expectedArtist, candidateList);
            if (reranked != null) {
                bestSynced = reranked.getRawObject();
                bestSyncedScore = reranked.getTraditionalScore();
                rerankerAccepted = true;
            } else {
                bestSynced = null;
                bestSyncedScore = 0;
            }
        }

        // Jev 已接受的语义匹配独立于传统分；仅传统匹配继续使用 40% 门槛。
        if (bestSynced != null && (rerankerAccepted || bestSyncedScore >= 40)) {
            Lyric lyric = buildLyricFromJsonObject(bestSynced);
            log.info("LRCLIB 命中 Synced 歌词: {} - {} (匹配度: {}%)", lyric.getTitle(), lyric.getAuthor(), bestSyncedScore);
            return lyric;
        }

        return null;
    }

    /**
     * 从搜索结果中筛选最匹配的 Plain 普通歌词（作为兜底）
     */
    private Lyric selectBestPlainLyric(JSONArray list, String expectedTitle, String expectedArtist) {
        if (list == null || list.isEmpty()) return null;

        JSONObject bestPlain = null;
        int bestPlainScore = -1;

        String cleanExpTitle = SongUtil.stripFeatAnnotations(expectedTitle);
        String normExpectedTitle = SongUtil.removeAccents(cleanExpTitle.toLowerCase()).replaceAll("[^a-z0-9]", "");
        String normExpectedArtist = expectedArtist != null ? SongUtil.removeAccents(expectedArtist.toLowerCase()).replaceAll("[^a-z0-9]", "") : "";

        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            String plain = item.getString("plainLyrics");
            if (plain == null || plain.isBlank()) {
                continue;
            }

            String title = item.getString("trackName");
            if (title == null || title.isBlank()) {
                title = item.getString("name");
            }
            String artist = item.getString("artistName");

            int similarity = SongMatchingUtil.calculateSimilarity(expectedTitle, expectedArtist, title, artist);

            if (similarity < 60 && expectedArtist != null && !expectedArtist.isBlank()) {
                int swappedSimilarity = SongMatchingUtil.calculateSimilarity(expectedArtist, expectedTitle, title, artist);
                similarity = Math.max(similarity, swappedSimilarity);
            }

            if (title != null) {
                String cleanItemTitle = SongUtil.stripFeatAnnotations(title);
                String normItemTitle = SongUtil.removeAccents(cleanItemTitle.toLowerCase()).replaceAll("[^a-z0-9]", "");
                if (!normExpectedTitle.isBlank() && !normItemTitle.isBlank()) {
                    if (normItemTitle.equals(normExpectedTitle)) {
                        similarity = Math.max(similarity, 85);
                        if (artist != null && !normExpectedArtist.isBlank()) {
                            String normArtist = SongUtil.removeAccents(artist.toLowerCase()).replaceAll("[^a-z0-9]", "");
                            if (normArtist.equals(normExpectedArtist) || normArtist.contains(normExpectedArtist) || normExpectedArtist.contains(normArtist)) {
                                similarity = Math.max(similarity, 90);
                            }
                        }
                    } else if (normExpectedTitle.contains(normItemTitle) || normItemTitle.contains(normExpectedTitle)) {
                        int minLen = Math.min(normExpectedTitle.length(), normItemTitle.length());
                        int maxLen = Math.max(normExpectedTitle.length(), normItemTitle.length());
                        if (minLen >= 3 && (double) minLen / maxLen >= 0.35) {
                            similarity = Math.max(similarity, 75);
                            if (artist != null && !normExpectedArtist.isBlank()) {
                                String normArtist = SongUtil.removeAccents(artist.toLowerCase()).replaceAll("[^a-z0-9]", "");
                                if (normArtist.equals(normExpectedArtist) || normArtist.contains(normExpectedArtist) || normExpectedArtist.contains(normArtist)) {
                                    similarity = Math.max(similarity, 90);
                                }
                            }
                        }
                    }
                }
            }

            if (similarity > bestPlainScore) {
                bestPlainScore = similarity;
                bestPlain = item;
            }
        }

        if (bestPlain != null && bestPlainScore >= 40) {
            Lyric lyric = buildLyricFromJsonObject(bestPlain);
            log.info("LRCLIB 命中 Plain 歌词: {} - {} (匹配度: {}%)", lyric.getTitle(), lyric.getAuthor(), bestPlainScore);
            return lyric;
        }

        return null;
    }

    /**
     * 辅助搜索歌曲元数据（当国内平台无结果时的元数据兜底）
     */
    public Track searchTrack(String keyword) {
        String[] parseResult = SongUtil.parseCleanTitle(keyword);
        String title = parseResult[0];
        String artist = parseResult[1];

        if (title.isBlank()) {
            title = keyword;
        }

        String primaryArtist = (artist != null && !artist.isBlank())
                ? artist.split("(?i)\\s*(?:x|&|ft\\.?|feat\\.?|featuring|và|/|•|,|;)\\s*")[0].trim()
                : "";

        try {
            String query = !primaryArtist.isBlank() ? title + " " + primaryArtist : title;
            String searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String respBody = sendGet(searchUrl);
            if (respBody != null && !respBody.isBlank()) {
                JSONArray list = JSON.parseArray(respBody);
                if (list != null && !list.isEmpty()) {
                    String cleanExpTitle = SongUtil.stripFeatAnnotations(title);
                    String normExpectedTitle = SongUtil.removeAccents(cleanExpTitle.toLowerCase()).replaceAll("[^a-z0-9]", "");

                    JSONObject bestItem = null;
                    int highestScore = -1;
                    boolean semanticMatchConfirmed = false;
                    List<SongCandidate<JSONObject>> candidateList = new ArrayList<>();

                    for (int i = 0; i < list.size(); i++) {
                        JSONObject item = list.getJSONObject(i);
                        String trackTitle = item.getString("trackName");
                        if (trackTitle == null || trackTitle.isBlank()) {
                            trackTitle = item.getString("name");
                        }
                        String trackArtist = item.getString("artistName");
                        int similarity = SongMatchingUtil.calculateSimilarity(title, artist, trackTitle, trackArtist);

                        if (trackTitle != null) {
                            String cleanItemTitle = SongUtil.stripFeatAnnotations(trackTitle);
                            String normTrackTitle = SongUtil.removeAccents(cleanItemTitle.toLowerCase()).replaceAll("[^a-z0-9]", "");
                            if (!normExpectedTitle.isBlank() && !normTrackTitle.isBlank()) {
                                if (normTrackTitle.equals(normExpectedTitle)) {
                                    similarity = Math.max(similarity, 85);
                                } else if (normTrackTitle.contains(normExpectedTitle) || normExpectedTitle.contains(normTrackTitle)) {
                                    int minLen = Math.min(normExpectedTitle.length(), normTrackTitle.length());
                                    int maxLen = Math.max(normExpectedTitle.length(), normTrackTitle.length());
                                    if (minLen >= 3 && (double) minLen / maxLen >= 0.35) {
                                        similarity = Math.max(similarity, 75);
                                    }
                                }
                            }
                        }

                        // 优先选择有 Synced 歌词的 Track
                        if (isSynced(item)) {
                            similarity += 15;
                        }

                        candidateList.add(SongCandidate.<JSONObject>builder()
                                .id("cand_" + i)
                                .title(trackTitle)
                                .artist(trackArtist)
                                .album(item.getString("albumName"))
                                .traditionalScore(similarity)
                                .rawObject(item)
                                .build());

                        if (similarity > highestScore && similarity >= 40) {
                            highestScore = similarity;
                            bestItem = item;
                        }
                    }

                    if (jevSongReranker != null && !candidateList.isEmpty()) {
                        SongCandidate<JSONObject> reranked = jevSongReranker.selectBestCandidate(title, artist, candidateList);
                        if (reranked != null) {
                            bestItem = reranked.getRawObject();
                            highestScore = reranked.getTraditionalScore();
                            semanticMatchConfirmed = reranked.isSemanticMatchConfirmed();
                        } else {
                            bestItem = null;
                            highestScore = 0;
                        }
                    }

                    if (bestItem != null) {
                        String trackTitle = bestItem.getString("trackName");
                        if (trackTitle == null || trackTitle.isBlank()) {
                            trackTitle = bestItem.getString("name");
                        }
                        // 如果获取到的标题形如 "Artist - Title"，清洗提取真实歌名
                        String[] cleanParts = SongUtil.parseCleanTitle(trackTitle);
                        if (!cleanParts[0].isBlank()) {
                            trackTitle = cleanParts[0];
                        }
                        String trackArtist = bestItem.getString("artistName");
                        int durationSec = (int) Math.round(bestItem.getDoubleValue("duration"));
                        String album = bestItem.getString("albumName");

                        return Track.builder()
                                .id("lrclib-" + bestItem.getLongValue("id"))
                                .title(trackTitle)
                                .author(trackArtist != null ? trackArtist : "")
                                .album(album != null ? album : "")
                                .duration(durationSec)
                                .durationHuman(TimeUtil.getFormattedDuration(durationSec))
                                .cover("https://gitee.com/widdit/now-playing/raw/master/spotify_no_cover.jpg")
                                .url("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
                                .isVideo(false)
                                .isAdvertisement(false)
                                .inLibrary(false)
                                .semanticMatchConfirmed(semanticMatchConfirmed)
                                .build();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LRCLIB 搜索 Track 元数据失败: {}", e.getMessage());
        }

        return null;
    }

    private boolean isSynced(JSONObject obj) {
        if (obj == null) return false;
        String synced = obj.getString("syncedLyrics");
        return synced != null && !synced.isBlank();
    }

    private Lyric buildLyricFromJsonObject(JSONObject obj) {
        if (obj == null) return null;

        String synced = obj.getString("syncedLyrics");
        String plain = obj.getString("plainLyrics");

        // 优先使用 Synced 歌词，若无再降级使用 Plain
        String lyricText = (synced != null && !synced.isBlank()) ? synced : plain;
        boolean hasLyric = lyricText != null && !lyricText.isBlank();

        String title = obj.getString("trackName");
        if (title == null || title.isBlank()) {
            title = obj.getString("name");
        }
        String artist = obj.getString("artistName");
        int duration = (int) Math.round(obj.getDoubleValue("duration"));

        return Lyric.builder()
                .source("lrclib")
                .title(title != null ? title : "")
                .author(artist != null ? artist : "")
                .duration(duration)
                .hasLyric(hasLyric)
                .hasTranslatedLyric(false)
                .hasKaraokeLyric(false)
                .lrc(hasLyric ? lyricText : "")
                .translatedLyric("")
                .karaokeLyric("")
                .build();
    }

    private String sendGet(String url) {
        int maxRetries = 2;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent("NowPlayingLyrics/1.0.0 (contact@example.com)")
                        .header("Accept", "application/json")
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .timeout(TIMEOUT_MS)
                        .method(Connection.Method.GET)
                        .execute();

                if (response.statusCode() == 200) {
                    return response.body();
                } else if (response.statusCode() == 503 || response.statusCode() == 429) {
                    if (attempt < maxRetries) {
                        Thread.sleep(400);
                        continue;
                    }
                }
            } catch (Exception e) {
                log.debug("HTTP GET 请求失败 [{}]: {}", url, e.getMessage());
            }
        }
        return null;
    }
}

package com.widdit.nowplaying.service.netease;

import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.service.AudioService;
import com.widdit.nowplaying.service.SettingsService;
import com.widdit.nowplaying.util.SongMatchingUtil;
import com.widdit.nowplaying.util.SongUtil;
import com.widdit.nowplaying.util.TimeUtil;
import com.widdit.nowplaying.util.lyric.generator.LysGenerator;
import com.widdit.nowplaying.util.lyric.model.LyricLine;
import com.widdit.nowplaying.util.lyric.parser.YrcParser;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import java.util.ArrayList;

@Service
@Slf4j
public class NeteaseMusicService {

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private AudioService audioService;
    @Autowired(required = false)
    private JevSongReranker jevSongReranker;

    // 缓存相关变量
    private String prevKeyword;
    private Track prevTrack;

    // 锁对象
    private final Object cacheLock = new Object();

    String sendSearchRequest(Map<String, String> data) throws Exception {
        return EapiHelper.post("https://interface3.music.163.com/eapi/search/get", data);
    }

    String sendLyricRequest(Map<String, String> data) throws Exception {
        return EapiHelper.post("https://interface3.music.163.com/eapi/song/lyric/v1", data);
    }

    /**
     * 根据关键词搜索歌曲，返回歌曲信息对象
     * @param keyword 关键词
     * @return
     */
    public Track search(String keyword) throws Exception {
        log.info("获取网易云音乐歌曲信息..");

        // 尝试从缓存获取 (加锁读取，保证读取到的是完整的一组数据)
        synchronized (cacheLock) {
            if (Objects.equals(keyword, prevKeyword) && prevTrack != null) {
                log.info("命中歌曲缓存：" + keyword);
                return prevTrack;
            }
        }

        // 缓存未命中，执行网络请求逻辑
        // 封装请求参数对象
        String searchKeyword = SongUtil.getBestSearchKeyword(keyword);
        if (searchKeyword == null || searchKeyword.isBlank()) {
            searchKeyword = keyword;
        }

        Map<String, String> data = new HashMap<>();
        data.put("s", searchKeyword);
        data.put("limit", "5");
        data.put("offset", "0");
        data.put("type", "1");
        data.put("csrf_token", "");

        // 发送搜索歌曲请求
        String respStr = sendSearchRequest(data);

        // 解析 JSON 字符串为 JSONObject
        JSONObject jsonObject = JSON.parseObject(respStr);

        // 检查响应数据的 code
        if (jsonObject == null || !jsonObject.containsKey("code") || jsonObject.getIntValue("code") != 200) {
            throw new RuntimeException("网易云音乐歌曲信息获取失败，响应码错误（" + respStr + "）");
        }

        // 提取所需字段
        JSONArray songs = jsonObject.getJSONObject("result").getJSONArray("songs");

        // 检查数组是否为空
        if (songs == null || songs.isEmpty()) {
            throw new RuntimeException("网易云音乐歌曲信息获取失败，搜索结果为空");
        }

        // 最多遍历前 5 个元素
        int maxCount = Math.min(songs.size(), 5);

        // 解析出本地歌曲信息，用于后续计算歌曲信息匹配度
        String[] parseResult = SongUtil.parseCleanTitle(keyword);
        String localTitle = parseResult[0];
        String localAuthor = parseResult[1];

        // 用于记录候选歌曲列表（供 Jev Reranker 及 Fallback 使用）
        List<SongCandidate<JSONObject>> candidateList = new ArrayList<>();
        JSONObject bestMatchSong = null;
        int highestSimilarity = -1;
        boolean rerankerAccepted = false;
        boolean semanticMatchConfirmed = false;

        // 遍历歌曲数组并用 SongMatchingUtil 预先计算传统匹配分
        for (int index = 0; index < maxCount; index++) {
            JSONObject song = songs.getJSONObject(index);

            // 提取歌曲标题
            String songTitle = song.getString("name");

            // 提取歌手名
            JSONArray artists = song.getJSONArray("artists");
            StringBuilder authorBuilder = new StringBuilder();
            for (int i = 0; i < artists.size(); i++) {
                if (authorBuilder.length() > 0) {
                    authorBuilder.append(" / ");
                }
                authorBuilder.append(artists.getJSONObject(i).getString("name"));
            }
            String songAuthor = authorBuilder.toString();

            // 计算相似度（支持正常方向及反转方向）
            int similarity = SongMatchingUtil.calculateSimilarity(localTitle, localAuthor, songTitle, songAuthor);
            if (similarity < 60 && !localAuthor.isBlank()) {
                int swapped = SongMatchingUtil.calculateSimilarity(localAuthor, localTitle, songTitle, songAuthor);
                similarity = Math.max(similarity, swapped);
            }

            // 如果有时长信息，且时长误差在 3 秒内，增加权重
            int currentTotalSec = audioService.getTotalSeconds();
            int songSec = 0;
            if (currentTotalSec > 0) {
                long durMs = song.getLongValue("duration");
                if (durMs > 0) {
                    songSec = (int) (durMs / 1000);
                    if (Math.abs(songSec - currentTotalSec) <= 3) {
                        similarity += 15;
                    }
                }
            }

            JSONObject albumObj = song.getJSONObject("album");
            String albumName = albumObj != null ? albumObj.getString("name") : "";

            candidateList.add(SongCandidate.<JSONObject>builder()
                    .id("cand_" + index)
                    .title(songTitle)
                    .artist(songAuthor)
                    .album(albumName)
                    .durationSeconds(songSec)
                    .traditionalScore(similarity)
                    .rawObject(song)
                    .build());

            // 记录传统最高相似度
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatchSong = song;
            }
        }

        // Tầng 1: Sử dụng JevSongReranker làm Semantic Reranker thông minh
        if (jevSongReranker != null && !candidateList.isEmpty()) {
            SongCandidate<JSONObject> reranked = jevSongReranker.selectBestCandidate(localTitle, localAuthor, candidateList);
            if (reranked != null) {
                bestMatchSong = reranked.getRawObject();
                highestSimilarity = reranked.getTraditionalScore();
                rerankerAccepted = true;
                semanticMatchConfirmed = reranked.isSemanticMatchConfirmed();
            } else {
                bestMatchSong = null;
                highestSimilarity = 0;
            }
        }

        // 双向重试机制：若首轮搜索未达到及格线 (40%) 且存在歌手信息，尝试反转关键词搜索（应对 YouTube "Ca Sĩ - Bài Hát" 场景）
        if (!rerankerAccepted && highestSimilarity < 40 && !localAuthor.isBlank()) {
            log.info("网易云音乐首轮未匹配到合格歌曲 (最高相似度: {}%)，尝试反转关键词重试...", highestSimilarity);
            try {
                String retryQuery = localAuthor + " " + localTitle;
                Map<String, String> retryData = new HashMap<>();
                retryData.put("s", retryQuery);
                retryData.put("limit", "5");
                retryData.put("offset", "0");
                retryData.put("type", "1");
                retryData.put("csrf_token", "");

                String retryResp = sendSearchRequest(retryData);
                JSONObject retryObj = JSON.parseObject(retryResp);
                if (retryObj != null && retryObj.containsKey("code") && retryObj.getIntValue("code") == 200) {
                    JSONArray retrySongs = retryObj.getJSONObject("result").getJSONArray("songs");
                    if (retrySongs != null && !retrySongs.isEmpty()) {
                        int rCount = Math.min(retrySongs.size(), 5);
                        List<SongCandidate<JSONObject>> retryCandidates = new ArrayList<>();
                        for (int i = 0; i < rCount; i++) {
                            JSONObject song = retrySongs.getJSONObject(i);
                            String songTitle = song.getString("name");
                            JSONArray artists = song.getJSONArray("artists");
                            StringBuilder authorBuilder = new StringBuilder();
                            for (int j = 0; j < artists.size(); j++) {
                                if (authorBuilder.length() > 0) authorBuilder.append(" / ");
                                authorBuilder.append(artists.getJSONObject(j).getString("name"));
                            }
                            String songAuthor = authorBuilder.toString();
                            int sim = SongMatchingUtil.calculateSimilarity(localTitle, localAuthor, songTitle, songAuthor);
                            int swapped = SongMatchingUtil.calculateSimilarity(localAuthor, localTitle, songTitle, songAuthor);
                            sim = Math.max(sim, swapped);

                            JSONObject albumObj = song.getJSONObject("album");
                            String albumName = albumObj != null ? albumObj.getString("name") : "";

                            retryCandidates.add(SongCandidate.<JSONObject>builder()
                                    .id("retry_" + i)
                                    .title(songTitle)
                                    .artist(songAuthor)
                                    .album(albumName)
                                    .traditionalScore(sim)
                                    .rawObject(song)
                                    .build());

                            if (sim > highestSimilarity) {
                                highestSimilarity = sim;
                                bestMatchSong = song;
                            }
                        }

                        if (jevSongReranker != null && !retryCandidates.isEmpty()) {
                            SongCandidate<JSONObject> rerankedRetry = jevSongReranker.selectBestCandidate(localTitle, localAuthor, retryCandidates);
                            if (rerankedRetry != null) {
                                bestMatchSong = rerankedRetry.getRawObject();
                                highestSimilarity = rerankedRetry.getTraditionalScore();
                                rerankerAccepted = true;
                                semanticMatchConfirmed = rerankedRetry.isSemanticMatchConfirmed();
                            } else {
                                bestMatchSong = null;
                                highestSimilarity = 0;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("网易云音乐双向重试失败: {}", ex.getMessage());
            }
        }

        // Jev 已接受的语义匹配独立于传统分；仅传统匹配继续使用 40% 门槛。
        if (bestMatchSong == null || (!rerankerAccepted && highestSimilarity < 40)) {
            throw new RuntimeException("网易云音乐未找到匹配歌曲 (最高相似度: " + highestSimilarity + "%)");
        }

        // 从最佳匹配的歌曲中提取最终信息
        String title = bestMatchSong.getString("name");

        JSONArray artists = bestMatchSong.getJSONArray("artists");
        StringBuilder authorBuilder = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            if (authorBuilder.length() > 0) {
                authorBuilder.append(" / ");
            }
            authorBuilder.append(artists.getJSONObject(i).getString("name"));
        }
        String author = authorBuilder.toString();

        String id = bestMatchSong.getString("id");
        Integer duration = bestMatchSong.getInteger("duration") / 1000;  // 毫秒转为秒

        JSONObject albumObject = bestMatchSong.getJSONObject("album");

        String album = albumObject.getString("name");
        long picId = albumObject.getLong("picId");
        String cover = CoverHelper.buildCoverUrl(picId, 500);

        // 计算出格式化的时长
        String durationHuman = TimeUtil.getFormattedDuration(duration);

        // 封装歌曲对象
        Track track = Track.builder()
                .author(author)
                .title(title)
                .album(album)
                .cover(cover)
                .duration(duration)
                .durationHuman(durationHuman)
                .url("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
                .id(id)
                .isVideo(false)
                .isAdvertisement(false)
                .inLibrary(false)
                .semanticMatchConfirmed(semanticMatchConfirmed)
                .build();

        log.info("获取成功");

        // 更新缓存 (加锁写入)
        synchronized (cacheLock) {
            this.prevKeyword = keyword;
            this.prevTrack = track;
        }

        return track;
    }

    /**
     * 获取歌曲封面 URL
     * （由于需要多一次网络请求，已弃用，推荐使用 CoverHelper 直接根据 picUrl 进行加密获取封面 URL）
     * @param id 歌曲 id
     * @return
     */
    public String getCoverUrl(String id) throws Exception {
        // 封装请求参数对象
        Map<String, String> data = new HashMap<>();
        data.put("id", id);
        data.put("c", "[{\"id\":" + id + "}]");
        data.put("ids", "[" + id + "]");
        data.put("csrf_token", "");

        // 发送获取歌曲详情请求
        String respStr = EapiHelper.post("https://interface3.music.163.com/eapi/song/detail", data);

        // 解析 JSON 字符串为 JSONObject
        JSONObject jsonObject = JSON.parseObject(respStr);

        // 检查响应数据的 code
        if (!jsonObject.containsKey("code") || jsonObject.getIntValue("code") != 200) {
            throw new RuntimeException("网易云音乐歌曲封面获取失败：响应码错误（" + respStr + "）");
        }

        // 提取所需字段
        String cover = jsonObject.getJSONArray("songs").getJSONObject(0).getJSONObject("album").getString("picUrl");
        cover += "?param=500y500";  // 图片大小设置为 500*500

        return cover;
    }

    /**
     * 从网易云音乐获取歌词
     * @param keyword 关键词
     * @return
     * @throws Exception
     */
    public Lyric getLyric(String keyword) throws Exception {
        String[] parseResult = SongUtil.parseCleanTitle(keyword);
        String realTitle = parseResult[0];
        String realAuthor = parseResult[1];

        String title = "";
        String author = "";
        Integer duration = 0;
        boolean semanticMatchConfirmed = false;

        // 1. 获取歌曲在网易云音乐的 ID 和基本信息
        String id = null;

        // 当前平台刚好就是网易云音乐，免去一次搜索歌曲的网络请求
        if ("netease".equals(audioService.getCurrentPlatform())) {
            int maxRetries = 5;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                // 发送 GET 请求
                URL url = new URL("http://localhost:9863/api/query/track");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // 读取响应
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // 解析 JSON
                JSONObject jsonObject = JSON.parseObject(response.toString());
                title = jsonObject.getString("title");
                author = jsonObject.getString("author");
                duration = jsonObject.getInteger("duration");

                int similarity = SongMatchingUtil.calculateSimilarity(realTitle, realAuthor, title, author);
                if (similarity >= SongMatchingUtil.EXACT_MATCH_THRESHOLD) {
                    // 歌曲信息匹配，则提取歌曲 ID
                    id = jsonObject.getString("id");
                    break;

                } else if (attempt < maxRetries) {
                    // 歌曲信息不匹配（通常是由于 query 接口未及时更新数据），等待一段时间后重试
                    Thread.sleep(50);
                }
            }
        }

        if (id == null || "".equals(id)) {
            Track track = search(keyword);
            id = track.getId();
            title = track.getTitle();
            author = track.getAuthor();
            duration = track.getDuration();
            semanticMatchConfirmed = track.isSemanticMatchConfirmed();
        }

        log.info("从网易云音乐获取歌词..");

        Lyric lyric = new Lyric();
        lyric.setSource("netease");
        lyric.setTitle(title);
        lyric.setAuthor(author);
        lyric.setDuration(duration);

        // 计算相似度，判断歌曲信息与真实信息是否匹配
        int similarity = SongMatchingUtil.calculateSimilarity(realTitle, realAuthor, title, author);

        int matchThreshold = SongMatchingUtil.EXACT_MATCH_THRESHOLD;
        // 对于歌手名缺失的情况，可适当降低阈值标准
        if (realAuthor == null || realAuthor.isBlank()) {
            matchThreshold = 75;
        }

        // 如果歌曲错误，则说明网易云音乐没有该歌曲，也就没有必要再调用 API 获取歌词了
        if (!semanticMatchConfirmed && similarity < matchThreshold) {
            // 设置真实歌曲标题，而非错误歌曲标题
            lyric.setTitle(realTitle);
            lyric.setAuthor(realAuthor);

            // 宁可返回空歌词，也不要返回不匹配的歌词
            log.warn("网易云歌词获取失败（未找到匹配歌曲）");
            return lyric;
        }

        // 2. 获取歌词
        // 构建请求参数
        Map<String, String> data = new HashMap<>();
        data.put("id", id);
        data.put("cp", "false");
        data.put("lv", "0");
        data.put("kv", "0");
        data.put("tv", "0");
        data.put("rv", "0");
        data.put("yv", "0");
        data.put("ytv", "0");
        data.put("yrv", "0");
        data.put("csrf_token", "");
        String respStr = sendLyricRequest(data);

        // 解析 JSON 字符串为 JSONObject
        JSONObject jsonObject = JSON.parseObject(respStr);

        // 检查响应数据的 code
        if (!jsonObject.containsKey("code") || jsonObject.getIntValue("code") != 200) {
            throw new RuntimeException("获取歌词失败：id = " + id);
        }

        if (!jsonObject.containsKey("lrc")) {
            log.info("网易云歌词获取成功（匹配度：{}%，该歌曲无歌词）", similarity);
            return lyric;
        }

        // 提取原始歌词
        String lrc = jsonObject.getJSONObject("lrc").getString("lyric");
        if (lrc == null || "".equals(lrc) || !lrc.contains("00") || lrc.contains("纯音乐，请欣赏")) {
            log.info("网易云歌词获取成功（匹配度：{}%，该歌曲无歌词）", similarity);
            return lyric;
        }
        lyric.setHasLyric(true);
        lyric.setLrc(lrc);

        // 提取翻译歌词
        if (jsonObject.containsKey("tlyric")) {
            String translatedLyric = jsonObject.getJSONObject("tlyric").getString("lyric");
            if (!StringUtils.isBlank(translatedLyric)) {
                lyric.setHasTranslatedLyric(true);
                lyric.setTranslatedLyric(translatedLyric);
            }
        }

        // 提取逐字歌词
        if (jsonObject.containsKey("yrc")) {
            String yrcContent = jsonObject.getJSONObject("yrc").getString("lyric");
            if (!StringUtils.isBlank(yrcContent)) {
                // 将 YRC 格式的逐字歌词解析为 List<LyricLine> 内部对象
                List<LyricLine> lyricLines = YrcParser.parse(yrcContent);

                // 根据 List<LyricLine> 内部对象生成 LYS 格式的逐字歌词
                String lys = LysGenerator.generate(lyricLines, "yrc");

                lyric.setHasKaraokeLyric(true);
                lyric.setKaraokeLyric(yrcContent);
            }
        }

        log.info("网易云歌词获取成功（匹配度：{}%）", similarity);

        return lyric;
    }

}

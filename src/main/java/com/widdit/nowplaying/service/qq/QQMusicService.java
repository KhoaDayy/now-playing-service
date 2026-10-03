package com.widdit.nowplaying.service.qq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.util.SongMatchingUtil;
import com.widdit.nowplaying.util.SongUtil;
import com.widdit.nowplaying.util.TimeUtil;
import com.widdit.nowplaying.util.lyric.generator.LrcGenerator;
import com.widdit.nowplaying.util.lyric.generator.LysGenerator;
import com.widdit.nowplaying.util.lyric.model.LyricLine;
import com.widdit.nowplaying.util.lyric.parser.QrcParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;

@Service
@Slf4j
public class QQMusicService {

    @Autowired(required = false)
    private JevSongReranker jevSongReranker;

    // 缓存相关变量
    private String prevKeyword;
    private Track prevTrack;

    // 锁对象
    private final Object cacheLock = new Object();

    private static final Map<String, String> VERBATIM_XML_MAPPING_DICT = new HashMap<>();

    static {
        VERBATIM_XML_MAPPING_DICT.put("content", "orig");       // 原文
        VERBATIM_XML_MAPPING_DICT.put("contentts", "ts");       // 译文
        VERBATIM_XML_MAPPING_DICT.put("contentroma", "roma");   // 罗马音
        VERBATIM_XML_MAPPING_DICT.put("Lyric_1", "lyric");      // 解压后的内容
    }

    // 新接口风控相关变量：记录新接口上次失败的时间戳（-1 表示未失败过）
    private volatile long newApiFailTimestamp = -1;

    // 新接口失败后的冷却时间（10 分钟，单位毫秒）
    private static final long NEW_API_COOLDOWN_MS = 10 * 60 * 1000L;

    /**
     * 根据关键词搜索歌曲，返回歌曲信息对象
     * @param keyword 关键词
     * @return
     */
    public Track search(String keyword) throws Exception {
        log.info("获取 QQ 音乐歌曲信息..");

        // 尝试从缓存获取 (加锁读取，保证读取到的是完整的一组数据)
        synchronized (cacheLock) {
            if (Objects.equals(keyword, prevKeyword) && prevTrack != null) {
                log.info("命中歌曲缓存：" + keyword);
                return prevTrack;
            }
        }

        // 判断新接口是否处于冷却期
        boolean newApiInCooldown = newApiFailTimestamp != -1
                && (System.currentTimeMillis() - newApiFailTimestamp) < NEW_API_COOLDOWN_MS;

        if (newApiInCooldown) {
            // 新接口处于冷却期，直接调用旧接口
            return searchAlternative(keyword);
        }

        // 缓存未命中，执行网络请求逻辑
        // 构建请求体
        String searchKeyword = SongUtil.getBestSearchKeyword(keyword);
        if (searchKeyword == null || searchKeyword.isBlank()) {
            searchKeyword = keyword;
        }

        JSONObject param = new JSONObject();
        param.put("search_type", 0);
        param.put("query", searchKeyword);
        param.put("page_num", 1);
        param.put("num_per_page", 8);

        JSONObject req1 = new JSONObject();
        req1.put("method", "DoSearchForQQMusicDesktop");
        req1.put("module", "music.search.SearchCgiService");
        req1.put("param", param);

        JSONObject requestBody = new JSONObject();
        requestBody.put("req_1", req1);

        // 发送搜索歌曲请求
        String respStr = sendPostRequest("https://u.y.qq.com/cgi-bin/musicu.fcg", requestBody.toJSONString());

        // 解析 JSON 字符串为 JSONObject
        JSONObject jsonObject = JSON.parseObject(respStr);

        // 检查响应数据的 req_1 的 code
        JSONObject req1Resp = jsonObject.getJSONObject("req_1");
        if (req1Resp == null || req1Resp.getIntValue("code") != 0) {
            log.warn("QQ 音乐新搜索接口响应错误，将在未来 10 分钟内使用旧接口兜底。响应内容：" + respStr);
            newApiFailTimestamp = System.currentTimeMillis();
            return searchAlternative(keyword);
        }

        // 提取所需字段
        JSONArray songs = jsonObject.getJSONObject("req_1").getJSONObject("data").getJSONObject("body").getJSONObject("song").getJSONArray("list");

        // 检查数组是否为空
        if (songs == null || songs.isEmpty()) {
            throw new RuntimeException("QQ 音乐歌曲信息获取失败，搜索结果为空");
        }

        // 最多遍历前 8 个元素
        int maxCount = Math.min(songs.size(), 8);

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
            String songTitle = song.getString("title");

            // 提取歌手名
            JSONArray artists = song.getJSONArray("singer");
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

            JSONObject albumObj = song.getJSONObject("album");
            String albumName = albumObj != null ? albumObj.getString("name") : "";
            int durationSec = song.getIntValue("interval");

            candidateList.add(SongCandidate.<JSONObject>builder()
                    .id("cand_" + index)
                    .title(songTitle)
                    .artist(songAuthor)
                    .album(albumName)
                    .durationSeconds(durationSec)
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

        // 双向重试机制：若首轮搜索未达到及格线 (40%) 且存在歌手信息，尝试反转关键词搜索
        if (!rerankerAccepted && highestSimilarity < 40 && !localAuthor.isBlank()) {
            log.info("QQ 音乐首轮未匹配到合格歌曲 (最高相似度: {}%)，尝试反转关键词重试...", highestSimilarity);
            try {
                String retryQuery = localAuthor + " " + localTitle;
                JSONObject retryParam = new JSONObject();
                retryParam.put("search_type", 0);
                retryParam.put("query", retryQuery);
                retryParam.put("page_num", 1);
                retryParam.put("num_per_page", 8);

                JSONObject retryReq1 = new JSONObject();
                retryReq1.put("method", "DoSearchForQQMusicDesktop");
                retryReq1.put("module", "music.search.SearchCgiService");
                retryReq1.put("param", retryParam);

                JSONObject retryReqData = new JSONObject();
                retryReqData.put("req_1", retryReq1);

                String retryResp = sendPostRequest("https://u.y.qq.com/cgi-bin/musicu.fcg", retryReqData.toJSONString());
                JSONObject retryJson = JSON.parseObject(retryResp);
                if (retryJson != null && retryJson.containsKey("req_1")) {
                    JSONObject req1Obj = retryJson.getJSONObject("req_1");
                    if (req1Obj != null && req1Obj.getIntValue("code") == 0) {
                        JSONArray rSongs = req1Obj.getJSONObject("data").getJSONObject("body").getJSONObject("song").getJSONArray("list");
                        if (rSongs != null && !rSongs.isEmpty()) {
                            int rCount = Math.min(rSongs.size(), 8);
                            List<SongCandidate<JSONObject>> retryCandidates = new ArrayList<>();
                            for (int i = 0; i < rCount; i++) {
                                JSONObject song = rSongs.getJSONObject(i);
                                String songTitle = song.getString("title");
                                JSONArray artists = song.getJSONArray("singer");
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
                                int durSec = song.getIntValue("interval");

                                retryCandidates.add(SongCandidate.<JSONObject>builder()
                                        .id("retry_" + i)
                                        .title(songTitle)
                                        .artist(songAuthor)
                                        .album(albumName)
                                        .durationSeconds(durSec)
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
                }
            } catch (Exception ex) {
                log.warn("QQ 音乐双向重试失败: {}", ex.getMessage());
            }
        }

        // Jev 已接受的语义匹配独立于传统分；仅传统匹配继续使用 40% 门槛。
        if (bestMatchSong == null || (!rerankerAccepted && highestSimilarity < 40)) {
            throw new RuntimeException("QQ 音乐未找到匹配歌曲 (最高相似度: " + highestSimilarity + "%)");
        }

        // 从最佳匹配的歌曲中提取最终信息
        String title = bestMatchSong.getString("title");

        JSONArray artists = bestMatchSong.getJSONArray("singer");
        StringBuilder authorBuilder = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            if (authorBuilder.length() > 0) {
                authorBuilder.append(" / ");
            }
            authorBuilder.append(artists.getJSONObject(i).getString("name"));
        }
        String author = authorBuilder.toString();

        String id = bestMatchSong.getString("id");
        String album = bestMatchSong.getJSONObject("album").getString("name");
        String albumMid = bestMatchSong.getJSONObject("album").getString("mid");
        Integer duration = bestMatchSong.getInteger("interval");

        // 计算出格式化的时长
        String durationHuman = TimeUtil.getFormattedDuration(duration);

        // 封装歌曲对象
        Track track = Track.builder()
                .author(author)
                .title(title)
                .album(album)
                .cover("https://y.qq.com/music/photo_new/T002R500x500M000" + albumMid + "_1.jpg")
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
     * 根据关键词搜索歌曲，返回歌曲信息对象（使用旧搜索接口作为兜底，速度比新接口慢 200ms）
     * @param keyword 关键词
     * @return
     */
    public Track searchAlternative(String keyword) throws Exception {
        // 构建请求参数
        Map<String, String> params = new HashMap<>();
        params.put("ct", "24");
        params.put("qqmusic_ver", "1298");
        params.put("remoteplace", "txt.yqq.center");
        params.put("t", "0");
        params.put("aggr", "1");
        params.put("cr", "1");
        params.put("catZhida", "1");
        params.put("lossless", "0");
        params.put("flag_qc", "0");
        params.put("p", "1");  // 关键参数：页码
        params.put("n", "8");  // 关键参数：每页数量
        String searchKeyword = SongUtil.getBestSearchKeyword(keyword);
        if (searchKeyword == null || searchKeyword.isBlank()) {
            searchKeyword = keyword;
        }

        params.put("w", searchKeyword);  // 关键参数：搜索关键词
        params.put("g_tk", "5381");
        params.put("loginUin", "0");
        params.put("hostUin", "0");
        params.put("format", "json");
        params.put("inCharset", "utf8");
        params.put("outCharset", "utf-8");
        params.put("notice", "0");
        params.put("platform", "yqq");
        params.put("needNewCode", "0");

        // 发送搜索歌曲请求
        String respStr = sendGetRequest("https://c.y.qq.com/soso/fcgi-bin/client_search_cp", params);

        // 解析 JSON 字符串为 JSONObject
        JSONObject jsonObject = JSON.parseObject(respStr);

        // 检查响应数据的 code
        if (!jsonObject.containsKey("code") || jsonObject.getIntValue("code") != 0) {
            throw new RuntimeException("QQ 音乐歌曲信息获取失败，响应码错误（" + respStr + "）");
        }

        // 提取所需字段
        JSONArray songs = jsonObject.getJSONObject("data").getJSONObject("song").getJSONArray("list");

        // 检查数组是否为空
        if (songs == null || songs.isEmpty()) {
            throw new RuntimeException("QQ 音乐歌曲信息获取失败，搜索结果为空");
        }

        // 最多遍历前 8 个元素
        int maxCount = Math.min(songs.size(), 8);

        // 解析出本地歌曲信息，用于后续计算歌曲信息匹配度
        String[] parseResult = SongUtil.parseCleanTitle(keyword);
        String localTitle = parseResult[0];
        String localAuthor = parseResult[1];

        // 用于记录最佳匹配的歌曲
        JSONObject bestMatchSong = null;
        int highestSimilarity = -1;

        // 遍历歌曲数组
        for (int index = 0; index < maxCount; index++) {
            JSONObject song = songs.getJSONObject(index);

            // 提取歌曲标题
            String songTitle = song.getString("songname");

            // 提取歌手名
            JSONArray artists = song.getJSONArray("singer");
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

            // 如果完美匹配，直接选中并退出循环
            if (similarity >= 100) {
                bestMatchSong = song;
                highestSimilarity = similarity;
                break;
            }

            // 记录相似度最高的歌曲
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatchSong = song;
            }
        }

        // 严格 chốt chặn: 相似度低于 40% 绝不采纳，防止把完全无关的歌曲当成匹配歌曲
        if (highestSimilarity < 40 || bestMatchSong == null) {
            throw new RuntimeException("QQ 音乐未找到匹配歌曲 (最高相似度: " + highestSimilarity + "%)");
        }

        // 从最佳匹配的歌曲中提取最终信息
        String title = bestMatchSong.getString("songname");

        JSONArray artists = bestMatchSong.getJSONArray("singer");
        StringBuilder authorBuilder = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            if (authorBuilder.length() > 0) {
                authorBuilder.append(" / ");
            }
            authorBuilder.append(artists.getJSONObject(i).getString("name"));
        }
        String author = authorBuilder.toString();

        String id = bestMatchSong.getString("songid");
        String album = bestMatchSong.getString("albumname");
        String albumMid = bestMatchSong.getString("albummid");
        Integer duration = bestMatchSong.getInteger("interval");

        // 计算出格式化的时长
        String durationHuman = TimeUtil.getFormattedDuration(duration);

        // 封装歌曲对象
        Track track = Track.builder()
                .author(author)
                .title(title)
                .album(album)
                .cover("https://y.qq.com/music/photo_new/T002R500x500M000" + albumMid + "_1.jpg")
                .duration(duration)
                .durationHuman(durationHuman)
                .url("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
                .id(id)
                .isVideo(false)
                .isAdvertisement(false)
                .inLibrary(false)
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
     * 从 QQ 音乐获取歌词
     * @param keyword 关键词
     * @return
     * @throws Exception
     */
    public Lyric getLyric(String keyword) throws Exception {
        String[] parseResult = SongUtil.parseCleanTitle(keyword);
        String realTitle = parseResult[0];
        String realAuthor = parseResult[1];

        // 1. 获取歌曲在 QQ 音乐的 ID 和基本信息
        Track track = search(keyword);
        String id = track.getId();
        String title = track.getTitle();
        String author = track.getAuthor();
        Integer duration = track.getDuration();

        log.info("从 QQ 音乐获取歌词..");

        Lyric lyric = new Lyric();
        lyric.setSource("qq");
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

        // 如果歌曲错误，则说明 QQ 音乐没有该歌曲，也就没有必要再调用 API 获取歌词了
        if (!track.isSemanticMatchConfirmed() && similarity < matchThreshold) {
            // 设置真实歌曲标题，而非错误歌曲标题
            lyric.setTitle(realTitle);
            lyric.setAuthor(realAuthor);

            // 宁可返回空歌词，也不要返回不匹配的歌词
            log.warn("QQ 歌词获取失败（未找到匹配歌曲）");
            return lyric;
        }

        // 2. 获取逐字歌词与翻译歌词，并根据 QRC 生成 LRC
        QrcLyric qrcLyric = getQrcLyric(id);

        String qrcContent = qrcLyric.getQrc();
        if (!StringUtils.isBlank(qrcContent)) {
            // 将 QRC 格式的逐字歌词解析为 List<LyricLine> 内部对象
            List<LyricLine> lyricLines = QrcParser.parse(qrcContent);

            // 根据 List<LyricLine> 内部对象生成 LYS 格式的逐字歌词
            String lys = LysGenerator.generate(lyricLines, "qrc");

            lyric.setHasKaraokeLyric(true);
            lyric.setKaraokeLyric(qrcContent);

            // 根据 List<LyricLine> 内部对象生成 LRC 歌词
            String lrc = LrcGenerator.generate(lyricLines);

            lyric.setHasLyric(true);
            lyric.setLrc(lrc);
        }

        if (!StringUtils.isBlank(qrcLyric.getTrans())) {
            lyric.setHasTranslatedLyric(true);
            lyric.setTranslatedLyric(qrcLyric.getTrans());
        }

        // 3. 只有当没有得到 LRC 时，才获取原始歌词（通常发生于该歌曲本身无逐字歌词）
        if (!lyric.getHasLyric()) {
            // 构建请求参数
            Map<String, String> params = new HashMap<>();
            params.put("musicid", id);
            params.put("callback", "MusicJsonCallback_lrc");
            params.put("pcachetime", String.valueOf(System.currentTimeMillis()));
            params.put("g_tk", "5381");
            params.put("jsonpCallback", "MusicJsonCallback_lrc");
            params.put("loginUin", "0");
            params.put("hostUin", "0");
            params.put("format", "json");
            params.put("inCharset", "utf8");
            params.put("outCharset", "utf8");
            params.put("notice", "0");
            params.put("platform", "yqq");
            params.put("needNewCode", "0");
            params.put("nobase64", "1");

            // 发送请求
            String respStr = sendGetRequest("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg", params);

            // 解析 JSON 字符串为 JSONObject
            JSONObject jsonObject = JSON.parseObject(respStr);

            // 检查响应数据的 code
            if (!jsonObject.containsKey("code")) {
                throw new RuntimeException("获取原始歌词失败（id = " + id + "）：响应结果不包含 code 字段");
            }
            int retCode = jsonObject.getIntValue("code");
            if (retCode != 0 && retCode != -1901) {
                throw new RuntimeException("获取原始歌词失败（id = " + id + "）：响应结果的 code 为 " + retCode);
            }

            // -1901 为一个特殊的 code，它并不代表 API 请求错误，而是 QQ 音乐没有歌词，因此需要特殊处理，不抛出异常
            if (jsonObject.containsKey("lyric") && retCode != -1901) {
                // 提取原始歌词
                String lrc = jsonObject.getString("lyric");
                if (lrc != null && !lrc.isBlank() && lrc.contains("00") && !lrc.contains("此歌曲为没有填词的纯音乐")) {
                    lyric.setHasLyric(true);
                    lyric.setLrc(lrc);
                }
            }

            // 提取翻译歌词（兼容处理）
            // 说明：
            // 1. 自 2026 年 3 月起，QQ 音乐该接口不再返回翻译歌词（可能是为了减少网络开销）
            // 2. 多数歌曲已通过 QRC 歌词提供完整信息（含逐字 + 翻译）
            // 3. 为兼容仍返回翻译歌词的情况，此处保留兜底判断
            if (!lyric.getHasTranslatedLyric() && jsonObject.containsKey("trans")) {
                String translatedLyric = jsonObject.getString("trans");
                if (!StringUtils.isBlank(translatedLyric)) {
                    lyric.setHasTranslatedLyric(true);
                    lyric.setTranslatedLyric(translatedLyric);
                }
            }
        }

        if (lyric.getHasKaraokeLyric() || lyric.getHasLyric()) {
            log.info("QQ 歌词获取成功（匹配度：{}%）", similarity);
        } else {
            log.info("QQ 歌词获取成功（匹配度：{}%，该歌曲无歌词）", similarity);
        }

        return lyric;
    }

    /**
     * 获取 QRC 逐字歌词（含翻译歌词）
     *
     * 原作者：WXRIW
     * 代码链接：https://github.com/WXRIW/Lyricify-Lyrics-Helper/blob/master/Lyricify.Lyrics.Helper/Providers/Web/QQMusic/Api.cs
     * Licensed under the Apache License, Version 2.0
     *
     * 修改者：Widdit
     *
     * @param songid 歌曲 ID
     * @return 包含解密后的 QRC 逐字歌词和翻译歌词的 QrcLyric 对象
     */
    public QrcLyric getQrcLyric(String songid) {
        try {
            // 构建请求参数
            Map<String, String> params = new HashMap<>();
            params.put("musicid", songid);
            params.put("version", "15");
            params.put("miniversion", "82");
            params.put("lrctype", "4");

            // 发送请求
            String resp = sendGetRequest("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg", params);

            // 处理响应
            resp = resp.replace("<!--", "").replace("-->", "");

            Map<String, Node> dict = new HashMap<>();
            Document doc = Decrypter.createXmlDocument(resp);
            Decrypter.recursionFindElement(doc.getDocumentElement(), VERBATIM_XML_MAPPING_DICT, dict);

            QrcLyric qrcLyric = new QrcLyric();

            // 提取逐字歌词节点 "orig"
            if (dict.containsKey("orig")) {
                String text = Decrypter.getNodeText(dict.get("orig"));

                if (text != null && !text.isBlank()) {
                    try {
                        String decompressText = Decrypter.decryptLyrics(text);
                        if (decompressText != null && !decompressText.isBlank()) {
                            String qrcContent = extractLyricContent(decompressText);
                            if (qrcContent != null && !qrcContent.isBlank()) {
                                qrcLyric.setQrc(qrcContent);
                            }
                        }
                    } catch (Exception e) {
                        log.error("解密 QRC 歌词失败（id = {}）：{}", songid, e.getMessage());
                    }
                }
            }

            // 提取翻译歌词节点 "ts"
            if (dict.containsKey("ts")) {
                String text = Decrypter.getNodeText(dict.get("ts"));

                if (text != null && !text.isBlank()) {
                    // 去除影响美观的 "//" 标记
                    text = text.replace("//", "");
                    qrcLyric.setTrans(text);
                }
            }

            return qrcLyric;

        } catch (Exception e) {
            log.error("获取 QRC 逐字歌词失败（id = {}）：{}", songid, e.getMessage());
            return new QrcLyric();
        }
    }

    /**
     * 从解密后的文本中提取实际的 QRC 歌词内容
     * 如果解密后的文本是 XML 格式，则通过正则表达式直接提取 LyricContent 属性的原始值
     * （避免通过 DOM 解析导致属性值中的换行符被规范化为空格），并对 XML 转义字符进行反转义；
     * 否则直接返回原文本
     *
     * @param decompressText 解密后的文本
     * @return 实际的 QRC 歌词字符串
     */
    private String extractLyricContent(String decompressText) {
        if (decompressText.contains("<?xml")) {
            try {
                Pattern pattern = Pattern.compile("LyricContent=\"([\\s\\S]*?)\"\\s*/>");
                Matcher matcher = pattern.matcher(decompressText);
                if (matcher.find()) {
                    return unescapeXml(matcher.group(1));
                }
                return decompressText;
            } catch (Exception e) {
                return decompressText;
            }
        } else {
            return decompressText;
        }
    }

    /**
     * 反转义 XML 中的转义字符
     *
     * @param text 待反转义的文本
     * @return 反转义后的文本
     */
    private String unescapeXml(String text) {
        if (text == null) return null;
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /**
     * 发送 GET 请求
     * @param url 请求 URL
     * @param params 查询参数
     * @return 响应 JSON 字符串
     */
    private String sendGetRequest(String url, Map<String, String> params) throws Exception {
        URL parsedUrl = new URL(url);
        String host = parsedUrl.getHost();
        String referer = parsedUrl.getProtocol() + "://" + host + "/";

        Connection.Response response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36")
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Host", host)
                .header("Referer", referer)
                .method(Connection.Method.GET)
                .data(params)
                .ignoreContentType(true)
                .timeout(10000)
                .execute();

        return response.body();
    }

    /**
     * 发送 POST 请求
     * @param url 请求 URL
     * @param body 请求体 JSON 字符串
     * @return 响应 JSON 字符串
     */
    String sendPostRequest(String url, String body) throws Exception {
        URL parsedUrl = new URL(url);
        String host = parsedUrl.getHost();
        String referer = parsedUrl.getProtocol() + "://" + host + "/";

        Connection.Response response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36")
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Host", host)
                .header("Referer", referer)
                .header("Content-Type", "application/json")
                .method(Connection.Method.POST)
                .requestBody(body)
                .ignoreContentType(true)
                .timeout(10000)
                .execute();

        return response.body();
    }

}

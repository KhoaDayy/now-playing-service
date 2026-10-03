package com.widdit.nowplaying.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.SettingsLyric;
import com.widdit.nowplaying.entity.SettingsLyricCommon;
import com.widdit.nowplaying.event.LyricChangedEvent;
import com.widdit.nowplaying.event.TrackChangedEvent;
import com.widdit.nowplaying.service.netease.NeteaseMusicService;
import com.widdit.nowplaying.service.qq.QQMusicService;
import com.widdit.nowplaying.service.wesing.WeSingService;
import com.widdit.nowplaying.util.SongMatchingUtil;
import com.widdit.nowplaying.service.lrclib.LrclibService;
import com.widdit.nowplaying.util.SongUtil;
import com.widdit.nowplaying.util.lyric.LyricOffsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LyricService {

    @Autowired
    private AudioService audioService;
    @Autowired
    private NeteaseMusicService neteaseMusicService;
    @Autowired
    private QQMusicService qqMusicService;
    @Autowired
    private WeSingService weSingService;
    @Autowired
    private LrclibService lrclibService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // 当前歌曲歌词
    private volatile Lyric lyric = new Lyric();

    // 当前歌词对象所对应的标题（用于判断歌词是否过期）
    private volatile String currentLyricWindowTitle = null;

    // 是否启用歌词自动获取（WebSocket 连接数 > 0 时启用）
    private volatile boolean fetchLyricEnabled = false;

    // 歌词获取操作的锁，用于处理并发情况
    private final ReentrantLock fetchLock = new ReentrantLock();

    // 设置文件目录
    private static final String SETTINGS_DIR = "Settings";
    // 设置文件路径
    private static final String SETTINGS_FILE_PATH = "Settings\\settings-lyric.json";
    // 通用歌词设置文件路径
    private static final String SETTINGS_COMMON_FILE_PATH = "Settings\\settings-lyric-common.json";
    // 所有配置文件的 ID
    private static final String[] CONFIG_IDS = {
            "main", "profileA", "profileB", "profileC", "profileD",
            "player", "playerMobile", "playerWidget"
    };
    // 歌词页面的配置文件 ID
    private static final List<String> LYRIC_PAGE_CONFIG_IDS = List.of("main", "profileA", "profileB", "profileC", "profileD");
    // 播放器页面的配置文件 ID
    private static final List<String> PLAYER_PAGE_CONFIG_IDS = List.of("player", "playerMobile", "playerWidget");

    // 存储所有配置的 Map，key 为配置文件 ID，value 为对应的 SettingsLyric 对象
    private Map<String, SettingsLyric> settingsMap;
    // 通用歌词设置
    private SettingsLyricCommon settingsCommon;

    /**
     * Spring 容器初始化完成后执行，加载配置文件
     */
    @PostConstruct
    public void init() {
        loadSettingsFromFile();
        loadCommonSettingsFromFile();
    }

    /**
     * 获取歌词对象
     * 如果歌词未准备好（当前歌词与播放歌曲不匹配），会触发网络请求获取歌词
     * @return 歌词对象
     */
    public Lyric getLyric() {
        String windowTitle = audioService.getWindowTitle();
        String status = audioService.getStatus();

        // 如果没有播放或窗口标题为空，返回当前歌词（可能是空歌词）
        if ("None".equals(status) || windowTitle == null || windowTitle.isEmpty()) {
            Lyric emptyLyric = new Lyric();
            emptyLyric.setSource(settingsCommon.getLyricSource());
            return emptyLyric;
        }

        // Read the lyric and its title under the same lock as cache writes.
        return fetchLyricIfNeeded(windowTitle);
    }

    /**
     * Returns the current lyric with an optional per-consumer timestamp correction.
     * The cached lyric remains unchanged so different API consumers can use different offsets.
     */
    public Lyric getLyric(Integer offsetMs) {
        return LyricOffsetUtil.shift(getLyric(), offsetMs);
    }

    /**
     * 如果需要则获取歌词（线程安全）
     * 处理并发情况，避免重复的网络请求
     * @param windowTitle 当前窗口标题
     * @return 歌词对象
     */
    private Lyric fetchLyricIfNeeded(String windowTitle) {
        fetchLock.lock();
        try {
            // A queued request may belong to a track that stopped while waiting for the lock.
            if (!isCurrentTrack(windowTitle)) {
                return createEmptyCurrentLyric();
            }

            // 双重检查：在等待锁期间，歌词可能已被其他线程（如 updateLyric）更新
            if (windowTitle.equals(currentLyricWindowTitle)) {
                return this.lyric;
            }

            log.info("歌词未准备好，触发更新歌词: {}", windowTitle);

            // 获取歌词
            Lyric newLyric = fetchLyric(windowTitle);

            // Providers can finish after playback moves to another track or closes.
            if (!isCurrentTrack(windowTitle)) {
                return createEmptyCurrentLyric();
            }

            // 更新缓存
            this.lyric = newLyric;
            this.currentLyricWindowTitle = windowTitle;

            return newLyric;

        } finally {
            fetchLock.unlock();
        }
    }

    /**
     * 获取歌词
     * 通过网络请求获取歌词
     * @param windowTitle 当前窗口标题
     * @return 歌词对象
     */
    private Lyric fetchLyric(String windowTitle) {
        // 获取通用歌词设置的关键字段
        String source = settingsCommon.getLyricSource();
        boolean autoSelectBestLyric = Boolean.TRUE.equals(settingsCommon.getAutoSelectBestLyric());

        // 获取播放状态
        String status = audioService.getStatus();

        Lyric newLyric = new Lyric();
        newLyric.setSource(source);

        if ("None".equals(status) || windowTitle == null || "".equals(windowTitle)) {
            return newLyric;
        }

        // 如果当前平台为全民 K 歌，则优先读取全民 K 歌本地缓存歌词
        if ("wesing".equals(audioService.getCurrentPlatform())) {
            Lyric localLyric = weSingService.getLocalLyric(windowTitle);
            if (hasUsableLyric(localLyric)) {
                log.info("使用全民 K 歌本地缓存歌词");
                return localLyric;
            }
            log.info("全民 K 歌本地无歌词，退化为在线获取");
        }

        boolean preferQq = "qq".equals(source) || windowTitle.contains("周杰伦") || windowTitle.contains("周杰倫");
        boolean isJayChou = windowTitle.contains("周杰伦") || windowTitle.contains("周杰倫");
        if (autoSelectBestLyric && !isJayChou) {
            // 智能匹配最佳歌词
            newLyric = selectBestLyric(source, windowTitle);
        } else {
            // Selected domestic source first, then the other domestic source.
            newLyric = fetchPrimaryLyric(preferQq ? "qq" : "netease", windowTitle);
            if (!hasUsableLyric(newLyric)) {
                newLyric = fetchPrimaryLyric(preferQq ? "netease" : "qq", windowTitle);
            }
        }

        // LRCLIB is always last: both domestic sources must fail or have no usable lyric.
        if (!hasUsableLyric(newLyric)) {
            log.info("国内音乐平台未获取到有效歌词，尝试使用 LRCLIB 兜底获取: {}", windowTitle);
            try {
                int totalSec = audioService.getTotalSeconds();
                Integer duration = totalSec > 0 ? totalSec : null;
                Lyric lrclibLyric = lrclibService.getLyric(windowTitle, duration);
                if (hasUsableLyric(lrclibLyric)) {
                    log.info("LRCLIB 成功兜底获取到歌词！");
                    return lrclibLyric;
                }
            } catch (Exception e) {
                log.warn("LRCLIB 兜底获取歌词异常: {}", e.getMessage());
            }
        }

        if (!hasUsableLyric(newLyric)) {
            return createEmptyLyric(windowTitle, source);
        }

        return newLyric;
    }

    private Lyric fetchPrimaryLyric(String source, String windowTitle) {
        try {
            return "qq".equals(source)
                    ? qqMusicService.getLyric(windowTitle) : neteaseMusicService.getLyric(windowTitle);
        } catch (Exception e) {
            log.warn("获取 {} 歌词失败：{}", source, e.getMessage());
            return null;
        }
    }

    private boolean hasUsableLyric(Lyric candidate) {
        return candidate != null && Boolean.TRUE.equals(candidate.getHasLyric())
                && ((candidate.getLrc() != null && !candidate.getLrc().isBlank())
                || (Boolean.TRUE.equals(candidate.getHasKaraokeLyric())
                && candidate.getKaraokeLyric() != null && !candidate.getKaraokeLyric().isBlank()));
    }

    /**
     * 监听歌曲发生改变的事件
     * @param event
     */
    @EventListener
    public void handleTrackChange(TrackChangedEvent event) {
        // 歌曲发生改变，在后台异步更新歌词，绝不阻塞音频状态监听线程
        CompletableFuture.runAsync(this::updateLyric);
    }

    /**
     * 更新是否启用歌词自动获取
     * @param fetchLyricEnabled
     */
    public void setFetchLyricEnabled(boolean fetchLyricEnabled) {
        this.fetchLyricEnabled = fetchLyricEnabled;

        // 当启用歌词获取时，如果当前歌词未准备好，则立即异步更新
        if (fetchLyricEnabled) {
            String windowTitle = audioService.getWindowTitle();
            if (windowTitle != null && !windowTitle.equals(currentLyricWindowTitle)) {
                CompletableFuture.runAsync(this::updateLyric);
            }
        }
    }

    /**
     * 获取指定 ID 的配置
     *
     * @param id 配置文件 ID
     * @return 对应 id 的 SettingsLyric 对象，如果 id 不存在则返回 null
     */
    public SettingsLyric getSettings(String id) {
        String normalizedId = normalizeId(id);
        return settingsMap.get(normalizedId);
    }

    /**
     * 获取所有配置
     *
     * @return 包含所有 SettingsLyric 对象的 Map，key 为配置文件 id
     */
    public Map<String, SettingsLyric> getAllSettings() {
        return new HashMap<>(settingsMap);
    }

    /**
     * 更新指定 ID 的配置
     * 更新内存中对应 id 的 SettingsLyric 对象，并且写入到本地文件
     *
     * @param id 配置文件
     * @param settingsLyric 新的配置对象
     */
    public void updateSettings(String id, SettingsLyric settingsLyric) {
        log.info("用户更新歌词配置：{}", id);

        String normalizedId = normalizeId(id);

        // 设置更新时间戳
        settingsLyric.setLastUpdateTime(System.currentTimeMillis());

        // 更新内存中的配置
        settingsMap.put(normalizedId, settingsLyric);

        // 写入到本地文件
        saveSettingsToFile();
    }

    /**
     * 获取通用歌词设置
     *
     * @return SettingsLyricCommon 对象
     */
    public SettingsLyricCommon getCommonSettings() {
        return settingsCommon;
    }

    /**
     * 更新通用歌词设置
     * 更新内存中的 SettingsLyricCommon 对象，并且写入到本地文件
     *
     * @param settingsLyricCommon 新的通用歌词设置对象
     */
    public void updateCommonSettings(SettingsLyricCommon settingsLyricCommon) {
        log.info("用户更新通用歌词设置");

        // 更新内存中的设置
        this.settingsCommon = settingsLyricCommon;

        // 清除当前歌词缓存标记，强制下次重新获取
        this.currentLyricWindowTitle = null;

        // 通用歌词设置发生改变，则更新歌词
        updateLyric();

        // 写入到本地文件
        saveCommonSettingsToFile();
    }

    /**
     * 智能匹配最佳歌词
     * @param source 默认歌词来源
     * @param windowTitle 窗口标题（真实歌曲标题）
     * @return 最佳歌词对象
     */
    private Lyric selectBestLyric(String source, String windowTitle) {
        log.info("并行获取两个音乐平台的歌词..");

        // 并行获取两个音乐平台的歌词
        CompletableFuture<Lyric> neteaseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return neteaseMusicService.getLyric(windowTitle);
            } catch (Exception e) {
                log.error("获取网易云音乐歌词失败：" + e.getMessage());
                return null;
            }
        });

        CompletableFuture<Lyric> qqFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return qqMusicService.getLyric(windowTitle);
            } catch (Exception e) {
                log.error("获取 QQ 音乐歌词失败：" + e.getMessage());
                return null;
            }
        });

        Lyric neteaseLyric = null;
        Lyric qqLyric = null;

        try {
            // 等待两个请求都完成
            CompletableFuture.allOf(neteaseFuture, qqFuture).join();
            neteaseLyric = neteaseFuture.get();
            qqLyric = qqFuture.get();
        } catch (Exception e) {
            log.error("并行获取歌词结果失败：" + e.getMessage());
        }

        // 检查两个平台是否有有效歌词
        boolean neteaseHas = hasUsableLyric(neteaseLyric);
        boolean qqHas = hasUsableLyric(qqLyric);

        // LRCLIB fallback is handled once by fetchLyric for every selection mode.
        if (!neteaseHas && !qqHas) {
            return createEmptyLyric(windowTitle, source);
        }

        // 如果只有一个平台有歌词，直接返回有歌词的那个
        if (!neteaseHas) {
            log.info("网易云音乐无歌词，使用 QQ 音乐歌词");
            return qqLyric;
        }
        if (!qqHas) {
            log.info("QQ 音乐无歌词，使用网易云音乐歌词");
            return neteaseLyric;
        }

        String[] parseResult = SongUtil.parseCleanTitle(windowTitle);
        String realTitle = parseResult[0];
        String realAuthor = parseResult[1];

        // 计算歌曲标题相似度
        int neteaseSimilarity = SongMatchingUtil.calculateSimilarity(realTitle, realAuthor, neteaseLyric.getTitle(), neteaseLyric.getAuthor());
        int qqSimilarity = SongMatchingUtil.calculateSimilarity(realTitle, realAuthor, qqLyric.getTitle(), qqLyric.getAuthor());

        // System.out.println("网易云音乐：" + neteaseLyric.getTitle() + " - " + neteaseLyric.getAuthor());
        // System.out.println("网易云音乐相似度：" + neteaseSimilarity);
        // System.out.println("QQ 音乐：" + qqLyric.getTitle() + " - " + qqLyric.getAuthor());
        // System.out.println("QQ 音乐相似度：" + qqSimilarity);

        int similarityDiff = Math.abs(neteaseSimilarity - qqSimilarity);
        int durationDiff = Math.abs(neteaseLyric.getDuration() - qqLyric.getDuration());

        // 相似度接近，说明歌曲信息都匹配，则根据得分选择最佳歌词
        if (similarityDiff <= 2 || (durationDiff <= 2 && similarityDiff <= 8)) {
            return selectByScore(neteaseLyric, qqLyric, source);
        }

        // 相似度不接近，则选择歌曲相似度更高的歌词
        if (neteaseSimilarity > qqSimilarity) {
            return neteaseLyric;
        } else {
            return qqLyric;
        }
    }

    /**
     * 根据得分选择最佳歌词（评判标准：原版歌词、翻译歌词、逐字歌词是否齐全，越齐全得分越高）
     * @param neteaseLyric 网易云音乐歌词对象
     * @param qqLyric QQ 音乐歌词对象
     * @param source 歌词来源
     * @return 最佳歌词对象
     */
    private Lyric selectByScore(Lyric neteaseLyric, Lyric qqLyric, String source) {
        int neteaseScore = 0;
        int qqScore = 0;

        if (neteaseLyric.getHasLyric()) neteaseScore++;
        if (qqLyric.getHasLyric()) qqScore++;

        if (neteaseLyric.getHasTranslatedLyric()) neteaseScore++;
        if (qqLyric.getHasTranslatedLyric()) qqScore++;

        if (neteaseLyric.getHasKaraokeLyric()) neteaseScore++;
        if (qqLyric.getHasKaraokeLyric()) qqScore++;

        // System.out.println("网易云音乐得分：" + neteaseScore);
        // System.out.println("QQ 音乐得分：" + qqScore);

        // 返回 score 更高的歌词对象
        if (neteaseScore > qqScore) {
            return neteaseLyric;
        } else if (qqScore > neteaseScore) {
            return qqLyric;
        } else {
            // 如果 score 相同，则返回用户所选平台的歌词对象
            if ("qq".equals(source)) {
                return qqLyric;
            } else {
                return neteaseLyric;
            }
        }
    }

    /**
     * 创建空 Lyric 对象
     * @param windowTitle 窗口标题
     * @param source 歌词来源
     * @return 空 Lyric 对象
     */
    private Lyric createEmptyLyric(String windowTitle, String source) {
        Lyric lyric = new Lyric();
        lyric.setSource(source);

        String[] parseResult = SongUtil.parseCleanTitle(windowTitle);
        String title = parseResult[0];
        String author = parseResult[1];

        if (!title.isBlank()) {
            lyric.setTitle(title);
            lyric.setAuthor(author);
        } else {
            String pivot = " - ";
            if (windowTitle.contains(pivot)) {
                int pos = windowTitle.lastIndexOf(pivot);
                lyric.setTitle(windowTitle.substring(0, pos).trim());
                lyric.setAuthor(windowTitle.substring(pos + pivot.length()).trim());
            } else {
                lyric.setTitle(windowTitle);
                lyric.setAuthor(" ");
            }
        }

        return lyric;
    }

    private boolean isCurrentTrack(String windowTitle) {
        return windowTitle != null && !windowTitle.isEmpty()
                && !"None".equals(audioService.getStatus())
                && windowTitle.equals(audioService.getWindowTitle());
    }

    private Lyric createEmptyCurrentLyric() {
        String windowTitle = audioService.getWindowTitle();
        String source = settingsCommon.getLyricSource();
        if (!isCurrentTrack(windowTitle)) {
            Lyric emptyLyric = new Lyric();
            emptyLyric.setSource(source);
            return emptyLyric;
        }
        return createEmptyLyric(windowTitle, source);
    }

    /**
     * 强制刷新歌词
     * 清除缓存标记，重新获取歌词并发布事件
     */
    public void forceRefreshLyric() {
        if (!fetchLyricEnabled) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            fetchLock.lock();
            try {
                String windowTitle = audioService.getWindowTitle();
                if (!isCurrentTrack(windowTitle)) {
                    return;
                }

                log.info("强制刷新歌词: {}", windowTitle);

                // 清除缓存标记，强制重新获取
                this.currentLyricWindowTitle = null;
                Lyric newLyric = fetchLyric(windowTitle);
                if (!isCurrentTrack(windowTitle)) {
                    return;
                }
                this.lyric = newLyric;
                this.currentLyricWindowTitle = windowTitle;

                eventPublisher.publishEvent(new LyricChangedEvent(this, "歌词发生改变"));
            } finally {
                fetchLock.unlock();
            }
        });
    }

    /**
     * 更新歌词对象
     */
    private void updateLyric() {
        if (!fetchLyricEnabled) {
            return;
        }

        fetchLock.lock();
        try {
            String windowTitle = audioService.getWindowTitle();

            if (!isCurrentTrack(windowTitle)) {
                return;
            }

            // 窗口标题没变则跳过网络请求，但仍需发布事件。
            // 否则会导致：切歌时 getLyric() (走 /api/lyric) 可能先于 handleTrackChange 拿到
            // fetchLock 并把 currentLyricWindowTitle 更新成新值；待 updateLyric 再拿到锁时
            // 匹配成功直接 return，前端就收不到 Lyric WebSocket 消息。
            if (!windowTitle.equals(currentLyricWindowTitle)) {
                log.info("更新歌词：{}", windowTitle);

                Lyric newLyric = fetchLyric(windowTitle);
                if (!isCurrentTrack(windowTitle)) {
                    return;
                }
                this.lyric = newLyric;
                this.currentLyricWindowTitle = windowTitle;
            }

            // 发布事件，通知变化
            if (!isCurrentTrack(windowTitle)) {
                return;
            }
            eventPublisher.publishEvent(new LyricChangedEvent(this, "歌词发生改变"));

        } finally {
            fetchLock.unlock();
        }
    }

    /**
     * 从本地磁盘加载配置文件
     * 如果文件不存在，则创建默认配置
     */
    private void loadSettingsFromFile() {
        File settingsFile = new File(SETTINGS_FILE_PATH);

        if (settingsFile.exists()) {
            try {
                // 读取文件内容
                byte[] bytes = Files.readAllBytes(settingsFile.toPath());
                String jsonContent = new String(bytes, StandardCharsets.UTF_8);

                // 解析 JSON
                settingsMap = JSON.parseObject(jsonContent, new TypeReference<Map<String, SettingsLyric>>() {});

                // 如果解析结果为 null，创建默认配置
                if (settingsMap == null) {
                    createDefaultSettings();
                }
            } catch (Exception e) {
                log.error("读取歌词配置文件失败：{}", e.getMessage());
                createDefaultSettings();
            }
        } else {
            // 文件不存在，创建默认配置
            createDefaultSettings();
        }
    }

    /**
     * 从本地磁盘加载通用歌词设置文件
     * 如果文件不存在，则创建默认配置
     */
    private void loadCommonSettingsFromFile() {
        File settingsFile = new File(SETTINGS_COMMON_FILE_PATH);

        if (settingsFile.exists()) {
            try {
                // 读取文件内容
                byte[] bytes = Files.readAllBytes(settingsFile.toPath());
                String jsonContent = new String(bytes, StandardCharsets.UTF_8);

                // 解析 JSON
                settingsCommon = JSON.parseObject(jsonContent, SettingsLyricCommon.class);

                // 如果解析结果为 null，创建默认配置
                if (settingsCommon == null) {
                    createDefaultCommonSettings();
                }
            } catch (Exception e) {
                log.error("读取通用歌词设置文件失败：{}", e.getMessage());
                createDefaultCommonSettings();
            }
        } else {
            // 文件不存在，创建默认配置
            createDefaultCommonSettings();
        }
    }

    /**
     * 创建默认配置
     * 如果 Settings 目录不存在，先创建目录
     * 然后创建 5 个默认的 SettingsLyric 对象并保存到文件
     */
    private void createDefaultSettings() {
        // 检查并创建 Settings 目录
        File dir = new File(SETTINGS_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                System.err.println("无法创建目录: " + SETTINGS_DIR);
            }
        }

        // 初始化 settingsMap
        settingsMap = new HashMap<>();

        // 为每个配置 ID 创建默认的 SettingsLyric 对象
        for (String id : CONFIG_IDS) {
            SettingsLyric settingsLyric = new SettingsLyric();

            settingsLyric.setLastUpdateTime(System.currentTimeMillis());

            if (LYRIC_PAGE_CONFIG_IDS.contains(id)) {
                settingsLyric.setBackgroundEnabled(false);
            } else if (PLAYER_PAGE_CONFIG_IDS.contains(id)) {
                settingsLyric.setBackgroundEnabled(true);
            }

            settingsMap.put(id, settingsLyric);
        }

        // 保存到文件
        saveSettingsToFile();
    }

    /**
     * 创建默认通用歌词设置
     * 如果 Settings 目录不存在，先创建目录
     * 然后创建默认的 SettingsLyricCommon 对象并保存到文件
     */
    private void createDefaultCommonSettings() {
        // 检查并创建 Settings 目录
        File dir = new File(SETTINGS_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                System.err.println("无法创建目录: " + SETTINGS_DIR);
            }
        }

        // 创建默认的 SettingsLyricCommon 对象
        settingsCommon = new SettingsLyricCommon();

        // 保存到文件
        saveCommonSettingsToFile();
    }

    /**
     * 将当前内存中的配置保存到本地文件
     */
    private synchronized void saveSettingsToFile() {
        try {
            File settingsFile = new File(SETTINGS_FILE_PATH);

            // 确保父目录存在
            File parentDir = settingsFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 序列化为 JSON 字符串
            String jsonContent = JSON.toJSONString(settingsMap,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteDateUseDateFormat);

            // 写入文件
            Files.write(settingsFile.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("保存歌词配置文件失败：{}", e.getMessage());
        }
    }

    /**
     * 将当前内存中的通用歌词设置保存到本地文件
     */
    private synchronized void saveCommonSettingsToFile() {
        try {
            File settingsFile = new File(SETTINGS_COMMON_FILE_PATH);

            // 确保父目录存在
            File parentDir = settingsFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 序列化为 JSON 字符串
            String jsonContent = JSON.toJSONString(settingsCommon,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteDateUseDateFormat);

            // 写入文件
            Files.write(settingsFile.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("保存通用歌词设置文件失败：{}", e.getMessage());
        }
    }

    /**
     * 规范化配置文件 ID
     * 如果 id 为 null、空字符串或 "Main"（不区分大小写），则返回 "main"
     *
     * @param id 原始 ID
     * @return 规范化后的 ID
     */
    private String normalizeId(String id) {
        if (id == null || "".equals(id) || "Main".equalsIgnoreCase(id)) {
            return "main";
        }
        return id;
    }

    /**
     * 判断歌词文本是否包含有效的时间戳格式 [mm:ss]
     */
    private boolean containsTimestamps(String lrc) {
        if (lrc == null || lrc.isBlank()) return false;
        return Pattern.compile("\\[\\d{1,2}:\\d{2}").matcher(lrc).find();
    }

}

package com.widdit.nowplaying.service;

import com.widdit.nowplaying.component.Timer;
import com.widdit.nowplaying.entity.*;
import com.widdit.nowplaying.event.*;
import com.widdit.nowplaying.service.kugou.KuGouMusicService;
import com.widdit.nowplaying.service.kuwo.KuWoMusicService;
import com.widdit.nowplaying.service.netease.NeteaseMusicNewService;
import com.widdit.nowplaying.service.netease.NeteaseMusicService;
import com.widdit.nowplaying.service.qq.QQMusicService;
import com.widdit.nowplaying.service.lrclib.LrclibService;
import com.widdit.nowplaying.util.SongUtil;
import com.widdit.nowplaying.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class NowPlayingService {

    // 播放器信息
    private Player player = new Player();
    // 歌曲信息
    private Track track = new Track();

    // 计时器
    private Timer timer = new Timer();

    // 前一个窗口标题
    private String prevWindowTitle = "";
    // 前 1 秒的播放状态
    private String prevStatus = "None";
    // 前一个是否暂停状态
    private boolean prevIsPaused = true;
    // 状态转为 None 时的时间戳（毫秒）
    private long noneOccursTime = 0;

    // C# 端上一次的播放进度（秒），用于检测重唱和触发同步（-1 表示自然失去进度（C# 端不再报告进度），-2 表示由切歌/重置清零）
    private static final int CSHARP_PROGRESS_NONE = -1;
    private static final int CSHARP_PROGRESS_RESET = -2;
    private int prevCSharpProgressSeconds = CSHARP_PROGRESS_RESET;
    private long lastProgressSyncTime = 0L;

    private final Map<String, String> otherPlatforms = new HashMap<>();

    @Autowired
    private AudioService audioService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private NeteaseMusicService neteaseMusicService;
    @Autowired
    private QQMusicService qqMusicService;
    @Autowired
    private KuGouMusicService kuGouMusicService;
    @Autowired
    private KuWoMusicService kuWoMusicService;
    @Autowired
    private NeteaseMusicNewService neteaseMusicNewService;
    @Autowired
    private OutputService outputService;
    @Autowired
    private LrclibService lrclibService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    @Lazy
    private LyricService lyricService;

    /**
     * 监听音乐状态被更新的事件
     * @param event
     */
    @EventListener
    public void updateMusicInfo(MusicStatusUpdatedEvent event) {
        SettingsGeneral settings = settingsService.getSettingsGeneral();

        // 获取音乐状态
        String status = audioService.getStatus();
        String windowTitle = audioService.getWindowTitle();

        // 为防止误判，要求连续 3 秒以上都为 None 才认为是真正关闭了音乐软件
        if ("None".equals(status)) {
            if ("None".equals(prevStatus)) {
                if (System.currentTimeMillis() - noneOccursTime > 3 * 1000) {
                    // 此时认为音乐软件真正被关闭了
                    boolean hadSong = player.getHasSong();
                    player = new Player();
                    track = new Track();
                    timer.reset();
                    prevWindowTitle = "";
                    prevIsPaused = true;
                    prevCSharpProgressSeconds = CSHARP_PROGRESS_RESET;

                    // 如果之前有歌曲，发布事件通知前端清除歌词和播放状态
                    if (hadSong) {
                        eventPublisher.publishEvent(new TrackChangedEvent(this, "音乐软件已关闭"));
                        eventPublisher.publishEvent(new PlayerPauseStateChangedEvent(this, "播放器暂停状态改变"));
                    }
                } else {
                    // 这种情况下乐观地认为音乐软件依然存在，更新进度条
                    advanceSeekbar();
                }
            } else {
                // 状态转为 None
                noneOccursTime = System.currentTimeMillis();
                // 这种情况下乐观地认为音乐软件依然存在，更新进度条
                advanceSeekbar();
            }
            prevStatus = status;
            return;
        }

        player.setHasSong(true);

        boolean isPaused = !"Playing".equals(status);
        player.setIsPaused(isPaused);

        if (prevIsPaused != isPaused) {
            // 发布事件，通知变化
            eventPublisher.publishEvent(new PlayerPauseStateChangedEvent(this, "播放器暂停状态改变"));
        }

        if (windowTitle.equals(prevWindowTitle)) {  // 窗口标题不变（没切歌），无需查询歌曲信息
            // 处理 C# 端精确进度更新（检测重唱、同名歌曲恢复、进度同步）
            handleCSharpProgressUpdate();

            // 如果状态是暂停，则让进度条暂停；否则，让进度条前进
            if (isPaused) {
                pauseSeekbar();
            } else {
                advanceSeekbar();
            }
        } else {  // 窗口标题改变（切歌了），需要查询歌曲信息
            prevCSharpProgressSeconds = CSHARP_PROGRESS_RESET;

            log.info("切换歌曲为：" + windowTitle);

            String platform = audioService.getCurrentPlatform();
            boolean isKaraokeOrNoise = SongUtil.isKaraokeOrNoise(windowTitle);
            boolean isWebOrYoutube = "youtube".equals(platform) || "browser".equals(platform);

            // 1. 立即使用清洗后的歌名和歌手名初始化 Track，绝不阻塞音频状态监听线程
            String[] cleanParts = (isKaraokeOrNoise || isWebOrYoutube)
                    ? SongUtil.parseCleanTitle(windowTitle)
                    : SongUtil.parseWindowTitle(windowTitle);

            String initialTitle = (cleanParts[0] != null && !cleanParts[0].isBlank()) ? cleanParts[0] : windowTitle;
            String initialAuthor = (cleanParts[1] != null && !cleanParts[1].isBlank()) ? cleanParts[1] : "";

            int initialTotal = audioService.getTotalSeconds() > 0 ? audioService.getTotalSeconds() : 5 * 60;
            track = Track.builder()
                    .title(initialTitle)
                    .author(initialAuthor)
                    .album("")
                    .cover("https://gitee.com/widdit/now-playing/raw/master/spotify_no_cover.jpg")
                    .duration(initialTotal)
                    .durationHuman(TimeUtil.getFormattedDuration(initialTotal))
                    .url("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
                    .build();

            // 2. 立即初始化计时器，若已有 C# 进度则直接同步，无需盲目重置为 0
            timer.reset();
            long initialMs = audioService.getProgressMs();
            if (initialMs > 0) {
                long targetMs = initialMs;
                if (isWebOrYoutube && !isPaused) {
                    targetMs += getProgressOffsetMs();
                }
                if (initialTotal > 0) {
                    targetMs = Math.min(targetMs, initialTotal * 1000L);
                }
                timer.setTime(Math.max(0, targetMs));
            }
            if (!isPaused) {
                timer.start();
            }

            // 3. 立即发布切歌事件通知前端更新标题，实现 0 延迟响应
            eventPublisher.publishEvent(new TrackChangedEvent(this, "歌曲发生改变"));
            outputService.outputAsync(track);

            // 4. 在后台异步线程进行网络歌词与封面匹配，避免阻塞音频状态读取线程
            final String currentSongTitle = windowTitle;
            CompletableFuture.runAsync(() -> {
                enrichTrackMetadataAsync(currentSongTitle, platform, settings);
            });
        }

        prevWindowTitle = windowTitle;
        prevStatus = status;
        prevIsPaused = isPaused;
    }

    /**
     * 获取歌曲当前播放进度的毫秒值（统一的进度读取入口）
     * @return
     */
    public long getCurrentProgressMs() {
        return timer.getTime();
    }

    /**
     * 返回歌曲信息
     * @return
     */
    public Query query() {
        return new Query(queryPlayer(), track);
    }

    /**
     * 返回播放器信息
     * @return
     */
    public Player queryPlayer() {
        // 从计时器中实时获取进度条时间
        long progressMs = getCurrentProgressMs();
        int progressSec = (int) (progressMs / 1000);
        int duration = track.getDuration();

        // 如果 C# 端提供了歌曲总时长，则使用 C# 端的值
        if (audioService.getTotalSeconds() > 0) {
            duration = audioService.getTotalSeconds();
        }

        // 防止未知异常情况，不要除以 0 就行
        if (duration <= 0) {
            duration = 5 * 60;
        }

        player.setSeekbarCurrentPosition(progressSec);
        player.setSeekbarCurrentPositionHuman(TimeUtil.getFormattedDuration(progressSec));
        player.setStatePercent((double) progressSec / duration);

        return player;
    }

    /**
     * 返回歌曲信息
     * @return
     */
    public Track queryTrack() {
        return track;
    }

    /**
     * 返回进度条毫秒值
     * @return
     */
    public QueryProgress queryProgress() {
        return new QueryProgress(getCurrentProgressMs());
    }

    /**
     * 获取当前是否有歌曲
     * @return
     */
    public RespData<Boolean> hasSong() {
        return new RespData<>(player.getHasSong());
    }

    /**
     * 获取是否成功连接平台
     * @return
     */
    public RespData<Boolean> isConnected() {
        return new RespData<>(player.getHasSong());
    }

    /**
     * 监听通用设置被修改的事件
     * @param event
     */
    @EventListener
    public void handleSettingsGeneralChange(SettingsGeneralChangedEvent event) {
        // 清空歌曲状态
        player = new Player();
        track = new Track();
        prevWindowTitle = "";
        prevCSharpProgressSeconds = CSHARP_PROGRESS_RESET;
    }

    /**
     * 初始化操作。该方法会在该类实例被 Spring 创建时自动执行
     */
    @PostConstruct
    public void init() {
        otherPlatforms.put("spotify", "Spotify");
        otherPlatforms.put("ayna", "卡西米尔唱片机");
        otherPlatforms.put("apple", "Apple Music");
        otherPlatforms.put("potplayer", "PotPlayer");
        otherPlatforms.put("foobar", "Foobar2000");
        otherPlatforms.put("lx", "洛雪音乐");
        otherPlatforms.put("soda", "汽水音乐");
        otherPlatforms.put("huahua", "花花直播助手");
        otherPlatforms.put("musicfree", "MusicFree");
        otherPlatforms.put("bq", "BQ点歌姬");
        otherPlatforms.put("aimp", "AIMP");
        otherPlatforms.put("youtube", "YouTube Music");
        otherPlatforms.put("miebo", "咩播");
        otherPlatforms.put("yesplay", "YesPlayMusic");
        otherPlatforms.put("cider", "Cider");
        otherPlatforms.put("browser", "浏览器");
        otherPlatforms.put("salt", "Salt Player");
    }

    /**
     * 处理 C# 端精确进度的更新（检测重唱、同名歌曲恢复、进度同步）
     * 适用于所有支持 C# 端精确进度的平台，对于不提供精确进度的平台会自动跳过
     */
    private void handleCSharpProgressUpdate() {
        int csharpProgress = audioService.getProgressSeconds();
        String platform = audioService.getCurrentPlatform();

        // C# 端提供的总时长比在线搜索的结果更准确（搜索可能匹配到不同时长的版本），用它修正歌曲时长，
        // 否则进度超过搜索结果的时长时，会被 advanceSeekbar 每秒误判为单曲循环重唱
        int csharpTotal = audioService.getTotalSeconds();
        if (csharpTotal > 0 && track.getDuration() != csharpTotal) {
            track.setDuration(csharpTotal);
            track.setDurationHuman(TimeUtil.getFormattedDuration(csharpTotal));
        }

        // 检测重唱（进度大幅回跳），仅全民 K 歌平台生效
        if ("wesing".equals(platform)
                && csharpProgress >= 0 && prevCSharpProgressSeconds > 3 && csharpProgress < prevCSharpProgressSeconds - 2) {
            log.info("检测到重唱/切歌，进度从 {}s 回跳到 {}s", prevCSharpProgressSeconds, csharpProgress);
            timer.reset();
            timer.start();
            eventPublisher.publishEvent(new PlayerProgressReplayEvent(this, "播放器重新播放"));
            lyricService.forceRefreshLyric();
        }

        // 从自然失去进度恢复到有进度（如关闭演唱窗口后选同名歌曲），强制刷新歌词并清空封面
        // 仅 CSHARP_PROGRESS_NONE（自然失去进度）时触发，CSHARP_PROGRESS_RESET（切歌重置）不触发
        if (csharpProgress >= 0 && prevCSharpProgressSeconds == CSHARP_PROGRESS_NONE) {
            log.info("检测到同名歌曲恢复，强制刷新歌词并清空封面");
            timer.reset();
            timer.start();
            // 同名歌曲切换时窗口标题不变，无法重新搜索，旧封面可能是错的，清空
            track.setCover("");
            eventPublisher.publishEvent(new TrackChangedEvent(this, "同名歌曲封面重置"));
            eventPublisher.publishEvent(new PlayerProgressReplayEvent(this, "播放器重新播放"));
            lyricService.forceRefreshLyric();
        }

        // C# 进度变化或存在偏差时，用 C# 的实际进度校准计时器，并向前端推送进度同步
        if (csharpProgress >= 0) {
            long targetMs = audioService.getProgressMs();
            if (targetMs < 0) {
                targetMs = csharpProgress * 1000L;
            }

            // 针对浏览器 / YouTube 场景，在播放状态下补偿 SMTC 管道延迟（约 2 秒）
            boolean isWebOrYoutube = "youtube".equals(platform) || "browser".equals(platform);
            if (isWebOrYoutube && !player.getIsPaused()) {
                targetMs += getProgressOffsetMs();
            }

            if (csharpTotal > 0) {
                targetMs = Math.min(targetMs, csharpTotal * 1000L);
            }
            targetMs = Math.max(0, targetMs);

            long currentTimerMs = timer.getTime();
            long drift = Math.abs(currentTimerMs - targetMs);

            boolean isFirstProgress = (prevCSharpProgressSeconds == CSHARP_PROGRESS_RESET);
            boolean isSeekJump = (drift >= 1500);
            long now = System.currentTimeMillis();
            boolean isPeriodicDrift = (drift >= 800 && (now - lastProgressSyncTime >= 3000));

            // 切歌后首次获取到进度，或者用户 seek 发生较大跳跃 (drift >= 1500ms)，或者周期性偏差校准（>= 800ms 且间隔 >= 3s）
            if (isFirstProgress || isSeekJump || isPeriodicDrift) {
                timer.setTime(targetMs);
                lastProgressSyncTime = now;
                eventPublisher.publishEvent(new PlayerProgressSyncEvent(this, "播放器进度同步"));
            }
        }

        // 更新 prevCSharpProgressSeconds
        // 特殊处理：如果当前是切歌状态（CSHARP_PROGRESS_RESET）并且 C# 端无进度（-1），则保持切歌状态
        if (prevCSharpProgressSeconds == CSHARP_PROGRESS_RESET && csharpProgress == CSHARP_PROGRESS_NONE) {
            // 保持 CSHARP_PROGRESS_RESET 状态，不更新
        } else {
            prevCSharpProgressSeconds = csharpProgress;
        }
    }

    /**
     * 获取 SMTC / 浏览器进度延迟补偿毫秒数（默认 0ms）
     */
    private int getProgressOffsetMs() {
        SettingsGeneral settings = settingsService.getSettingsGeneral();
        if (settings != null && settings.getProgressOffsetMs() != null) {
            return settings.getProgressOffsetMs();
        }
        return 0;
    }

    /**
     * 后台异步检索歌曲详细元数据（封面、歌词 ID 等），避免阻塞音频状态监听线程
     */
    private void enrichTrackMetadataAsync(String windowTitle, String platform, SettingsGeneral settings) {
        Set<String> sources = new LinkedHashSet<>();
        if ("netease".equals(platform) && settings != null && Boolean.TRUE.equals(settings.getSmtc())) {
            sources.add("netease-smtc");
        } else if ("qq".equals(platform) || "wesing".equals(platform)) {
            sources.add("qq");
        } else if ("kugou".equals(platform) || "kuwo".equals(platform)) {
            sources.add(platform);
        }
        // 优先当前平台，然后尝试其余主来源；LRCLIB 始终是最后的备用来源。
        sources.add("netease");
        sources.add("qq");

        for (String source : sources) {
            Track searchedTrack = searchPrimaryTrackMetadata(source, windowTitle);
            if (searchedTrack != null) {
                applySearchedTrack(windowTitle, searchedTrack, platform);
                return;
            }
        }

        log.info("主来源未找到歌曲信息，尝试 LRCLIB 备用来源");
        try {
            Track lrclibTrack = lrclibService.searchTrack(windowTitle);
            if (lrclibTrack != null) {
                log.info("成功从 LRCLIB 获取原版歌曲元数据: {} - {}", lrclibTrack.getTitle(), lrclibTrack.getAuthor());
                applySearchedTrack(windowTitle, lrclibTrack, platform);
            }
        } catch (Exception e) {
            log.warn("LRCLIB 检索歌曲失败: {}", e.getMessage());
        }
    }

    private Track searchPrimaryTrackMetadata(String source, String windowTitle) {
        try {
            switch (source) {
                case "netease-smtc":
                    return neteaseMusicNewService.getTrackInfo(windowTitle);
                case "netease":
                    return neteaseMusicService.search(windowTitle);
                case "qq":
                    return qqMusicService.search(windowTitle);
                case "kugou":
                    return kuGouMusicService.search(windowTitle);
                case "kuwo":
                    return kuWoMusicService.search(windowTitle);
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("{} 检索歌曲信息失败: {}", source, e.getMessage());
            return null;
        }
    }

    /**
     * 应用异步检索到的歌曲信息
     */
    private synchronized void applySearchedTrack(String windowTitle, Track searchedTrack, String platform) {
        // 如果当前播放的歌曲已经改变，丢弃该过期的搜索结果
        if (!windowTitle.equals(audioService.getWindowTitle())) {
            return;
        }

        boolean hasMatchedTrack = searchedTrack != null && searchedTrack.getTitle() != null
                && !searchedTrack.getTitle().isBlank() && searchedTrack.getId() != null && !searchedTrack.getId().isBlank();

        boolean isKaraokeOrNoise = SongUtil.isKaraokeOrNoise(windowTitle);
        boolean isWebOrYoutube = "youtube".equals(platform) || "browser".equals(platform);

        Track updatedTrack = copyTrack(searchedTrack);

        if (hasMatchedTrack && (isKaraokeOrNoise || isWebOrYoutube)) {
            if (updatedTrack.getTitle().contains(" - ")) {
                String[] parts = SongUtil.parseCleanTitle(updatedTrack.getTitle());
                if (parts[0] != null && !parts[0].isBlank()) {
                    updatedTrack.setTitle(parts[0]);
                }
                if (parts[1] != null && !parts[1].isBlank() && (updatedTrack.getAuthor() == null || updatedTrack.getAuthor().isBlank())) {
                    updatedTrack.setAuthor(parts[1]);
                }
            } else {
                updatedTrack.setTitle(SongUtil.stripQuotes(updatedTrack.getTitle()));
            }
            log.info("保留原版歌曲元数据: {} - {}", updatedTrack.getTitle(), updatedTrack.getAuthor());
        } else {
            String[] parseResult = (isKaraokeOrNoise || isWebOrYoutube)
                    ? SongUtil.parseCleanTitle(windowTitle)
                    : SongUtil.parseWindowTitle(windowTitle);

            if (parseResult[0] != null && !parseResult[0].isBlank()) {
                updatedTrack.setTitle(parseResult[0]);
            }
            if (parseResult[1] != null && !parseResult[1].isBlank()) {
                updatedTrack.setAuthor(parseResult[1]);
            }
        }

        // 保留当前已生效的真实 C# 总时长
        if (audioService.getTotalSeconds() > 0) {
            updatedTrack.setDuration(audioService.getTotalSeconds());
            updatedTrack.setDurationHuman(TimeUtil.getFormattedDuration(audioService.getTotalSeconds()));
        }

        this.track = updatedTrack;
        eventPublisher.publishEvent(new TrackChangedEvent(this, "歌曲元数据更新"));
        outputService.outputAsync(this.track);
    }

    /**
     * 进度条前进
     */
    private void advanceSeekbar() {
        if (!timer.isRunning()) {
            timer.start();
        }

        long progressMs = timer.getTime();
        long durationMs = track.getDuration() * 1000L;

        // 一般发生在单曲循环情况下
        if (progressMs >= durationMs - 300 && durationMs > 0) {
            timer.reset();
            timer.start();

            // 发布事件，通知变化
            eventPublisher.publishEvent(new PlayerProgressReplayEvent(this, "播放器重新播放"));
        }
    }

    /**
     * 进度条暂停
     */
    private void pauseSeekbar() {
        if (timer.isRunning()) {
            timer.pause();
        }
    }

    /**
     * 复制 Track 对象（浅拷贝）
     * @param source
     * @return
     */
    private Track copyTrack(Track source) {
        if (source == null) {
            return null;
        }
        Track target = new Track();
        BeanUtils.copyProperties(source, target);
        return target;
    }

}

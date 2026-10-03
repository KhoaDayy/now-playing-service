using System;
using System.Linq;
using Windows.Media.Control;
using WindowsMediaController;
using CSCore.CoreAudioAPI;

/*
    YouTube / YouTube Music 的 SMTC 实现。
    支持 YouTube Music 桌面客户端（youtube-music-desktop-app）以及各种浏览器（Thorium, Chrome, Edge 等）。
    提供实时切歌识别与基于 Windows SMTC 时间线的毫秒级高精度进度输出。
*/
public class YouTubeMusicSMTC : MusicService
{
    private static readonly string[] YouTubeKeywords =
    {
        "youtube", "thorium", "chrome", "msedge", "firefox", "brave", "opera", "vivaldi", "arc",
        "electron.app", "coccoc", "coc_coc", "floorp", "zen", "waterfox", "librewolf", "supermium", "chromium"
    };

    private MediaManager mediaManager;
    private string prevTitle = "";
    private string prevArtist = "";

    public override void Init()
    {
        mediaManager = new MediaManager();
        mediaManager.Start();
    }

    public override string GetMusicStatus(AudioSessionManager2 sessionManager)
    {
        try
        {
            // 优先选取正在播放的 YouTube / 浏览器会话
            var mediaSession = FindYouTubeSession(sessionManager, out bool isPlaying);
            if (mediaSession == null)
            {
                return "None";
            }

            // 获取歌曲信息
            var songInfo = mediaSession.ControlSession.TryGetMediaPropertiesAsync().GetAwaiter().GetResult();
            string title = songInfo?.Title;
            string artist = songInfo?.Artist ?? "";

            if (string.IsNullOrEmpty(title))
            {
                return "None";
            }

            artist = artist.Replace("、", " / ").Replace("和", " / ");

            // 检测到切歌时，在后台重新拉取并校验封面，确认内容已更新后再保存
            if (title != prevTitle || artist != prevArtist)
            {
                var controlSession = mediaSession.ControlSession;
                ThumbnailHelper.UpdateThumbnailAsync(() =>
                {
                    try
                    {
                        var latestSongInfo = controlSession.TryGetMediaPropertiesAsync().GetAwaiter().GetResult();
                        return latestSongInfo?.Thumbnail;
                    }
                    catch (Exception)
                    {
                        return null;
                    }
                });
            }

            prevTitle = title;
            prevArtist = artist;

            string status = isPlaying ? "Playing" : "Paused";
            string result;
            if (string.IsNullOrEmpty(artist))
            {
                result = $"{status}\r\n{title}";
            }
            else
            {
                result = $"{status}\r\n{title} - {artist}";
            }

            // 通过 SMTC 时间线输出精确进度
            try
            {
                var timeline = mediaSession.ControlSession.GetTimelineProperties();
                double totalSec = timeline.EndTime.TotalSeconds;

                if (totalSec > 0)
                {
                    double currentSec = timeline.Position.TotalSeconds;

                    // Position 是 LastUpdatedTime 时刻的快照，播放中需要补偿之后流逝的时间
                    if (isPlaying)
                    {
                        currentSec += (DateTimeOffset.UtcNow - timeline.LastUpdatedTime).TotalSeconds;
                    }

                    currentSec = Math.Max(0, Math.Min(currentSec, totalSec));
                    long currentMs = (long)Math.Round(currentSec * 1000.0);
                    long totalMs = (long)Math.Round(totalSec * 1000.0);
                    result += $"\r\nProgress:{(int)currentSec}|{(int)totalSec}|{currentMs}|{totalMs}";
                }
            }
            catch (Exception)
            {
                // 部分页面不提供时间线信息，忽略进度输出
            }

            return result;
        }
        catch (Exception)
        {
            return "None";
        }
    }

    /*
        在所有 SMTC 会话中寻找 YouTube / 浏览器会话，优先返回正在播放的那个。
    */
    private MediaManager.MediaSession FindYouTubeSession(AudioSessionManager2 sessionManager, out bool isPlaying)
    {
        isPlaying = false;
        MediaManager.MediaSession youtubePlaying = null;
        MediaManager.MediaSession browserPlaying = null;
        MediaManager.MediaSession fallback = null;

        foreach (var pair in mediaManager.CurrentMediaSessions)
        {
            string sessionId = pair.Key.ToLowerInvariant();
            if (!YouTubeKeywords.Any(sessionId.Contains))
            {
                continue;
            }

            bool sessionPlaying = false;
            try
            {
                var playbackStatus = pair.Value.ControlSession.GetPlaybackInfo().PlaybackStatus;
                if (playbackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                {
                    sessionPlaying = true;
                }
            }
            catch (Exception)
            {
                // ignore
            }

            // 若 SMTC 状态未更新为 Playing，尝试通过音频音量判断是否正在发声
            if (!sessionPlaying && sessionManager != null)
            {
                sessionPlaying = GetVolume(sessionManager) > 0.00001;
            }

            if (sessionPlaying)
            {
                if (sessionId.Contains("youtube"))
                {
                    youtubePlaying = pair.Value;
                    break;
                }
                else if (browserPlaying == null)
                {
                    browserPlaying = pair.Value;
                }
            }

            fallback ??= pair.Value;
        }

        if (youtubePlaying != null)
        {
            isPlaying = true;
            return youtubePlaying;
        }
        if (browserPlaying != null)
        {
            isPlaying = true;
            return browserPlaying;
        }
        if (fallback != null)
        {
            try
            {
                var pbStatus = fallback.ControlSession.GetPlaybackInfo().PlaybackStatus;
                if (pbStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                {
                    isPlaying = true;
                }
                else if (sessionManager != null && GetVolume(sessionManager) > 0.00001)
                {
                    isPlaying = true;
                }
            }
            catch (Exception) { }
            return fallback;
        }
        return null;
    }

    private double GetVolume(AudioSessionManager2 sessionManager)
    {
        double volume = 0;
        try
        {
            AudioSessionEnumerator sessionEnumerator = sessionManager.GetSessionEnumerator();
            foreach (AudioSessionControl session in sessionEnumerator)
            {
                if (session == null) continue;
                AudioSessionControl2 sessionControl = session.QueryInterface<AudioSessionControl2>();
                if (sessionControl == null || sessionControl.Process == null) continue;

                string processName = sessionControl.Process.ProcessName.ToLowerInvariant();
                if (YouTubeKeywords.Any(processName.Contains))
                {
                    using (AudioMeterInformation meter = session.QueryInterface<AudioMeterInformation>())
                    {
                        if (meter != null && meter.PeakValue > volume)
                        {
                            volume = meter.PeakValue;
                        }
                    }
                }

                sessionControl?.Dispose();
                session.Dispose();
            }
            sessionEnumerator?.Dispose();
        }
        catch (Exception)
        {
        }

        return volume;
    }
}
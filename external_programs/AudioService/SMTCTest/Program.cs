using System;
using System.Linq;
using Windows.Media.Control;
using WindowsMediaController;

class Program
{
    private static readonly string[] BrowserKeywords = new string[]
    {
        "chrome", "msedge", "firefox", "brave", "opera", "vivaldi", "arc", "qqbrowser", "sogou", "360se",
        "360chrome", "electron.app", "thorium", "coccoc", "coc_coc", "floorp", "zen", "waterfox", "librewolf", "supermium",
        "chromium", "youtube"
    };

    static void Main()
    {
        Console.OutputEncoding = System.Text.Encoding.UTF8;
        var mediaManager = new MediaManager();
        mediaManager.Start();
        System.Threading.Thread.Sleep(2000);

        Console.WriteLine($"Total sessions: {mediaManager.CurrentMediaSessions.Count}");
        foreach (var pair in mediaManager.CurrentMediaSessions)
        {
            string sessionId = pair.Key.ToLowerInvariant();
            bool matches = BrowserKeywords.Any(sessionId.Contains);
            var info = pair.Value.ControlSession.GetPlaybackInfo();
            var status = info.PlaybackStatus;
            var props = pair.Value.ControlSession.TryGetMediaPropertiesAsync().GetAwaiter().GetResult();
            Console.WriteLine($"Key: '{pair.Key}', Matches: {matches}, Status: {status}, Title: '{props?.Title}', Artist: '{props?.Artist}'");
        }
    }
}

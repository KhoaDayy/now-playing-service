package com.widdit.nowplaying.service;

import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.SettingsLyricCommon;
import com.widdit.nowplaying.event.LyricChangedEvent;
import com.widdit.nowplaying.service.lrclib.LrclibService;
import com.widdit.nowplaying.service.netease.NeteaseMusicService;
import com.widdit.nowplaying.service.qq.QQMusicService;
import com.widdit.nowplaying.service.wesing.WeSingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LyricServiceTest {

    private static final String FIRST_TRACK = "First Song - Singer";
    private static final String SECOND_TRACK = "Second Song - Singer";

    private LyricService service;
    private AudioService audio;
    private NeteaseMusicService netease;
    private QQMusicService qq;
    private LrclibService lrclib;
    private ApplicationEventPublisher publisher;
    private SettingsLyricCommon settings;
    private AtomicReference<String> title;
    private AtomicReference<String> status;

    @BeforeEach
    void setUp() {
        service = new LyricService();
        audio = mock(AudioService.class);
        netease = mock(NeteaseMusicService.class);
        qq = mock(QQMusicService.class);
        lrclib = mock(LrclibService.class);
        publisher = mock(ApplicationEventPublisher.class);
        settings = new SettingsLyricCommon();
        title = new AtomicReference<>(FIRST_TRACK);
        status = new AtomicReference<>("Playing");

        ReflectionTestUtils.setField(service, "audioService", audio);
        ReflectionTestUtils.setField(service, "neteaseMusicService", netease);
        ReflectionTestUtils.setField(service, "qqMusicService", qq);
        ReflectionTestUtils.setField(service, "weSingService", mock(WeSingService.class));
        ReflectionTestUtils.setField(service, "lrclibService", lrclib);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);
        ReflectionTestUtils.setField(service, "settingsCommon", settings);

        // No Spring context or init(): these tests never read/write Settings or call providers.
        when(audio.getWindowTitle()).thenAnswer(invocation -> title.get());
        when(audio.getStatus()).thenAnswer(invocation -> status.get());
        when(audio.getCurrentPlatform()).thenReturn("netease");
        when(audio.getTotalSeconds()).thenReturn(180);
    }

    @Test
    void bothDomesticFailuresTryMissingLrclibOnlyOnce() throws Exception {
        when(netease.getLyric(FIRST_TRACK)).thenThrow(new IOException("offline"));
        when(qq.getLyric(FIRST_TRACK)).thenThrow(new IOException("offline"));
        when(lrclib.getLyric(FIRST_TRACK, 180)).thenReturn(null);

        Lyric result = service.getLyric();

        assertFalse(result.getHasLyric());
        verify(lrclib, times(1)).getLyric(FIRST_TRACK, 180);
        assertSame(result, service.getLyric());
        verify(netease, times(1)).getLyric(FIRST_TRACK);
        verify(qq, times(1)).getLyric(FIRST_TRACK);
        verify(lrclib, times(1)).getLyric(FIRST_TRACK, 180);
    }

    @Test
    void domesticMissesUseSuccessfulLrclibFallback() throws Exception {
        Lyric fallback = lyric("lrclib", "First Song");
        when(netease.getLyric(FIRST_TRACK)).thenReturn(new Lyric());
        when(qq.getLyric(FIRST_TRACK)).thenReturn(null);
        when(lrclib.getLyric(FIRST_TRACK, 180)).thenReturn(fallback);

        assertSame(fallback, service.getLyric());
        verify(lrclib, times(1)).getLyric(FIRST_TRACK, 180);
    }

    @Test
    void domesticSuccessSkipsLrclibFallback() throws Exception {
        Lyric domestic = lyric("netease", "First Song");
        when(netease.getLyric(FIRST_TRACK)).thenReturn(domestic);
        when(qq.getLyric(FIRST_TRACK)).thenReturn(new Lyric());

        assertSame(domestic, service.getLyric());
        verify(netease).getLyric(FIRST_TRACK);
        verify(qq).getLyric(FIRST_TRACK);
        verifyNoInteractions(lrclib);
    }

    @Test
    void manualNeteaseSuccessSkipsOtherProviders() throws Exception {
        settings.setAutoSelectBestLyric(false);
        settings.setLyricSource("netease");
        Lyric preferred = lyric("netease", "First Song");
        when(netease.getLyric(FIRST_TRACK)).thenReturn(preferred);

        assertSame(preferred, service.getLyric());
        verify(netease).getLyric(FIRST_TRACK);
        verifyNoInteractions(qq, lrclib);
    }

    @Test
    void manualQqSuccessSkipsOtherProviders() throws Exception {
        settings.setAutoSelectBestLyric(false);
        settings.setLyricSource("qq");
        Lyric preferred = lyric("qq", "First Song");
        when(qq.getLyric(FIRST_TRACK)).thenReturn(preferred);

        assertSame(preferred, service.getLyric());
        verify(qq).getLyric(FIRST_TRACK);
        verifyNoInteractions(netease, lrclib);
    }

    @Test
    void manualNeteaseFailureTriesQqBeforeLrclib() throws Exception {
        settings.setAutoSelectBestLyric(false);
        settings.setLyricSource("netease");
        Lyric alternate = lyric("qq", "First Song");
        when(netease.getLyric(FIRST_TRACK)).thenThrow(new IOException("offline"));
        when(qq.getLyric(FIRST_TRACK)).thenReturn(alternate);

        assertSame(alternate, service.getLyric());
        InOrder order = inOrder(netease, qq);
        order.verify(netease).getLyric(FIRST_TRACK);
        order.verify(qq).getLyric(FIRST_TRACK);
        verifyNoInteractions(lrclib);
    }

    @Test
    void manualQqFailureTriesNeteaseBeforeLrclib() throws Exception {
        settings.setAutoSelectBestLyric(false);
        settings.setLyricSource("qq");
        Lyric alternate = lyric("netease", "First Song");
        when(qq.getLyric(FIRST_TRACK)).thenThrow(new IOException("offline"));
        when(netease.getLyric(FIRST_TRACK)).thenReturn(alternate);

        assertSame(alternate, service.getLyric());
        InOrder order = inOrder(qq, netease);
        order.verify(qq).getLyric(FIRST_TRACK);
        order.verify(netease).getLyric(FIRST_TRACK);
        verifyNoInteractions(lrclib);
    }

    @Test
    void manualDomesticFailuresReachLrclibLastAndOnlyOnce() throws Exception {
        settings.setAutoSelectBestLyric(false);
        settings.setLyricSource("qq");
        Lyric fallback = lyric("lrclib", "First Song");
        when(qq.getLyric(FIRST_TRACK)).thenThrow(new IOException("offline"));
        when(netease.getLyric(FIRST_TRACK)).thenReturn(null);
        when(lrclib.getLyric(FIRST_TRACK, 180)).thenReturn(fallback);

        assertSame(fallback, service.getLyric());
        assertSame(fallback, service.getLyric());
        InOrder order = inOrder(qq, netease, lrclib);
        order.verify(qq).getLyric(FIRST_TRACK);
        order.verify(netease).getLyric(FIRST_TRACK);
        order.verify(lrclib).getLyric(FIRST_TRACK, 180);
        verifyNoMoreInteractions(qq, netease, lrclib);
    }

    @Test
    void emptyLyricsWithTrueFlagsDoNotSuppressFallback() throws Exception {
        settings.setAutoSelectBestLyric(false);
        Lyric emptyFlagged = lyric("netease", "First Song");
        emptyFlagged.setLrc(" \n\t");
        emptyFlagged.setHasKaraokeLyric(true);
        emptyFlagged.setKaraokeLyric(" ");
        Lyric fallback = lyric("lrclib", "First Song");
        when(netease.getLyric(FIRST_TRACK)).thenReturn(emptyFlagged);
        when(qq.getLyric(FIRST_TRACK)).thenReturn(new Lyric());
        when(lrclib.getLyric(FIRST_TRACK, 180)).thenReturn(fallback);

        assertSame(fallback, service.getLyric());
        InOrder order = inOrder(netease, qq, lrclib);
        order.verify(netease).getLyric(FIRST_TRACK);
        order.verify(qq).getLyric(FIRST_TRACK);
        order.verify(lrclib).getLyric(FIRST_TRACK, 180);
    }

    @Test
    void autoModeUsesValidQqLyricsWhenNeteaseOnlyHasEmptyFlaggedLyrics() throws Exception {
        settings.setAutoSelectBestLyric(true);
        Lyric emptyFlagged = lyric("netease", "First Song");
        emptyFlagged.setLrc("");
        Lyric domestic = lyric("qq", "First Song");
        when(netease.getLyric(FIRST_TRACK)).thenReturn(emptyFlagged);
        when(qq.getLyric(FIRST_TRACK)).thenReturn(domestic);

        assertSame(domestic, service.getLyric());
        verify(netease).getLyric(FIRST_TRACK);
        verify(qq).getLyric(FIRST_TRACK);
        verifyNoInteractions(lrclib);
    }

    @Test
    void jayChouTriesQqThenNeteaseThenLrclibEvenInAutoMode() throws Exception {
        String jayTrack = "晴天 - 周杰倫";
        title.set(jayTrack);
        settings.setAutoSelectBestLyric(true);
        settings.setLyricSource("netease");
        Lyric fallback = lyric("lrclib", "晴天");
        when(qq.getLyric(jayTrack)).thenReturn(new Lyric());
        when(netease.getLyric(jayTrack)).thenThrow(new IOException("offline"));
        when(lrclib.getLyric(jayTrack, 180)).thenReturn(fallback);

        assertSame(fallback, service.getLyric());
        InOrder order = inOrder(qq, netease, lrclib);
        order.verify(qq).getLyric(jayTrack);
        order.verify(netease).getLyric(jayTrack);
        order.verify(lrclib).getLyric(jayTrack, 180);
        verifyNoMoreInteractions(qq, netease, lrclib);
    }

    @Test
    void offsetLyricResponseDoesNotMutateCachedLyric() {
        Lyric cached = lyric("netease", "First Song");
        cached.setLrc("[00:01.00]First line\n[00:02.00]Second line");
        cached.setTranslatedLyric("[00:01.00]Translated");
        cached.setKaraokeLyric("[0]First(1000,500)");
        ReflectionTestUtils.setField(service, "lyric", cached);
        ReflectionTestUtils.setField(service, "currentLyricWindowTitle", FIRST_TRACK);

        Lyric shifted = service.getLyric(1500);

        assertEquals("[00:02.50]First line\n[00:03.50]Second line", shifted.getLrc());
        assertEquals("[00:02.50]Translated", shifted.getTranslatedLyric());
        assertEquals("[0]First(2500,500)", shifted.getKaraokeLyric());
        assertEquals("[00:01.00]First line\n[00:02.00]Second line", cached.getLrc());
        assertSame(cached, ReflectionTestUtils.getField(service, "lyric"));
    }

    @Test
    void synchronousFetchDiscardsTrackThatChangedDuringLookup() throws Exception {
        settings.setAutoSelectBestLyric(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstProvider(started, release);
        Lyric second = lyric("netease", "Second Song");
        when(netease.getLyric(SECOND_TRACK)).thenReturn(second);

        CompletableFuture<Lyric> pending = CompletableFuture.supplyAsync(service::getLyric);
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            title.set(SECOND_TRACK);
        } finally {
            release.countDown();
        }

        Lyric result = pending.get(3, TimeUnit.SECONDS);
        assertFalse(result.getHasLyric());
        assertEquals("Second Song", result.getTitle());
        assertNull(ReflectionTestUtils.getField(service, "currentLyricWindowTitle"));
        assertFalse(((Lyric) ReflectionTestUtils.getField(service, "lyric")).getHasLyric());
        assertSame(second, service.getLyric());
        verifyNoInteractions(publisher);
    }

    @Test
    void synchronousFetchDiscardsResultAfterPlaybackCloses() throws Exception {
        settings.setAutoSelectBestLyric(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstProvider(started, release);

        CompletableFuture<Lyric> pending = CompletableFuture.supplyAsync(service::getLyric);
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            status.set("None");
        } finally {
            release.countDown();
        }

        Lyric result = pending.get(3, TimeUnit.SECONDS);
        assertFalse(result.getHasLyric());
        assertEquals("", result.getTitle());
        assertNull(ReflectionTestUtils.getField(service, "currentLyricWindowTitle"));
        verifyNoInteractions(publisher);
    }

    @Test
    void backgroundUpdateDoesNotCacheOrPublishObsoleteLyrics() throws Exception {
        settings.setAutoSelectBestLyric(false);
        ReflectionTestUtils.setField(service, "fetchLyricEnabled", true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstProvider(started, release);

        CompletableFuture<Void> pending = CompletableFuture.runAsync(
                () -> ReflectionTestUtils.invokeMethod(service, "updateLyric"));
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            title.set(SECOND_TRACK);
        } finally {
            release.countDown();
        }
        pending.get(3, TimeUnit.SECONDS);

        assertNull(ReflectionTestUtils.getField(service, "currentLyricWindowTitle"));
        assertFalse(((Lyric) ReflectionTestUtils.getField(service, "lyric")).getHasLyric());
        verify(publisher, never()).publishEvent(any(LyricChangedEvent.class));
    }

    @Test
    void forceRefreshDoesNotCacheOrPublishObsoleteLyrics() throws Exception {
        settings.setAutoSelectBestLyric(false);
        ReflectionTestUtils.setField(service, "fetchLyricEnabled", true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstProvider(started, release);

        service.forceRefreshLyric();
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            title.set(SECOND_TRACK);
        } finally {
            release.countDown();
        }

        // The refresh holds this lock until its asynchronous cache/event work finishes.
        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(service, "fetchLock");
        assertTrue(lock.tryLock(3, TimeUnit.SECONDS));
        try {
            assertNull(ReflectionTestUtils.getField(service, "currentLyricWindowTitle"));
            assertFalse(((Lyric) ReflectionTestUtils.getField(service, "lyric")).getHasLyric());
            verify(publisher, never()).publishEvent(any(LyricChangedEvent.class));
        } finally {
            lock.unlock();
        }
    }

    private void blockFirstProvider(CountDownLatch started, CountDownLatch release) throws Exception {
        when(netease.getLyric(FIRST_TRACK)).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return lyric("netease", "First Song");
        });
    }

    private static Lyric lyric(String source, String title) {
        Lyric lyric = new Lyric();
        lyric.setSource(source);
        lyric.setTitle(title);
        lyric.setAuthor("Singer");
        lyric.setHasLyric(true);
        lyric.setLrc("[00:00.00]" + title);
        return lyric;
    }
}

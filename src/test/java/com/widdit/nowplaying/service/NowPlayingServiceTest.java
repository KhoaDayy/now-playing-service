package com.widdit.nowplaying.service;

import com.widdit.nowplaying.entity.SettingsGeneral;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.service.kugou.KuGouMusicService;
import com.widdit.nowplaying.service.kuwo.KuWoMusicService;
import com.widdit.nowplaying.service.lrclib.LrclibService;
import com.widdit.nowplaying.service.netease.NeteaseMusicNewService;
import com.widdit.nowplaying.service.netease.NeteaseMusicService;
import com.widdit.nowplaying.service.qq.QQMusicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NowPlayingServiceTest {

    private static final String TITLE = "Song - Singer";

    private NowPlayingService service;
    private AudioService audio;
    private NeteaseMusicService netease;
    private NeteaseMusicNewService neteaseSmtc;
    private QQMusicService qq;
    private KuGouMusicService kugou;
    private KuWoMusicService kuwo;
    private LrclibService lrclib;
    private OutputService output;
    private ApplicationEventPublisher publisher;
    private SettingsGeneral settings;

    @BeforeEach
    void setUp() {
        service = new NowPlayingService();
        audio = mock(AudioService.class);
        netease = mock(NeteaseMusicService.class);
        neteaseSmtc = mock(NeteaseMusicNewService.class);
        qq = mock(QQMusicService.class);
        kugou = mock(KuGouMusicService.class);
        kuwo = mock(KuWoMusicService.class);
        lrclib = mock(LrclibService.class);
        output = mock(OutputService.class);
        publisher = mock(ApplicationEventPublisher.class);
        settings = new SettingsGeneral();
        ReflectionTestUtils.setField(service, "audioService", audio);
        ReflectionTestUtils.setField(service, "neteaseMusicService", netease);
        ReflectionTestUtils.setField(service, "neteaseMusicNewService", neteaseSmtc);
        ReflectionTestUtils.setField(service, "qqMusicService", qq);
        ReflectionTestUtils.setField(service, "kuGouMusicService", kugou);
        ReflectionTestUtils.setField(service, "kuWoMusicService", kuwo);
        ReflectionTestUtils.setField(service, "lrclibService", lrclib);
        ReflectionTestUtils.setField(service, "outputService", output);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);
        when(audio.getWindowTitle()).thenReturn(TITLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"browser", "youtube", "spotify", "apple"})
    void genericPlatformsTryQqBeforeLrclibWhenNeteaseFails(String platform) throws Exception {
        when(netease.search(TITLE)).thenThrow(new IOException("offline"));
        when(qq.search(TITLE)).thenReturn(track("qq"));

        enrich(platform);

        assertEquals("qq", service.queryTrack().getId());
        InOrder order = inOrder(netease, qq);
        order.verify(netease).search(TITLE);
        order.verify(qq).search(TITLE);
        verifyNoMoreInteractions(netease, qq);
        verifyNoInteractions(lrclib, neteaseSmtc, kugou, kuwo);
    }

    @Test
    void nullPrimaryResultsFallBackToLrclibOnce() throws Exception {
        when(netease.search(TITLE)).thenReturn(null);
        when(qq.search(TITLE)).thenReturn(null);
        when(lrclib.searchTrack(TITLE)).thenReturn(track("lrclib"));

        enrich("youtube");

        assertEquals("lrclib", service.queryTrack().getId());
        InOrder order = inOrder(netease, qq, lrclib);
        order.verify(netease).search(TITLE);
        order.verify(qq).search(TITLE);
        order.verify(lrclib).searchTrack(TITLE);
        verifyNoMoreInteractions(netease, qq, lrclib);
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "wesing"})
    void qqAndWesingKeepQqFirstThenTryNetease(String platform) throws Exception {
        when(qq.search(TITLE)).thenReturn(null);
        when(netease.search(TITLE)).thenReturn(track("netease"));

        enrich(platform);

        assertEquals("netease", service.queryTrack().getId());
        InOrder order = inOrder(qq, netease);
        order.verify(qq).search(TITLE);
        order.verify(netease).search(TITLE);
        verifyNoMoreInteractions(netease, qq);
        verifyNoInteractions(lrclib, neteaseSmtc, kugou, kuwo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "wesing", "kugou", "kuwo"})
    void successfulNativeProviderSkipsOtherProviders(String platform) throws Exception {
        if ("kugou".equals(platform)) {
            when(kugou.search(TITLE)).thenReturn(track(platform));
        } else if ("kuwo".equals(platform)) {
            when(kuwo.search(TITLE)).thenReturn(track(platform));
        } else {
            when(qq.search(TITLE)).thenReturn(track(platform));
        }

        enrich(platform);

        assertEquals(platform, service.queryTrack().getId());
        verifyNoInteractions(netease, neteaseSmtc, lrclib);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kugou", "kuwo"})
    void nativeProviderFailureTriesBothPrimarySourcesBeforeLrclib(String platform) throws Exception {
        if ("kugou".equals(platform)) {
            when(kugou.search(TITLE)).thenThrow(new IOException("offline"));
        } else {
            when(kuwo.search(TITLE)).thenThrow(new IOException("offline"));
        }
        when(netease.search(TITLE)).thenReturn(null);
        when(qq.search(TITLE)).thenThrow(new IOException("offline"));
        when(lrclib.searchTrack(TITLE)).thenReturn(track("lrclib"));

        enrich(platform);

        assertEquals("lrclib", service.queryTrack().getId());
        InOrder order = inOrder(kugou, kuwo, netease, qq, lrclib);
        if ("kugou".equals(platform)) {
            order.verify(kugou).search(TITLE);
        } else {
            order.verify(kuwo).search(TITLE);
        }
        order.verify(netease).search(TITLE);
        order.verify(qq).search(TITLE);
        order.verify(lrclib).searchTrack(TITLE);
        verifyNoMoreInteractions(kugou, kuwo, netease, qq, lrclib);
    }

    @Test
    void neteaseSmtcFailureFallsBackToSearchBeforeQqAndLrclib() throws Exception {
        settings.setSmtc(true);
        when(neteaseSmtc.getTrackInfo(TITLE)).thenThrow(new IOException("no local metadata"));
        when(netease.search(TITLE)).thenReturn(null);
        when(qq.search(TITLE)).thenReturn(track("qq"));

        enrich("netease");

        assertEquals("qq", service.queryTrack().getId());
        InOrder order = inOrder(neteaseSmtc, netease, qq);
        order.verify(neteaseSmtc).getTrackInfo(TITLE);
        order.verify(netease).search(TITLE);
        order.verify(qq).search(TITLE);
        verifyNoMoreInteractions(neteaseSmtc, netease, qq);
        verifyNoInteractions(lrclib);
    }

    @Test
    void successfulNeteaseSmtcKeepsLocalMetadata() throws Exception {
        settings.setSmtc(true);
        when(neteaseSmtc.getTrackInfo(TITLE)).thenReturn(track("smtc"));

        enrich("netease");

        assertEquals("smtc", service.queryTrack().getId());
        verify(neteaseSmtc).getTrackInfo(TITLE);
        verifyNoInteractions(netease, qq, lrclib, kugou, kuwo);
    }

    @Test
    void neteaseWithSmtcDisabledSearchesOnlyOnce() throws Exception {
        settings.setSmtc(false);
        when(netease.search(TITLE)).thenReturn(track("netease"));

        enrich("netease");

        assertEquals("netease", service.queryTrack().getId());
        verify(netease).search(TITLE);
        verifyNoMoreInteractions(netease);
        verifyNoInteractions(neteaseSmtc, qq, lrclib, kugou, kuwo);
    }

    @Test
    void obsoleteFallbackMetadataIsNotAppliedAfterTrackSwitch() throws Exception {
        Track current = service.queryTrack();
        when(netease.search(TITLE)).thenReturn(null);
        when(qq.search(TITLE)).thenReturn(null);
        when(lrclib.searchTrack(TITLE)).thenAnswer(invocation -> {
            when(audio.getWindowTitle()).thenReturn("Next Song - Singer");
            return track("obsolete");
        });

        enrich("spotify");

        assertSame(current, service.queryTrack());
        verifyNoInteractions(output, publisher);
    }

    private void enrich(String platform) {
        ReflectionTestUtils.invokeMethod(service, "enrichTrackMetadataAsync", TITLE, platform, settings);
    }

    private static Track track(String id) {
        Track track = new Track();
        track.setTitle("Song");
        track.setAuthor("Singer");
        track.setId(id);
        track.setDuration(180);
        return track;
    }
}

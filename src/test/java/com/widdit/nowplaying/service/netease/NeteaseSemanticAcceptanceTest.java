package com.widdit.nowplaying.service.netease;

import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import com.widdit.nowplaying.service.AudioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NeteaseSemanticAcceptanceTest {
    private static final String RESPONSE = "{\"code\":200,\"result\":{\"songs\":[{\"id\":123,\"name\":\"曲名\",\"artists\":[{\"name\":\"歌手\"}],\"duration\":180000,\"album\":{\"name\":\"Album\",\"picId\":1}}]}}";
    private NeteaseMusicService service;
    private JevSongReranker reranker;

    @BeforeEach
    void setUp() throws Exception {
        service = spy(new NeteaseMusicService());
        reranker = mock(JevSongReranker.class);
        ReflectionTestUtils.setField(service, "jevSongReranker", reranker);
        ReflectionTestUtils.setField(service, "audioService", mock(AudioService.class));
        doReturn(RESPONSE).when(service).sendSearchRequest(anyMap());
    }

    @Test
    void acceptedSemanticMatchSkipsRetryDespiteLowTraditionalScore() throws Exception {
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(this::acceptWeakCandidate);

        Track track = service.search("qzxw - prtn");
        assertEquals("123", track.getId());
        assertTrue(track.isSemanticMatchConfirmed());
        verify(service, times(1)).sendSearchRequest(anyMap());
    }

    @Test
    void acceptedSemanticMatchInRetryPassesTheFinalGate() throws Exception {
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList()))
                .thenReturn(null).thenAnswer(this::acceptWeakCandidate);

        assertEquals("123", service.search("qzxw - prtn").getId());
        verify(service, times(2)).sendSearchRequest(anyMap());
    }

    @Test
    void rejectedWeakMatchStillFailsAfterRetry() throws Exception {
        assertThrows(RuntimeException.class, () -> service.search("qzxw - prtn"));
        verify(service, times(2)).sendSearchRequest(anyMap());
    }

    @Test
    void jayChouSearchStaysOnNeteaseRatherThanDelegatingToQQ() throws Exception {
        doReturn(RESPONSE.replace("曲名", "夜曲").replace("歌手", "周杰伦"))
                .when(service).sendSearchRequest(anyMap());
        ReflectionTestUtils.setField(service, "jevSongReranker", null);

        assertEquals("夜曲", service.search("夜曲 - 周杰伦").getTitle());
        verify(service, times(1)).sendSearchRequest(anyMap());
    }

    @Test
    void confirmedSemanticSearchCanFetchLyricsDespiteDifferentProviderTitle() throws Exception {
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(this::acceptWeakCandidate);
        doReturn("{\"code\":200,\"lrc\":{\"lyric\":\"[00:01.00]line\"}}")
                .when(service).sendLyricRequest(anyMap());

        Lyric lyric = service.getLyric("qzxw - prtn");

        assertTrue(lyric.getHasLyric());
        assertEquals("[00:01.00]line", lyric.getLrc());
        verify(service, times(1)).sendLyricRequest(anyMap());
    }

    @Test
    void traditionalMatchStillNeedsTheExistingLyricThreshold() throws Exception {
        doReturn(Track.builder().id("123").title("曲名").author("歌手").duration(180).build())
                .when(service).search("qzxw - prtn");

        assertFalse(service.getLyric("qzxw - prtn").getHasLyric());
        verify(service, never()).sendLyricRequest(anyMap());
    }

    private SongCandidate<JSONObject> acceptWeakCandidate(org.mockito.invocation.InvocationOnMock invocation) {
        List<SongCandidate<JSONObject>> candidates = invocation.getArgument(2);
        assertTrue(candidates.get(0).getTraditionalScore() < 40);
        candidates.get(0).setSemanticMatchConfirmed(true);
        return candidates.get(0);
    }
}

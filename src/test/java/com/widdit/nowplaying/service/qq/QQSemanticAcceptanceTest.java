package com.widdit.nowplaying.service.qq;

import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QQSemanticAcceptanceTest {
    private static final String RESPONSE = "{\"req_1\":{\"code\":0,\"data\":{\"body\":{\"song\":{\"list\":[{\"id\":123,\"title\":\"曲名\",\"singer\":[{\"name\":\"歌手\"}],\"interval\":180,\"album\":{\"name\":\"Album\",\"mid\":\"album-id\"}}]}}}}}";
    private QQMusicService service;
    private JevSongReranker reranker;

    @BeforeEach
    void setUp() throws Exception {
        service = spy(new QQMusicService());
        reranker = mock(JevSongReranker.class);
        ReflectionTestUtils.setField(service, "jevSongReranker", reranker);
        doReturn(RESPONSE).when(service).sendPostRequest(anyString(), anyString());
    }

    @Test
    void acceptedSemanticMatchSkipsRetryDespiteLowTraditionalScore() throws Exception {
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(this::acceptWeakCandidate);

        Track track = service.search("qzxw - prtn");
        assertEquals("123", track.getId());
        assertTrue(track.isSemanticMatchConfirmed());
        verify(service, times(1)).sendPostRequest(anyString(), anyString());
    }

    @Test
    void acceptedSemanticMatchInRetryPassesTheFinalGate() throws Exception {
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList()))
                .thenReturn(null).thenAnswer(this::acceptWeakCandidate);

        assertEquals("123", service.search("qzxw - prtn").getId());
        verify(service, times(2)).sendPostRequest(anyString(), anyString());
    }

    @Test
    void rejectedWeakMatchStillFailsAfterRetry() throws Exception {
        assertThrows(RuntimeException.class, () -> service.search("qzxw - prtn"));
        verify(service, times(2)).sendPostRequest(anyString(), anyString());
    }

    @Test
    void confirmedSemanticSearchCanFetchLyricsDespiteDifferentProviderTitle() throws Exception {
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(this::acceptWeakCandidate);
        doReturn(QrcLyric.builder().qrc("[1000,1000]line(1000,1000)").trans("").build())
                .when(service).getQrcLyric("123");

        Lyric lyric = service.getLyric("qzxw - prtn");

        assertTrue(lyric.getHasLyric());
        assertEquals("[00:01.00]line", lyric.getLrc());
        verify(service, times(1)).getQrcLyric("123");
    }

    @Test
    void traditionalMatchStillNeedsTheExistingLyricThreshold() throws Exception {
        doReturn(Track.builder().id("123").title("曲名").author("歌手").duration(180).build())
                .when(service).search("qzxw - prtn");

        assertFalse(service.getLyric("qzxw - prtn").getHasLyric());
        verify(service, never()).getQrcLyric(anyString());
    }

    private SongCandidate<JSONObject> acceptWeakCandidate(org.mockito.invocation.InvocationOnMock invocation) {
        List<SongCandidate<JSONObject>> candidates = invocation.getArgument(2);
        assertTrue(candidates.get(0).getTraditionalScore() < 40);
        candidates.get(0).setSemanticMatchConfirmed(true);
        return candidates.get(0);
    }
}

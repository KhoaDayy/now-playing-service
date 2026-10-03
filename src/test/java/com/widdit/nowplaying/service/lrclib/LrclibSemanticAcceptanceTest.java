package com.widdit.nowplaying.service.lrclib;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LrclibSemanticAcceptanceTest {
    private static final String RESPONSE = "[{\"id\":123,\"trackName\":\"曲名\",\"artistName\":\"歌手\",\"duration\":180,\"albumName\":\"Album\",\"syncedLyrics\":\"[00:01.00]line\"}]";

    @Test
    void acceptedSemanticMatchReturnsSyncedLyricDespiteLowTraditionalScore() {
        LrclibService service = new LrclibService();
        JevSongReranker reranker = mock(JevSongReranker.class);
        ReflectionTestUtils.setField(service, "jevSongReranker", reranker);
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            List<SongCandidate<JSONObject>> candidates = invocation.getArgument(2);
            assertTrue(candidates.get(0).getTraditionalScore() < 40);
            return candidates.get(0);
        });

        Lyric lyric = service.selectBestSyncedLyric(JSON.parseArray(RESPONSE), "qzxw", "prtn", null);

        assertNotNull(lyric);
        assertEquals("[00:01.00]line", lyric.getLrc());
    }

    @Test
    void rejectedWeakMatchDoesNotReturnSyncedLyric() {
        LrclibService service = new LrclibService();
        ReflectionTestUtils.setField(service, "jevSongReranker", mock(JevSongReranker.class));

        assertNull(service.selectBestSyncedLyric(JSON.parseArray(RESPONSE), "qzxw", "prtn", null));
    }
}

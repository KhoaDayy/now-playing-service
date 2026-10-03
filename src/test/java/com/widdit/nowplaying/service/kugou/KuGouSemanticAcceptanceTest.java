package com.widdit.nowplaying.service.kugou;

import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.Track;
import com.widdit.nowplaying.jev.rerank.JevSongReranker;
import com.widdit.nowplaying.jev.rerank.SongCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KuGouSemanticAcceptanceTest {
    private static final String RESPONSE = "{\"error_code\":0,\"data\":{\"lists\":[{\"ID\":123,\"SongName\":\"曲名\",\"Singers\":[{\"name\":\"歌手\"}],\"Duration\":180,\"AlbumName\":\"Album\",\"Image\":\"http://example.invalid/{size}/cover.jpg\",\"FileHash\":\"song-hash\"}]}}";

    @Test
    void acceptedSemanticMatchPassesDespiteLowTraditionalScore() throws Exception {
        KuGouMusicService service = spy(new KuGouMusicService());
        JevSongReranker reranker = mock(JevSongReranker.class);
        ReflectionTestUtils.setField(service, "jevSongReranker", reranker);
        doReturn(RESPONSE).when(service).sendGetRequest(anyString());
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            List<SongCandidate<JSONObject>> candidates = invocation.getArgument(2);
            assertTrue(candidates.get(0).getTraditionalScore() < 40);
            candidates.get(0).setSemanticMatchConfirmed(true);
            return candidates.get(0);
        });

        Track track = service.search("qzxw - prtn");
        assertEquals("123", track.getId());
        assertTrue(track.isSemanticMatchConfirmed());
    }

    @Test
    void rejectedWeakMatchStillFails() throws Exception {
        KuGouMusicService service = spy(new KuGouMusicService());
        ReflectionTestUtils.setField(service, "jevSongReranker", mock(JevSongReranker.class));
        doReturn(RESPONSE).when(service).sendGetRequest(anyString());

        assertThrows(RuntimeException.class, () -> service.search("qzxw - prtn"));
    }

    @Test
    void confirmedSemanticSearchCanFetchLyricsDespiteDifferentProviderTitle() throws Exception {
        KuGouMusicService service = spy(new KuGouMusicService());
        JevSongReranker reranker = mock(JevSongReranker.class);
        ReflectionTestUtils.setField(service, "jevSongReranker", reranker);
        when(reranker.selectBestCandidate(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            List<SongCandidate<JSONObject>> candidates = invocation.getArgument(2);
            assertTrue(candidates.get(0).getTraditionalScore() < 40);
            candidates.get(0).setSemanticMatchConfirmed(true);
            return candidates.get(0);
        });
        doReturn(RESPONSE,
                "{\"errcode\":200,\"candidates\":[{\"id\":\"lyric-id\",\"accesskey\":\"key\"}]}",
                "{\"error_code\":200,\"content\":\"" + encryptedKrcFixture() + "\"}")
                .when(service).sendGetRequest(anyString());

        Lyric lyric = service.getLyric("qzxw - prtn");

        assertTrue(lyric.getHasLyric());
        assertEquals("[00:01.00]line", lyric.getLrc());
        verify(service, times(3)).sendGetRequest(anyString());
    }

    @Test
    void traditionalMatchStillNeedsTheExistingLyricThreshold() throws Exception {
        KuGouMusicService service = spy(new KuGouMusicService());
        doReturn(Track.builder().id("123").title("曲名").author("歌手").duration(180).build())
                .when(service).search("qzxw - prtn");

        assertFalse(service.getLyric("qzxw - prtn").getHasLyric());
        verify(service, never()).sendGetRequest(anyString());
    }

    private String encryptedKrcFixture() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream output = new DeflaterOutputStream(compressed)) {
            output.write("\uFEFF[1000,1000]<0,1000,0>line".getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = compressed.toByteArray();
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        encrypted.write(new byte[] {'k', 'r', 'c', '1'});
        for (int i = 0; i < bytes.length; i++) {
            encrypted.write(bytes[i] ^ Decrypter.DECRYPT_KEY[i % Decrypter.DECRYPT_KEY.length]);
        }
        return Base64.getEncoder().encodeToString(encrypted.toByteArray());
    }
}

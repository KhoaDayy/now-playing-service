package com.widdit.nowplaying.jev.rerank;

import com.widdit.nowplaying.jev.client.JevClient;
import com.widdit.nowplaying.jev.model.JevAnswer;
import com.widdit.nowplaying.jev.model.JevResponse;
import com.widdit.nowplaying.util.SongMatchingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class JevSongRerankerTest {

    private JevClient client;
    private JevSongReranker reranker;

    @BeforeEach
    void setUp() {
        client = mock(JevClient.class);
        reranker = new JevSongReranker(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 59, 60, 90})
    void disabledJevUsesTheSameFallbackFloor(int score) {
        SongCandidate<String> candidate = candidate("original-id", "Song", score);

        SongCandidate<String> selected = reranker.selectBestCandidate(
                "Song", "Artist", Collections.singletonList(candidate));

        if (score >= SongMatchingUtil.ALTERNATE_VERSION_THRESHOLD) {
            assertSame(candidate, selected);
        } else {
            assertNull(selected);
        }
        verify(client, never()).evaluate(any(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(ints = {59, 60, 80})
    void failedJevUsesFallbackOnlyWhenTheFloorIsMet(int score) {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(JevResponse.failure("timeout", 2500));
        SongCandidate<String> candidate = candidate("original-id", "Song", score);

        SongCandidate<String> selected = reranker.selectBestCandidate(
                "Song", "Artist", Collections.singletonList(candidate));

        if (score >= SongMatchingUtil.ALTERNATE_VERSION_THRESHOLD) {
            assertSame(candidate, selected);
        } else {
            assertNull(selected);
        }
    }

    @Test
    void ranksBeforeLimitingAndDoesNotMutateTheInput() {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(choiceResponse("cand_0", 0.90));
        List<SongCandidate<String>> original = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            original.add(candidate("provider-id-" + i, "Song " + i, 10 + i * 10));
        }
        List<SongCandidate<String>> input = Collections.unmodifiableList(original);
        List<String> originalIds = new ArrayList<>();
        for (SongCandidate<String> candidate : input) {
            originalIds.add(candidate.getId());
        }

        SongCandidate<String> selected = reranker.selectBestCandidate("Song", "Artist", input);

        assertSame(original.get(8), selected, "The strongest ninth candidate must enter the shortlist");
        ArgumentCaptor<Object> stateCaptor = ArgumentCaptor.forClass(Object.class);
        verify(client).evaluate(stateCaptor.capture(), anyMap());
        Map<?, ?> state = (Map<?, ?>) stateCaptor.getValue();
        List<?> shortlist = (List<?>) state.get("candidates");
        assertEquals(8, shortlist.size());
        assertEquals("Song 8", ((Map<?, ?>) shortlist.get(0)).get("title"));
        assertEquals("Song 1", ((Map<?, ?>) shortlist.get(7)).get("title"));
        for (int i = 0; i < input.size(); i++) {
            assertSame(original.get(i), input.get(i));
            assertEquals("Song " + i, input.get(i).getTitle());
            assertEquals(originalIds.get(i), input.get(i).getId());
        }
    }

    @Test
    void tiesPreserveInputOrderInTheShortlistAndFallback() {
        SongCandidate<String> first = candidate("first-id", "First", 70);
        SongCandidate<String> second = candidate("second-id", "Second", 70);
        List<SongCandidate<String>> input = Arrays.asList(first, second);
        assertSame(first, reranker.selectBestCandidate("Song", "Artist", input));

        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(choiceResponse("cand_0", 0.90));
        assertSame(first, reranker.selectBestCandidate("Song", "Artist", input));
    }

    @Test
    void confidentNoneOfAboveRejectsEvenAStrongFallback() {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(choiceResponse("none_of_above", 0.90));

        assertNull(reranker.selectBestCandidate("Song", "Artist", Arrays.asList(
                candidate("first", "Wrong song", 95), candidate("second", "Other song", 70))));
    }

    @ParameterizedTest
    @MethodSource("unusableAnswers")
    void unusableAnswersUseTheTraditionalFloor(String type, String choice, Double confidence) {
        when(client.isAvailable()).thenReturn(true);
        JevAnswer answer = JevAnswer.builder().type(type).choice(choice).confidence(confidence).build();
        when(client.evaluate(any(), anyMap())).thenReturn(JevResponse.builder()
                .answers(Collections.singletonMap("best_match", answer)).build());
        SongCandidate<String> strongest = candidate("first", "First", 70);
        SongCandidate<String> other = candidate("second", "Second", 40);

        assertSame(strongest, reranker.selectBestCandidate("Song", "Artist", Arrays.asList(strongest, other)));
        strongest.setTraditionalScore(59);
        assertNull(reranker.selectBestCandidate("Song", "Artist", Arrays.asList(strongest, other)));
    }

    static Stream<Arguments> unusableAnswers() {
        return Stream.of(
                Arguments.of("choice", "cand_1", 0.59),
                Arguments.of("choice", "unknown_candidate", 0.90),
                Arguments.of("noul", "cand_1", 0.90),
                Arguments.of(null, "cand_1", 0.90),
                Arguments.of("choice", null, 0.90),
                Arguments.of("choice", "cand_1", null),
                Arguments.of("choice", "cand_1", Double.NaN),
                Arguments.of("choice", "cand_1", Double.POSITIVE_INFINITY),
                Arguments.of("choice", "cand_1", 1.01));
    }

    @Test
    void confidenceAtTheThresholdCanSelectALowerTraditionalScore() {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(choiceResponse("cand_1", 0.60));
        SongCandidate<String> semanticMatch = candidate("second", "Translated title", 20);

        assertSame(semanticMatch, reranker.selectBestCandidate("Song", "Artist", Arrays.asList(
                candidate("first", "Unrelated title", 70), semanticMatch)));
        assertTrue(semanticMatch.isSemanticMatchConfirmed());
        assertEquals(20, semanticMatch.getTraditionalScore());
    }

    @Test
    void reusedCandidatesLoseSemanticConfirmationOnTraditionalFallback() {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(choiceResponse("cand_1", 0.90));
        SongCandidate<String> traditional = candidate("first", "First", 70);
        SongCandidate<String> semantic = candidate("second", "Translated title", 20);
        List<SongCandidate<String>> candidates = Arrays.asList(traditional, semantic);
        assertSame(semantic, reranker.selectBestCandidate("Song", "Artist", candidates));
        assertTrue(semantic.isSemanticMatchConfirmed());

        when(client.isAvailable()).thenReturn(false);
        assertSame(traditional, reranker.selectBestCandidate("Song", "Artist", candidates));
        assertFalse(semantic.isSemanticMatchConfirmed());
        assertFalse(traditional.isSemanticMatchConfirmed());
    }

    @Test
    void rejectedAnswerClearsPreviouslyConfirmedCandidates() {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(choiceResponse("none_of_above", 0.90));
        SongCandidate<String> candidate = candidate("first", "First", 70);
        candidate.setSemanticMatchConfirmed(true);

        assertNull(reranker.selectBestCandidate("Song", "Artist", Collections.singletonList(candidate)));
        assertFalse(candidate.isSemanticMatchConfirmed());
    }

    @Test
    void singleStrongCandidateShortcutDoesNotConfirmSemanticMatch() {
        when(client.isAvailable()).thenReturn(true);
        SongCandidate<String> candidate = candidate("first", "First", 90);
        candidate.setSemanticMatchConfirmed(true);

        assertSame(candidate, reranker.selectBestCandidate("Song", "Artist", Collections.singletonList(candidate)));
        assertFalse(candidate.isSemanticMatchConfirmed());
        verify(client, never()).evaluate(any(), anyMap());
    }

    @Test
    void emptyAndNullCandidatesAreSafe() {
        assertNull(reranker.selectBestCandidate("Song", "Artist", null));
        assertNull(reranker.selectBestCandidate("Song", "Artist", Collections.emptyList()));
        assertNull(reranker.selectBestCandidate("Song", "Artist", Arrays.asList(null, null)));
        SongCandidate<String> candidate = candidate("valid", "Song", 70);
        assertSame(candidate, reranker.selectBestCandidate("Song", "Artist", Arrays.asList(null, candidate, null)));
    }

    @Test
    void missingAnswerAndClientExceptionRespectFallbackFloor() {
        when(client.isAvailable()).thenReturn(true);
        when(client.evaluate(any(), anyMap())).thenReturn(JevResponse.builder().build());
        SongCandidate<String> weak = candidate("weak", "Song", 59);
        assertNull(reranker.selectBestCandidate("Song", "Artist", Collections.singletonList(weak)));

        when(client.evaluate(any(), anyMap())).thenThrow(new IllegalStateException("unavailable"));
        assertNull(reranker.selectBestCandidate("Song", "Artist", Collections.singletonList(weak)));
        weak.setTraditionalScore(60);
        assertSame(weak, reranker.selectBestCandidate("Song", "Artist", Collections.singletonList(weak)));
    }

    private static SongCandidate<String> candidate(String id, String title, int traditionalScore) {
        return SongCandidate.<String>builder().id(id).title(title).artist("Artist")
                .traditionalScore(traditionalScore).rawObject(id).build();
    }

    private static JevResponse choiceResponse(String choice, double confidence) {
        JevAnswer answer = JevAnswer.builder().type("choice").choice(choice).confidence(confidence).build();
        return JevResponse.builder().answers(Collections.singletonMap("best_match", answer)).build();
    }
}

package com.widdit.nowplaying.util.lyric;

import org.junit.jupiter.api.Test;
import com.widdit.nowplaying.entity.Lyric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LyricOffsetUtilTest {

    @Test
    void shiftsLrcAndPreservesMetadataAndPrecision() {
        String lrc = "[ar:Artist]\n[00:01.50][01:02.123]Line";

        assertEquals("[ar:Artist]\n[00:03.00][01:03.623]Line",
                LyricOffsetUtil.shiftLrc(lrc, 1500));
    }

    @Test
    void negativeOffsetClampsAtZero() {
        assertEquals("[00:00.00]Line", LyricOffsetUtil.shiftLrc("[00:01.00]Line", -2000));
    }

    @Test
    void shiftsKaraokeLineAndSyllableStartsButNotDurations() {
        String karaoke = "[420,4440](420,1320,0)Lately(1740,570,0)I've";

        assertEquals("[920,4440](920,1320,0)Lately(2240,570,0)I've",
                LyricOffsetUtil.shiftKaraoke(karaoke, 500));
    }

    @Test
    void shiftsTwoFieldQrcAndLysSyllables() {
        String karaoke = "[0,1000]Word(0,500)Next(500,500)";

        assertEquals("[250,1000]Word(250,500)Next(750,500)",
                LyricOffsetUtil.shiftKaraoke(karaoke, 250));
    }

    @Test
    void shiftsLysSyllablesWithTwoFieldTuples() {
        String lys = "[0]Lately(420,1320)I've(1740,570)";

        assertEquals("[0]Lately(920,1320)I've(2240,570)",
                LyricOffsetUtil.shiftKaraoke(lys, 500));
    }

    @Test
    void leavesRelativeKrcSyllableTimesUnchanged() {
        assertEquals("[1500,2000]<0,500,0>Word<500,1000,0>Next",
                LyricOffsetUtil.shiftKaraoke("[1000,2000]<0,500,0>Word<500,1000,0>Next", 500));
    }

    @Test
    void preservesMetadataAndMalformedTimestampText() {
        String lrc = "[ar:Artist]\n[00:xx]Malformed\n[00:01]Valid";

        assertEquals("[ar:Artist]\n[00:xx]Malformed\n[00:02]Valid",
                LyricOffsetUtil.shiftLrc(lrc, 1000));
    }

    @Test
    void preservesFractionSeparatorAndRollsOverMinute() {
        String lrc = "[00:59:500]Line";

        assertEquals("[01:00:500]Line", LyricOffsetUtil.shiftLrc(lrc, 1000));
    }

    @Test
    void zeroOffsetReturnsOriginalValue() {
        String lrc = "[00:01.00]Line";
        assertEquals(lrc, LyricOffsetUtil.shiftLrc(lrc, 0));
        assertEquals(lrc, LyricOffsetUtil.shiftKaraoke(lrc, 0));
    }

    @Test
    void preservesMillisecondOffsetOnLowPrecisionSourceTags() {
        assertEquals("[00:02.5]Line", LyricOffsetUtil.shiftLrc("[00:01]Line", 1500));
        assertEquals("[00:01.011]Line", LyricOffsetUtil.shiftLrc("[00:01.01]Line", 1));
        assertEquals("[00:00.999]Line", LyricOffsetUtil.shiftLrc("[00:01]Line", -1));
    }

    @Test
    void preservesInvalidAndOverflowingTimestamps() {
        assertEquals("[00:99.00]Line", LyricOffsetUtil.shiftLrc("[00:99.00]Line", 1500));
        String karaoke = "[9223372036854775807,500]Word(9999999999999999999999,100)";
        assertEquals(karaoke, LyricOffsetUtil.shiftKaraoke(karaoke, 1500));
    }

    @Test
    void clampsConsumerOffsetAndKeepsMetadataAndOriginalData() {
        Lyric source = Lyric.builder().source("qq").title("Song").author("Artist").duration(180)
                .hasLyric(true).hasTranslatedLyric(true).hasKaraokeLyric(true)
                .lrc("[00:01.00]Line").translatedLyric("[00:01.00]Translation")
                .karaokeLyric("[1000,500]Word(1000,500)").build();
        Lyric delayed = LyricOffsetUtil.shift(source, Integer.MAX_VALUE);
        assertEquals("[10:01.00]Line", delayed.getLrc());
        assertEquals("[10:01.00]Translation", delayed.getTranslatedLyric());
        assertEquals("[601000,500]Word(601000,500)", delayed.getKaraokeLyric());
        assertEquals(source.getSource(), delayed.getSource());
        assertEquals(source.getTitle(), delayed.getTitle());
        assertEquals(source.getAuthor(), delayed.getAuthor());
        assertEquals(source.getDuration(), delayed.getDuration());
        assertEquals(source.getHasKaraokeLyric(), delayed.getHasKaraokeLyric());
        assertEquals("[00:00.00]Line", LyricOffsetUtil.shift(source, Integer.MIN_VALUE).getLrc());
        assertEquals("[00:01.00]Line", source.getLrc());
        assertSame(source, LyricOffsetUtil.shift(source, null));
        assertSame(source, LyricOffsetUtil.shift(source, 0));
    }
}

package com.widdit.nowplaying.util.lyric;

import com.widdit.nowplaying.entity.Lyric;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a transport-specific timing correction without changing the lyric cache.
 * Positive offsets delay lyric timestamps; negative offsets make them earlier.
 */
public final class LyricOffsetUtil {

    public static final int MAX_OFFSET_MS = 600_000;

    private static final Pattern LRC_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:([.:])(\\d{1,3}))?\\]");
    private static final Pattern KARAOKE_LINE_TIME = Pattern.compile("\\[(\\d+),(\\d+)\\]");
    private static final Pattern KARAOKE_SYLLABLE_TIME = Pattern.compile("\\((\\d+),(\\d+)(,\\d+)?\\)");

    private LyricOffsetUtil() {
    }

    /** Returns a shifted copy for one consumer, keeping the shared cache intact. */
    public static Lyric shift(Lyric source, Integer offsetMs) {
        if (source == null || offsetMs == null || offsetMs == 0) {
            return source;
        }
        long safeOffset = Math.max(-MAX_OFFSET_MS, Math.min(MAX_OFFSET_MS, offsetMs.longValue()));
        return Lyric.builder()
                .source(source.getSource())
                .title(source.getTitle())
                .author(source.getAuthor())
                .duration(source.getDuration())
                .hasLyric(source.getHasLyric())
                .hasTranslatedLyric(source.getHasTranslatedLyric())
                .hasKaraokeLyric(source.getHasKaraokeLyric())
                .lrc(shiftLrc(source.getLrc(), safeOffset))
                .translatedLyric(shiftLrc(source.getTranslatedLyric(), safeOffset))
                .karaokeLyric(shiftKaraoke(source.getKaraokeLyric(), safeOffset))
                .build();
    }

    /**
     * Shifts standard LRC timestamps while preserving metadata tags and precision.
     */
    public static String shiftLrc(String lyric, long offsetMs) {
        if (lyric == null || lyric.isEmpty() || offsetMs == 0) {
            return lyric;
        }
        Matcher matcher = LRC_TAG.matcher(lyric);
        StringBuffer result = new StringBuffer(lyric.length() + 16);
        while (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            if (seconds >= 60) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String separator = matcher.group(3);
            String fraction = matcher.group(4);
            long timestampMs = minutes * 60_000L + seconds * 1_000L
                    + (fraction == null ? 0 : fractionToMillis(fraction));
            long shifted = Math.max(0L, timestampMs + offsetMs);
            String replacement = formatLrcTag(shifted, separator, fraction == null ? 0 : fraction.length());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Shifts line and syllable start times in YRC/QRC/LYS-like karaoke payloads.
     * Durations and non-numeric metadata are left unchanged.
     */
    public static String shiftKaraoke(String lyric, long offsetMs) {
        if (lyric == null || lyric.isEmpty() || offsetMs == 0) {
            return lyric;
        }
        String shifted = shiftTuple(lyric, KARAOKE_LINE_TIME, offsetMs);
        return shiftTuple(shifted, KARAOKE_SYLLABLE_TIME, offsetMs);
    }

    private static String shiftTuple(String input, Pattern pattern, long offsetMs) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer(input.length() + 16);
        while (matcher.find()) {
            String replacement = matcher.group();
            try {
                long start = Long.parseLong(matcher.group(1));
                long shifted = Math.max(0L, Math.addExact(start, offsetMs));
                int startPosition = matcher.start(1) - matcher.start();
                int endPosition = matcher.end(1) - matcher.start();
                replacement = replacement.substring(0, startPosition) + shifted + replacement.substring(endPosition);
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Preserve malformed or overflowing source timestamps.
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static long fractionToMillis(String fraction) {
        if (fraction.length() == 1) return Integer.parseInt(fraction) * 100L;
        if (fraction.length() == 2) return Integer.parseInt(fraction) * 10L;
        return Integer.parseInt(fraction.substring(0, 3));
    }

    private static String formatLrcTag(long timestampMs, String separator, int fractionDigits) {
        long minutes = timestampMs / 60_000L;
        long remainder = timestampMs % 60_000L;
        long seconds = remainder / 1_000L;
        long millis = remainder % 1_000L;
        // Increase precision when needed so a millisecond offset is never truncated.
        int neededDigits = millis % 10 != 0 ? 3 : millis % 100 != 0 ? 2 : millis != 0 ? 1 : 0;
        fractionDigits = Math.max(fractionDigits, neededDigits);
        if (separator == null) separator = ".";
        String fraction;
        if (fractionDigits == 1) {
            fraction = Long.toString(millis / 100L);
        } else if (fractionDigits == 2) {
            fraction = String.format(Locale.ROOT, "%02d", millis / 10L);
        } else {
            fraction = String.format(Locale.ROOT, "%03d", millis);
        }
        if (fractionDigits == 0) {
            return String.format(Locale.ROOT, "[%02d:%02d]", minutes, seconds);
        }
        return String.format(Locale.ROOT, "[%02d:%02d%s%s]", minutes, seconds, separator, fraction);
    }
}

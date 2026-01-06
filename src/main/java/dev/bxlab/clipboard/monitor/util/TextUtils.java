package dev.bxlab.clipboard.monitor.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class for text manipulation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextUtils {

    private static final int DEFAULT_TRUNCATE_LENGTH = 16;

    /**
     * Truncates a string to the default length (16 characters).
     *
     * @param text string to truncate
     * @return truncated string with "..." suffix, or empty string if null
     */
    public static String truncate(String text) {
        return truncate(text, DEFAULT_TRUNCATE_LENGTH);
    }

    /**
     * Truncates a string to a specified length.
     *
     * @param text   string to truncate
     * @param length maximum length before truncation
     * @return truncated string with "..." suffix, or empty string if null
     */
    public static String truncate(String text, int length) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= length) {
            return normalized;
        }
        return normalized.substring(0, length) + "...";
    }
}

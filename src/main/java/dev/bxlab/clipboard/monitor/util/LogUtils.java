package dev.bxlab.clipboard.monitor.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class for logging helpers.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogUtils {

    private static final int DEFAULT_HASH_DISPLAY_LENGTH = 8;

    /**
     * Truncates a hash string for display in logs.
     *
     * @param hash hash string to truncate
     * @return truncated hash with "..." suffix, or empty string if null
     */
    public static String truncateHash(String hash) {
        return truncateHash(hash, DEFAULT_HASH_DISPLAY_LENGTH);
    }

    /**
     * Truncates a hash string to specified length for display in logs.
     *
     * @param hash   hash string to truncate
     * @param length maximum length before truncation
     * @return truncated hash with "..." suffix, or empty string if null
     */
    public static String truncateHash(String hash, int length) {
        if (hash == null || hash.isEmpty()) {
            return "";
        }
        if (hash.length() <= length) {
            return hash;
        }
        return hash.substring(0, length) + "...";
    }
}

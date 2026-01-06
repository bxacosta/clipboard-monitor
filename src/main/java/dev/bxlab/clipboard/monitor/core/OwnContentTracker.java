package dev.bxlab.clipboard.monitor.core;

import dev.bxlab.clipboard.monitor.util.TextUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks content written by this monitor to prevent notification loops.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OwnContentTracker {

    /**
     * Maximum number of hashes to track.
     */
    private static final int MAX_ENTRIES = 10;

    /**
     * Time-to-live in nanoseconds (5 seconds).
     */
    private static final long TTL_NANOS = 5_000_000_000L;

    /**
     * LRU cache mapping hash to write timestamp.
     */
    private final Map<String, Long> recentHashes = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    if (super.size() > MAX_ENTRIES) {
                        log.debug("LRU eviction: removing hash {} (capacity exceeded)",
                                TextUtils.truncate(eldest.getKey()));
                        return true;
                    }
                    // Also remove if TTL expired
                    if ((System.nanoTime() - eldest.getValue()) > TTL_NANOS) {
                        log.debug("TTL eviction: removing hash {} (expired)", TextUtils.truncate(eldest.getKey()));
                        return true;
                    }
                    return false;
                }
            });

    /**
     * Creates a new OwnContentTracker instance.
     *
     * @return new tracker instance
     */
    public static OwnContentTracker create() {
        return new OwnContentTracker();
    }

    /**
     * Marks a hash as own content.
     *
     * @param hash content hash, may be null or empty (ignored)
     */
    public void markOwn(String hash) {
        if (hash == null || hash.isEmpty()) {
            return;
        }

        recentHashes.put(hash, System.nanoTime());
        log.debug("Marked hash as own: {} (tracked: {})", TextUtils.truncate(hash), recentHashes.size());
    }

    /**
     * Checks if content with the given hash was written by this monitor.
     *
     * @param hash content hash, may be null or empty
     * @return true if content was written by this monitor, false otherwise
     */
    public boolean isOwn(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        Long writeTime = recentHashes.get(hash);
        if (writeTime == null) {
            return false;
        }

        long ageNanos = System.nanoTime() - writeTime;
        if (ageNanos > TTL_NANOS) {
            // Lazy cleanup: remove expired entry
            recentHashes.remove(hash);
            log.debug("Hash expired and removed: {} (age: {}ms)", TextUtils.truncate(hash), ageNanos / 1_000_000);
            return false;
        }

        log.debug("Hash is own content: {} (age: {}ms)", TextUtils.truncate(hash), ageNanos / 1_000_000);
        return true;
    }

    /**
     * Clears all tracked hashes.
     */
    public void clear() {
        recentHashes.clear();
        log.debug("Own content tracker cleared");
    }

    /**
     * Returns the number of currently tracked hashes.
     *
     * @return number of tracked hashes
     */
    public int size() {
        return recentHashes.size();
    }
}

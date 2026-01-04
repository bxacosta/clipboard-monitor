package dev.bxlab.clipboard.monitor.internal;

import dev.bxlab.clipboard.monitor.util.TextUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks content written by this monitor to prevent notification loops.
 * <p>
 * Uses an LRU (Least Recently Used) cache with TTL (Time To Live) to track
 * recently written content hashes. This allows proper detection of own content
 * even in multi-write scenarios where multiple writes happen in quick succession.
 * <p>
 * Key features:
 * <ul>
 *   <li>LRU eviction: oldest entries removed when capacity exceeded</li>
 *   <li>TTL expiration: entries expire after 5 seconds</li>
 *   <li>Thread-safe: synchronized map wrapper</li>
 *   <li>Memory bounded: maximum 10 entries (~2KB)</li>
 * </ul>
 * <p>
 * This class fixes the grace period bug in the previous implementation by using
 * AND logic: a hash is only considered "own" if it exists in the cache AND
 * is within the TTL window.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OwnContentTracker {

    /**
     * Maximum number of hashes to track.
     * Supports multi-write scenarios where several writes happen quickly.
     */
    private static final int MAX_ENTRIES = 10;

    /**
     * Time-to-live in nanoseconds (5 seconds).
     * After this time, entries are considered expired and will be removed.
     */
    private static final long TTL_NANOS = 5_000_000_000L;

    /**
     * LRU cache mapping hash -> write timestamp (in nanos).
     * Uses access-order LinkedHashMap wrapped in a synchronized map.
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
     * Marks a hash as own content (written by this monitor).
     * <p>
     * The hash will be remembered for up to 5 seconds or until evicted
     * by newer entries (LRU eviction with max 10 entries).
     *
     * @param hash SHA-256 hash of written content, may be null or empty (ignored)
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
     * <p>
     * Returns true only if the hash exists in the cache AND is within the TTL.
     * This fixes the grace period bug where ANY change was ignored during
     * a time window after writing.
     *
     * @param hash SHA-256 hash of content to verify, may be null or empty
     * @return true if content was written by this monitor and is within TTL, false otherwise
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
     * <p>
     * Should be called when the monitor is stopped or reset.
     */
    public void clear() {
        recentHashes.clear();
        log.debug("Own content tracker cleared");
    }

    /**
     * Returns the number of currently tracked hashes.
     * <p>
     * Note: this count may include expired entries that haven't been
     * lazily cleaned up yet.
     *
     * @return number of tracked hashes
     */
    public int size() {
        return recentHashes.size();
    }
}

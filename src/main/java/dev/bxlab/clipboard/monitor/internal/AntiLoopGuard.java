package dev.bxlab.clipboard.monitor.internal;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Prevents infinite loops in bidirectional synchronization.
 * <p>
 * When the monitor writes content to the clipboard, this guard marks it as "own"
 * to prevent notifying it as an external change. Thread-safe.
 */
@Slf4j
public final class AntiLoopGuard {

    private static final long TIME_WINDOW_MS = 500;
    private static final long HASH_EXPIRY_SECONDS = 5;

    private final Set<String> ownHashes = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler;
    private volatile long lastWriteTime = 0;

    public AntiLoopGuard(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Marks a hash as own content (written by this monitor).
     * Hash is remembered for 5 seconds to prevent loops.
     *
     * @param hash SHA-256 of written content
     */
    public void markAsOwn(String hash) {
        if (hash == null || hash.isEmpty()) {
            return;
        }

        ownHashes.add(hash);
        lastWriteTime = System.currentTimeMillis();

        log.debug("Marked hash as own: {}...", hash.substring(0, Math.min(8, hash.length())));

        scheduler.schedule(() -> {
            ownHashes.remove(hash);
            log.debug("Removed own hash: {}...", hash.substring(0, Math.min(8, hash.length())));
        }, HASH_EXPIRY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Checks if content with a given hash was written by this monitor.
     * Returns true if within 500ms time window after write or hash is in own hashes.
     *
     * @param hash SHA-256 of content to verify
     * @return true if content is own, false if external
     */
    public boolean isOwnContent(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - lastWriteTime;
        if (elapsed < TIME_WINDOW_MS) {
            log.debug("Within time window ({}ms), assuming own content", elapsed);
            return true;
        }

        boolean isOwn = ownHashes.contains(hash);
        if (isOwn) {
            log.debug("Hash found in own hashes: {}...", hash.substring(0, Math.min(8, hash.length())));
        }

        return isOwn;
    }

    /**
     * Clears all marked hashes.
     */
    public void clear() {
        ownHashes.clear();
        lastWriteTime = 0;
        log.debug("Anti-loop guard cleared");
    }

    /**
     * @return number of currently marked own hashes
     */
    public int getOwnHashCount() {
        return ownHashes.size();
    }
}

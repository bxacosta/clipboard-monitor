package dev.bxlab.clipboard.monitor.internal;

import dev.bxlab.clipboard.monitor.util.LogUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
    private final Map<String, ScheduledFuture<?>> expiryTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private volatile long lastWriteNanoTime = 0;

    /**
     * Creates a new anti-loop guard.
     *
     * @param scheduler scheduler for expiry tasks
     */
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
        lastWriteNanoTime = System.nanoTime();

        log.debug("Marked hash as own: {}", LogUtils.truncateHash(hash));

        expiryTasks.compute(hash, (k, oldTask) -> {
            if (oldTask != null && !oldTask.isDone()) {
                oldTask.cancel(false);
            }
            return scheduler.schedule(() -> {
                ownHashes.remove(hash);
                expiryTasks.remove(hash);
                log.debug("Removed own hash: {}", LogUtils.truncateHash(hash));
            }, HASH_EXPIRY_SECONDS, TimeUnit.SECONDS);
        });
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

        long elapsedNanos = System.nanoTime() - lastWriteNanoTime;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (elapsedMs < TIME_WINDOW_MS && elapsedMs >= 0) {
            log.debug("Within time window ({}ms), assuming own content", elapsedMs);
            return true;
        }

        boolean isOwn = ownHashes.contains(hash);
        if (isOwn) {
            log.debug("Hash found in own hashes: {}", LogUtils.truncateHash(hash));
        }

        return isOwn;
    }

    /**
     * Clears all marked hashes and cancels pending expiry tasks.
     */
    public void clear() {
        ownHashes.clear();
        expiryTasks.values().forEach(task -> task.cancel(false));
        expiryTasks.clear();
        lastWriteNanoTime = 0;
        log.debug("Anti-loop guard cleared");
    }

    /**
     * @return number of currently marked own hashes
     */
    public int getOwnHashCount() {
        return ownHashes.size();
    }
}

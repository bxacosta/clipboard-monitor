package dev.bxlab.clipboard.monitor.internal.detector;

import dev.bxlab.clipboard.monitor.internal.ContentReader;
import dev.bxlab.clipboard.monitor.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Detects clipboard changes via periodic polling.
 * More reliable than OwnershipDetector but has higher latency (up to polling interval).
 * Used as fallback when OwnershipDetector fails.
 */
@Slf4j
@RequiredArgsConstructor
public final class PollingDetector implements ChangeDetector {

    private final Clipboard clipboard;
    private final ScheduledExecutorService scheduler;
    private final Consumer<Transferable> changeHandler;
    private final ContentReader contentReader;
    private final Duration pollingInterval;

    private volatile boolean running = false;
    private volatile String lastHash = "";
    private ScheduledFuture<?> pollingTask;


    @Override
    public void start() {
        if (running) {
            log.debug("PollingDetector already running");
            return;
        }

        running = true;

        try {
            Transferable initial = clipboard.getContents(null);
            lastHash = contentReader.calculateHash(initial);
            log.debug("Initial clipboard hash: {}", LogUtils.truncateHash(lastHash));
        } catch (Exception e) {
            log.warn("Could not get initial clipboard hash: {}", e.getMessage());
            lastHash = "";
        }

        pollingTask = scheduler.scheduleAtFixedRate(
                this::poll,
                pollingInterval.toMillis(),
                pollingInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        log.info("PollingDetector started with interval: {}ms", pollingInterval.toMillis());
    }

    @Override
    public void stop() {
        running = false;

        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }

        log.info("PollingDetector stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Updates last known hash without notifying change.
     * Useful after monitor writes to clipboard.
     *
     * @param hash new hash to remember
     */
    public void updateLastHash(String hash) {
        this.lastHash = hash;
        log.debug("Updated last known hash: {}", LogUtils.truncateHash(hash));
    }

    private void poll() {
        if (!running) {
            return;
        }

        try {
            Transferable current = clipboard.getContents(null);
            String currentHash = contentReader.calculateHash(current);

            if (!currentHash.equals(lastHash)) {
                log.debug("Clipboard change detected via polling. Old: {}, New: {}",
                        LogUtils.truncateHash(lastHash),
                        LogUtils.truncateHash(currentHash));

                lastHash = currentHash;
                changeHandler.accept(current);
            }

        } catch (IllegalStateException e) {
            log.debug("Clipboard busy during poll, will retry next cycle");
        } catch (Exception e) {
            log.error("Error during clipboard poll", e);
        }
    }
}

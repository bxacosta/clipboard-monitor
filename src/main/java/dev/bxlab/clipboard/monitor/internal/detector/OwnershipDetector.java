package dev.bxlab.clipboard.monitor.internal.detector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Detects clipboard changes using ClipboardOwner.
 * <p>
 * Takes clipboard ownership and gets notified when another process takes it.
 * Faster than polling but may fail on some systems.
 */
@Slf4j
@RequiredArgsConstructor
public final class OwnershipDetector implements ChangeDetector, ClipboardOwner {

    private static final long PROCESS_DELAY_MS = 50;

    private final Clipboard clipboard;
    private final ScheduledExecutorService scheduler;
    private final Consumer<Transferable> changeHandler;

    private volatile boolean running = false;

    @Override
    public void start() {
        if (running) {
            log.debug("OwnershipDetector already running");
            return;
        }

        running = true;
        takeOwnership();
        log.info("OwnershipDetector started");
    }

    @Override
    public void stop() {
        running = false;
        log.info("OwnershipDetector stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Called when another process takes clipboard ownership.
     * NOTE: Called from AWT thread, don't block here.
     */
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        if (!running) {
            log.debug("Lost ownership but detector is stopped, ignoring");
            return;
        }

        log.debug("Lost clipboard ownership, scheduling change processing");

        scheduler.schedule(() -> {
            if (!running) {
                return;
            }

            try {
                Transferable newContent = clipboard.getContents(null);
                changeHandler.accept(newContent);
                takeOwnership();
            } catch (Exception e) {
                log.error("Error processing clipboard change after lost ownership", e);
            }
        }, PROCESS_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Re-takes ownership after writing content.
     * Call after setContents() to continue monitoring.
     *
     * @param content content just written
     */
    public void retakeOwnership(Transferable content) {
        if (!running) {
            return;
        }

        try {
            clipboard.setContents(content, this);
            log.debug("Re-took clipboard ownership after write");
        } catch (Exception e) {
            log.warn("Could not re-take clipboard ownership: {}", e.getMessage());
        }
    }

    private void takeOwnership() {
        try {
            Transferable current = clipboard.getContents(null);
            if (current != null) {
                clipboard.setContents(current, this);
                log.debug("Took clipboard ownership");
            } else {
                log.debug("Clipboard is empty, cannot take ownership");
            }
        } catch (IllegalStateException e) {
            log.warn("Could not take clipboard ownership: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error taking clipboard ownership", e);
        }
    }
}

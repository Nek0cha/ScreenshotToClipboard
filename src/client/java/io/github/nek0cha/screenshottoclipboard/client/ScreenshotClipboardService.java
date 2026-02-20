package io.github.nek0cha.screenshottoclipboard.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Java port of ScreenshotClipboardService.kt (reference project).
 * Processes screenshot files off the main thread and copies them to the OS clipboard.
 * A bounded queue (capacity 3) prevents unbounded memory growth.
 */
public final class ScreenshotClipboardService {

    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotToClipboard");

    public static final ScreenshotClipboardService INSTANCE = new ScreenshotClipboardService();

    private record Job(File file, Runnable onSuccess, Runnable onFailure) {}

    private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<>(3);
    private final ThreadPoolExecutor executor;

    private ScreenshotClipboardService() {
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "screenshot-to-clipboard");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });
        executor.execute(this::workerLoop);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                executor.shutdownNow();
                executor.awaitTermination(200, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {}
        }, "screenshot-to-clipboard-shutdown"));
    }

    /**
     * Enqueue {@code screenshotFile} for clipboard copy.
     * {@code onSuccess}/{@code onFailure} are invoked from the worker thread.
     * If the queue is full the oldest entry is dropped.
     */
    public void enqueueFile(File screenshotFile, Runnable onSuccess, Runnable onFailure) {
        Job job = new Job(screenshotFile, onSuccess, onFailure);
        while (!queue.offer(job)) { queue.poll(); }
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Job job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                boolean ok = ClipboardUtil.copyImageToClipboard(job.file().toPath());
                if (ok) {
                    if (job.onSuccess() != null) job.onSuccess().run();
                } else {
                    LOGGER.warn("[ScreenshotToClipboard] Clipboard copy returned false for: {}", job.file());
                    if (job.onFailure() != null) job.onFailure().run();
                }
            } catch (Throwable t) {
                LOGGER.warn("[ScreenshotToClipboard] Failed to copy screenshot to clipboard", t);
                try { if (job.onFailure() != null) job.onFailure().run(); }
                catch (Throwable ignored) {}
            }
        }
    }
}
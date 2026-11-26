package com.fileguard.ui;

import com.fileguard.core.ProgressListener;
import com.fileguard.model.ScanReport;
import javafx.concurrent.Task;

/**
 * Runs a scan on a background thread so the window never freezes.
 *
 * The task also acts as the {@link ProgressListener} for the scanning code:
 * progress and messages are forwarded to JavaFX, which delivers them safely
 * to the UI thread, and {@code isCancelled()} becomes true when the user
 * presses Cancel.
 */
public class ScanTask extends Task<ScanReport> implements ProgressListener {

    /** The actual scan to run, e.g. {@code p -> scanService.findDuplicates(folder, p)}. */
    @FunctionalInterface
    public interface Work {
        ScanReport run(ProgressListener progress) throws Exception;
    }

    private final Work work;

    public ScanTask(Work work) {
        this.work = work;
    }

    @Override
    protected ScanReport call() throws Exception {
        return work.run(this);
    }

    @Override
    public void updateProgress(double fraction) {
        if (fraction < 0) {
            updateProgress(-1, 1); // indeterminate (animated) progress bar
        } else {
            updateProgress(fraction, 1.0);
        }
    }

    /** Task.updateMessage is protected; this override only makes it public for ProgressListener. */
    @Override
    public void updateMessage(String message) {
        super.updateMessage(message);
    }

    /** Starts the task on a daemon thread, so an unfinished scan never blocks closing the app. */
    public void start() {
        Thread thread = new Thread(this, "fileguard-scan");
        thread.setDaemon(true);
        thread.start();
    }
}

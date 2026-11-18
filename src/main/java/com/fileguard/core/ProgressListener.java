package com.fileguard.core;

import java.util.concurrent.CancellationException;

/**
 * Lets the scanning code report progress and check for cancellation without
 * knowing anything about JavaFX. The UI implements this on top of a JavaFX Task;
 * unit tests can use {@link #NONE} or a simple lambda-based implementation.
 */
public interface ProgressListener {

    /** A listener that ignores progress and is never cancelled. */
    ProgressListener NONE = new ProgressListener() {
        @Override public void updateProgress(double fraction) { }
        @Override public void updateMessage(String message) { }
        @Override public boolean isCancelled() { return false; }
    };

    /** @param fraction value between 0 and 1, or a negative value for "unknown" */
    void updateProgress(double fraction);

    void updateMessage(String message);

    boolean isCancelled();

    /** Throws {@link CancellationException} if the user pressed Cancel. */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Scan cancelled by user");
        }
    }
}

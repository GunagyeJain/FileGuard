package com.fileguard.core;

import java.util.concurrent.atomic.AtomicInteger;

/** Progress listeners used by the tests. */
public final class TestListeners {

    private TestListeners() {
    }

    /** Already cancelled before the work starts. */
    public static ProgressListener cancelled() {
        return cancelAfter(0);
    }

    /** Becomes cancelled after the given number of progress updates (simulates pressing Cancel mid-scan). */
    public static ProgressListener cancelAfter(int updates) {
        AtomicInteger count = new AtomicInteger();
        return new ProgressListener() {
            @Override public void updateProgress(double fraction) { count.incrementAndGet(); }
            @Override public void updateMessage(String message) { }
            @Override public boolean isCancelled() { return count.get() >= updates; }
        };
    }
}

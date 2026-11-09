package dev.junxiex.mikudyview.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 空闲堆监测：取最近一分钟内的"最低已用堆"作为活集估计。
 * 单次采样正好落在 GC 前后差别巨大，低水位才是这次判断想要的数。
 */
public final class MemoryMonitor {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long MIN_GAP_MILLIS = 1_000L;

    private final Deque<long[]> samples = new ArrayDeque<>();

    public synchronized void sample() {
        Runtime runtime = Runtime.getRuntime();
        if (runtime.maxMemory() <= 0) {
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        long used = runtime.totalMemory() - runtime.freeMemory();

        long[] newest = samples.peekLast();
        if (newest != null && now < newest[0]) {
            samples.clear();
            newest = null;
        }
        if (newest == null || now - newest[0] >= MIN_GAP_MILLIS) {
            samples.addLast(new long[]{now, used});
        } else if (used < newest[1]) {
            newest[1] = used;
        }

        long cutoff = now - WINDOW_MILLIS;
        while (!samples.isEmpty() && samples.peekFirst()[0] < cutoff) {
            samples.removeFirst();
        }
    }

    public synchronized double freePercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0) {
            return 100.0D;
        }
        long low = Long.MAX_VALUE;
        for (long[] sample : samples) {
            low = Math.min(low, sample[1]);
        }
        if (low == Long.MAX_VALUE) {
            low = runtime.totalMemory() - runtime.freeMemory();
        }
        return Math.max(0.0D, Math.min(100.0D, (max - low) * 100.0D / max));
    }

    public synchronized void clear() {
        samples.clear();
    }
}

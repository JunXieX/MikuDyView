package dev.junxiex.mikudyview.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.bukkit.Bukkit;

/** 读取 Paper 的 tick 耗时，并在自己的窗口里取中位数。 */
public final class MsptSampler {

    private final Deque<Double> samples = new ArrayDeque<>();
    private final int window;

    public MsptSampler(int window) {
        this.window = Math.max(window, 1);
    }

    /** 每次检查调用一次：把 Paper 最近 100 tick 的均值压进窗口。 */
    public synchronized void sample() {
        try {
            long[] times = Bukkit.getServer().getTickTimes();
            if (times == null || times.length == 0) {
                return;
            }
            double sum = 0.0D;
            for (long time : times) {
                sum += time / 1_000_000.0D;
            }
            samples.addLast(sum / times.length);
            while (samples.size() > window) {
                samples.removeFirst();
            }
        } catch (Throwable ignored) {
            // 服务端不提供该 API 时保持空窗口，读侧会回落到 getAverageTickTime。
        }
    }

    public synchronized double mspt() {
        if (!samples.isEmpty()) {
            List<Double> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            return sorted.get(sorted.size() / 2);
        }
        try {
            return Bukkit.getServer().getAverageTickTime();
        } catch (Throwable ignored) {
            return 50.0D;
        }
    }

    public synchronized void clear() {
        samples.clear();
    }
}

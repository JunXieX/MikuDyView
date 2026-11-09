package dev.junxiex.mikudyview.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 最近若干次真正写进世界的变更，供 /mvd history 使用。 */
public final class HistoryRing {

    public record Entry(long time, String world, int oldSim, int newSim,
                        int oldView, int newView, String reason) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    public HistoryRing(int capacity) {
        this.capacity = Math.max(capacity, 1);
    }

    public synchronized void add(Entry entry) {
        if (entries.size() >= capacity) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    /** 最新的在前。 */
    public synchronized List<Entry> recent(int limit) {
        List<Entry> all = new ArrayList<>(entries);
        List<Entry> out = new ArrayList<>(Math.min(limit, all.size()));
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            out.add(all.get(i));
        }
        return out;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}

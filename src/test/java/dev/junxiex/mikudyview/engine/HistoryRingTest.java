package dev.junxiex.mikudyview.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryRingTest {

    @Test
    void keepsOnlyCapacityAndReturnsNewestFirst() {
        HistoryRing ring = new HistoryRing(20);
        for (int i = 0; i < 25; i++) {
            ring.add(new HistoryRing.Entry(i, "world", 8, 9, 10, 11, "r" + i));
        }
        assertEquals(20, ring.size());
        List<HistoryRing.Entry> recent = ring.recent(20);
        assertEquals(20, recent.size());
        assertEquals("r24", recent.get(0).reason());
        assertEquals("r5", recent.get(19).reason());
    }

    @Test
    void limitIsRespected() {
        HistoryRing ring = new HistoryRing(20);
        ring.add(new HistoryRing.Entry(1, "world", 8, 9, 10, 11, "a"));
        ring.add(new HistoryRing.Entry(2, "world", 9, 8, 11, 10, "b"));
        assertEquals(1, ring.recent(1).size());
        assertEquals("b", ring.recent(1).get(0).reason());
    }
}

package dev.junxiex.mikudyview.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkMathTest {

    @Test
    void tickingChunksMatchesBudgetFormula() {
        assertEquals(5780, ChunkMath.tickingChunks(8, 20));
        assertEquals(0, ChunkMath.tickingChunks(8, 0));
    }

    @Test
    void nonTickingChunksExcludesSimulationSquare() {
        assertEquals(6720, ChunkMath.nonTickingChunks(12, 8, 20));
        assertEquals(0, ChunkMath.nonTickingChunks(6, 8, 20));
    }

    @Test
    void solveRoundTrips() {
        assertEquals(8, ChunkMath.solveSimulation(5780, 20));
        assertEquals(12, ChunkMath.solveView(6720, 20, 8));
    }

    @Test
    void unionAreaDeduplicatesOverlap() {
        // 0..3 x 0..3 and 2..5 x 0..3 -> 5 x 3
        assertEquals(15, ChunkMath.unionArea(List.of(
                new int[]{0, 0, 3, 3},
                new int[]{2, 0, 5, 3})));
    }

    @Test
    void unionAreaHandlesDisjointAndNested() {
        assertEquals(8, ChunkMath.unionArea(List.of(
                new int[]{0, 0, 2, 2},
                new int[]{10, 10, 12, 12})));
        assertEquals(9, ChunkMath.unionArea(List.of(
                new int[]{0, 0, 3, 3},
                new int[]{1, 1, 2, 2})));
    }

    @Test
    void unionAreaOfClusteredPlayersStaysSmall() {
        // 20 名玩家分散在 5x5 区块内，每个覆盖 17x17：并集 = (4 + 17)^2
        int area = ChunkMath.unionArea(List.of(
                new int[]{0, 0, 17, 17},
                new int[]{4, 0, 21, 17},
                new int[]{0, 4, 17, 21},
                new int[]{4, 4, 21, 21}));
        assertEquals(441, area);
    }
}

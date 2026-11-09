package dev.junxiex.mikudyview.engine;

import java.util.ArrayList;
import java.util.List;

/** 区块数量与距离之间的换算，全部是纯函数。 */
public final class ChunkMath {

    private ChunkMath() {
    }

    public static int tickingChunks(int simulationDistance, int players) {
        if (players <= 0) {
            return 0;
        }
        return square(2 * simulationDistance + 1) * players;
    }

    public static int nonTickingChunks(int viewDistance, int simulationDistance, int players) {
        if (players <= 0) {
            return 0;
        }
        int outer = square(2 * viewDistance + 1);
        int inner = square(2 * Math.min(viewDistance, simulationDistance) + 1);
        return Math.max(outer - inner, 0) * players;
    }

    /** (2d+1)^2 * players = allowed  =>  d = (sqrt(allowed/players) - 1) / 2 */
    public static int solveSimulation(int allowedChunks, int players) {
        if (players <= 0 || allowedChunks <= 0) {
            return 0;
        }
        double d = (Math.sqrt((double) allowedChunks / players) - 1.0D) / 2.0D;
        return (int) Math.floor(d);
    }

    /** ((2v+1)^2 - (2s+1)^2) * players = allowed  =>  v = (sqrt(allowed/players + (2s+1)^2) - 1) / 2 */
    public static int solveView(int allowedChunks, int players, int simulationDistance) {
        if (players <= 0) {
            return simulationDistance;
        }
        double inner = 2.0D * simulationDistance + 1.0D;
        double rhs = (double) Math.max(allowedChunks, 0) / players + inner * inner;
        return (int) Math.floor((Math.sqrt(rhs) - 1.0D) / 2.0D);
    }

    /**
     * 一组轴对齐矩形（区块坐标）的并集面积。
     * 每个矩形是 {x0, y0, x1, y1}，按先后顺序加入并扣除与已有部分的交集。
     */
    public static int unionArea(List<int[]> rectangles) {
        List<int[]> placed = new ArrayList<>(rectangles.size());
        long area = 0L;
        for (int[] rectangle : rectangles) {
            int[] adding = normalize(rectangle);
            List<int[]> pieces = new ArrayList<>();
            pieces.add(adding);
            for (int[] existing : placed) {
                List<int[]> next = new ArrayList<>(pieces.size());
                for (int[] piece : pieces) {
                    subtract(piece, existing, next);
                }
                pieces = next;
                if (pieces.isEmpty()) {
                    break;
                }
            }
            for (int[] piece : pieces) {
                area += (long) (piece[2] - piece[0]) * (piece[3] - piece[1]);
            }
            placed.add(adding);
        }
        return (int) Math.min(area, Integer.MAX_VALUE);
    }

    private static int[] normalize(int[] r) {
        return new int[]{
                Math.min(r[0], r[2]), Math.min(r[1], r[3]),
                Math.max(r[0], r[2]), Math.max(r[1], r[3])
        };
    }

    /** 把 piece - other 的差集（最多四块）写入 out。 */
    private static void subtract(int[] piece, int[] other, List<int[]> out) {
        if (piece[2] <= other[0] || piece[0] >= other[2] || piece[3] <= other[1] || piece[1] >= other[3]) {
            out.add(piece);
            return;
        }
        // 左
        if (piece[0] < other[0]) {
            out.add(new int[]{piece[0], piece[1], other[0], piece[3]});
        }
        // 右
        if (other[2] < piece[2]) {
            out.add(new int[]{other[2], piece[1], piece[2], piece[3]});
        }
        // 上（受 other 的 x 范围限制）
        int x0 = Math.max(piece[0], other[0]);
        int x1 = Math.min(piece[2], other[2]);
        if (piece[1] < other[1] && x0 < x1) {
            out.add(new int[]{x0, piece[1], x1, other[1]});
        }
        if (other[3] < piece[3] && x0 < x1) {
            out.add(new int[]{x0, other[3], x1, piece[3]});
        }
    }

    private static int square(int value) {
        return value * value;
    }
}

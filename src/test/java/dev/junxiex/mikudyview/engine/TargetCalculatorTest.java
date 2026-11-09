package dev.junxiex.mikudyview.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.junxiex.mikudyview.config.Settings;
import dev.junxiex.mikudyview.config.WorldSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TargetCalculatorTest {

    private final TargetCalculator calculator = new TargetCalculator();

    private static Settings settings(boolean clientCap, int headroom) {
        return new Settings(true, "mixed", 200, 0, 40.0D, 47.0D, 2, 4, 4, 1, 200, true,
                true, 5, true, 15.0D, clientCap, headroom, false, "mikudyview.fixed.",
                false, 5, 6, 5780, 6720, true, Map.of("default", WorldSettings.defaults()));
    }

    private static Snapshot snapshot(double mspt, int players) {
        return new Snapshot(mspt, 80.0D, 1.0D, 1, players,
                Map.of("world", players), Map.of(), 0L);
    }

    @Test
    void growthIsCappedByBudgetAndStep() {
        Settings settings = settings(false, 1);
        WorldSettings world = WorldSettings.defaults();
        Target target = calculator.compute(settings, world, 6, 8, 20, 0, snapshot(20.0D, 20));
        // 预算解出 8/12，MSPT 允许 +2，取两者较小值
        assertEquals(8, target.sim());
        assertEquals(10, target.view());
    }

    @Test
    void oversizedBudgetDropIsStepLimited() {
        Settings settings = new Settings(true, "mixed", 200, 0, 40.0D, 47.0D, 2, 4, 4, 1, 200, true,
                true, 5, true, 15.0D, false, 1, false, "mikudyview.fixed.",
                false, 5, 6, 3000, 1000, true, Map.of("default", WorldSettings.defaults()));
        WorldSettings wide = new WorldSettings(false, 4, 12, 4, 16, 1.0D, 0);
        Target target = calculator.compute(settings, wide, 12, 16, 20, 0, snapshot(20.0D, 20));
        // 预算只允许 5，但单轮最多降 4 格
        assertEquals(8, target.sim());
        assertTrue(target.reasons().contains(Target.Reason.STEP));
    }

    @Test
    void overloadShrinksFasterThanItGrows() {
        Settings settings = settings(false, 1);
        Target target = calculator.compute(settings, WorldSettings.defaults(), 10, 12, 20, 0, snapshot(62.0D, 20));
        // 超出阈值 15ms -> 1 + 3 = 4 格，正好等于 max-decrease-step
        assertEquals(6, target.sim());
        assertEquals(8, target.view());
    }

    @Test
    void viewNeverBelowSimulation() {
        Settings settings = settings(false, 1);
        WorldSettings odd = new WorldSettings(false, 8, 8, 2, 6, 1.0D, 0);
        Target target = calculator.compute(settings, odd, 8, 6, 20, 0, snapshot(20.0D, 20));
        assertTrue(target.view() >= target.sim());
    }

    @Test
    void clientCapLowersViewOnly() {
        Settings settings = settings(true, 1);
        Target target = calculator.compute(settings, WorldSettings.defaults(), 6, 12, 20, 6, snapshot(20.0D, 20));
        assertEquals(7, target.view());
        assertTrue(target.sim() <= target.view());
        assertTrue(target.reasons().contains(Target.Reason.CLIENT_CAP));
    }

    @Test
    void excludedWorldIsUntouched() {
        Settings settings = settings(false, 1);
        WorldSettings excluded = new WorldSettings(true, 4, 8, 4, 12, 1.0D, 0);
        Target target = calculator.compute(settings, excluded, 7, 9, 20, 0, snapshot(20.0D, 20));
        assertEquals(7, target.sim());
        assertEquals(9, target.view());
    }

    @Test
    void emptyWorldFallsToMinimum() {
        Settings settings = settings(false, 1);
        Target target = calculator.compute(settings, WorldSettings.defaults(), 8, 12, 0, 0, snapshot(20.0D, 0));
        assertEquals(4, target.sim());
        // 空世界目标是最小值，但仍受单轮降幅限制
        assertEquals(8, target.view());
    }
}

package dev.junxiex.mikudyview.engine;

import dev.junxiex.mikudyview.config.Settings;
import dev.junxiex.mikudyview.config.WorldSettings;
import java.util.EnumSet;

/**
 * 纯计算：给定世界当前状态与一次全局快照，算出这一轮的目标距离。
 * 不读 Bukkit、不写世界、不改计数器——所有副作用在 DistanceEngine 里。
 */
public final class TargetCalculator {

    public Target compute(Settings settings,
                          WorldSettings world,
                          int currentSim,
                          int currentView,
                          int players,
                          int clientMaxView,
                          Snapshot snapshot) {

        EnumSet<Target.Reason> reasons = EnumSet.noneOf(Target.Reason.class);
        if (world.exclude()) {
            return Target.of(currentSim, currentView, reasons);
        }

        int budgetSim;
        int budgetView;
        if (players <= 0) {
            budgetSim = world.minSim();
            budgetView = Math.max(world.minView(), budgetSim);
            reasons.add(Target.Reason.EMPTY);
        } else {
            double weightSum = Math.max(snapshot.weightSum(), 1.0e-4D);
            double share = Math.max(world.weight(), 0.0D) / weightSum;
            int allowedTicking = (int) Math.round(settings.tickingBudget() * share);
            budgetSim = clamp(ChunkMath.solveSimulation(allowedTicking, players), world.minSim(), world.maxSim());

            int allowedNonTicking = (int) Math.round(settings.nonTickingBudget() * share);
            budgetView = clamp(ChunkMath.solveView(allowedNonTicking, players, budgetSim), world.minView(), world.maxView());
            if (budgetView < budgetSim) {
                budgetView = Math.min(budgetSim, world.maxView());
            }
        }

        int msptSim = reactive(currentSim, world.minSim(), world.maxSim(), snapshot.mspt(), settings);
        int msptView = reactive(currentView, world.minView(), world.maxView(), snapshot.mspt(), settings);

        int sim = Math.min(budgetSim, msptSim);
        int view = Math.min(budgetView, msptView);

        if (players > 0 && (sim == budgetSim || view == budgetView)) {
            reasons.add(Target.Reason.BUDGET);
        }
        if (snapshot.mspt() >= settings.decreaseMspt() || snapshot.mspt() <= settings.increaseMspt()) {
            if (sim == msptSim || view == msptView) {
                reasons.add(Target.Reason.MSPT);
            }
        }

        int steppedSim = clampStep(currentSim, sim, settings.maxIncreaseStep(), settings.maxDecreaseStep());
        int steppedView = clampStep(currentView, view, settings.maxIncreaseStep(), settings.maxDecreaseStep());
        if (steppedSim != sim || steppedView != view) {
            reasons.add(Target.Reason.STEP);
        }

        int limitedSim = clamp(steppedSim, world.minSim(), world.maxSim());
        int limitedView = clamp(steppedView, world.minView(), world.maxView());
        if (limitedSim != steppedSim || limitedView != steppedView) {
            reasons.add(Target.Reason.LIMIT);
        }

        if (limitedView < limitedSim) {
            limitedView = Math.min(limitedSim, world.maxView());
            limitedSim = Math.min(limitedSim, limitedView);
        }

        if (settings.clientViewCap() && clientMaxView >= 2) {
            int cap = Math.max(world.minView(), clientMaxView + settings.clientViewCapHeadroom());
            if (cap < limitedView) {
                limitedView = cap;
                limitedSim = Math.min(limitedSim, limitedView);
                reasons.add(Target.Reason.CLIENT_CAP);
            }
        }

        return Target.of(limitedSim, limitedView, reasons);
    }

    /** 按超出阈值的幅度放大步长；每次至少一格，最多 maxStep。 */
    private static int reactive(int current, int min, int max, double mspt, Settings settings) {
        if (mspt >= settings.decreaseMspt() && current > min) {
            double over = mspt - settings.decreaseMspt();
            int step = Math.min(1 + (int) Math.floor(over / 5.0D), settings.maxDecreaseStep());
            return Math.max(current - step, min);
        }
        if (mspt <= settings.increaseMspt() && current < max) {
            double under = settings.increaseMspt() - mspt;
            int step = Math.min(1 + (int) Math.floor(under / 5.0D), settings.maxIncreaseStep());
            return Math.min(current + step, max);
        }
        return current;
    }

    private static int clampStep(int current, int target, int maxIncrease, int maxDecrease) {
        if (target > current) {
            return Math.min(target, current + maxIncrease);
        }
        if (target < current) {
            return Math.max(target, current - maxDecrease);
        }
        return target;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

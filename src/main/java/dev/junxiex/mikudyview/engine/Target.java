package dev.junxiex.mikudyview.engine;

import java.util.EnumSet;
import java.util.Set;

/** 一次计算得出的目标距离，以及它被哪些规则影响过。 */
public record Target(int sim, int view, Set<Reason> reasons) {

    public enum Reason {
        /** 由区块预算决定 */
        BUDGET,
        /** 由 MSPT 决定 */
        MSPT,
        /** 被单次步长夹住 */
        STEP,
        /** 被世界上下限夹住 */
        LIMIT,
        /** 被客户端视距封顶 */
        CLIENT_CAP,
        /** 世界没有玩家 */
        EMPTY
    }

    public static Target of(int sim, int view, EnumSet<Reason> reasons) {
        return new Target(sim, view, Set.copyOf(reasons));
    }

    public String reasonText() {
        if (reasons.isEmpty()) {
            return "无变化";
        }
        StringBuilder text = new StringBuilder();
        for (Reason reason : reasons) {
            if (!text.isEmpty()) {
                text.append(" + ");
            }
            text.append(switch (reason) {
                case BUDGET -> "区块预算";
                case MSPT -> "MSPT";
                case STEP -> "步长上限";
                case LIMIT -> "世界上下限";
                case CLIENT_CAP -> "客户端视距";
                case EMPTY -> "空世界";
            });
        }
        return text.toString();
    }
}

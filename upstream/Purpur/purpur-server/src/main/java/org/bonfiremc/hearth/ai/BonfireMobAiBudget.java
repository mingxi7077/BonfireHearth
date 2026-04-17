package org.bonfiremc.hearth.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.bonfiremc.hearth.tracking.BonfireEntityTrackingBudget;
import org.purpurmc.purpur.PurpurWorldConfig;

public final class BonfireMobAiBudget {
    private static final int BASE_FULL_GOAL_SELECTOR_INTERVAL = 2;

    private BonfireMobAiBudget() {
    }

    public static int fullGoalSelectorInterval(final Mob mob, final ServerLevel level) {
        final PurpurWorldConfig config = level.purpurConfig;
        if (!config.bonfireMobAiBudgetEnabled || !isBudgetable(mob)) {
            return BASE_FULL_GOAL_SELECTOR_INTERVAL;
        }

        return switch (BonfireEntityTrackingBudget.pressureState(level)) {
            case NORMAL -> BASE_FULL_GOAL_SELECTOR_INTERVAL;
            case HIGH -> Math.max(BASE_FULL_GOAL_SELECTOR_INTERVAL, config.bonfireMobAiBudgetHighPressureGoalSelectorInterval);
            case EXTREME -> Math.max(BASE_FULL_GOAL_SELECTOR_INTERVAL, config.bonfireMobAiBudgetExtremePressureGoalSelectorInterval);
        };
    }

    private static boolean isBudgetable(final Mob mob) {
        return mob.getTarget() == null
            && mob.getLastHurtByMob() == null
            && mob.hurtTime <= 0
            && !mob.isPassenger()
            && !mob.isVehicle()
            && !mob.isLeashed();
    }
}

package org.bonfiremc.hearth.tracking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bonfiremc.hearth.network.BonfireNetworkPressure;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.purpurmc.purpur.PurpurWorldConfig;

public final class BonfireEntityTrackingBudget {
    private BonfireEntityTrackingBudget() {
    }

    public static boolean shouldSendChanges(final ServerLevel level, final Entity entity, final int trackedPlayerCount) {
        final int interval = trackingInterval(level, entity, trackedPlayerCount);
        return shouldRespectInterval(entity, interval, 0);
    }

    public static int trackingInterval(final ServerLevel level, final Entity entity, final int trackedPlayerCount) {
        final PurpurWorldConfig config = level.purpurConfig;
        if (!config.bonfireEntityTrackingBudgetEnabled || trackedPlayerCount <= 0 || !isLowPriorityVisualEntity(config, entity)) {
            return 1;
        }

        return switch (pressureState(level, trackedPlayerCount)) {
            case NORMAL -> 1;
            case HIGH -> Math.max(1, config.bonfireEntityTrackingBudgetHighPressureInterval);
            case EXTREME -> Math.max(1, config.bonfireEntityTrackingBudgetExtremePressureInterval);
        };
    }

    public static BonfirePressureState pressureState(final ServerLevel level) {
        return pressureState(level, 0);
    }

    public static BonfirePressureState pressureState(final ServerLevel level, final int trackedPlayerCount) {
        final PurpurWorldConfig config = level.purpurConfig;
        final long averageTickTimeNanos = level.getServer().getAverageTickTimeNanos();
        final long extremePressureNanos = nanosFromMspt(config.bonfireEntityTrackingBudgetExtremePressureMspt);
        if (averageTickTimeNanos >= extremePressureNanos) {
            final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(level.getServer());
            return startupFloor.ordinal() > BonfirePressureState.EXTREME.ordinal() ? startupFloor : BonfirePressureState.EXTREME;
        }

        final long highPressureNanos = nanosFromMspt(config.bonfireEntityTrackingBudgetHighPressureMspt);
        if (averageTickTimeNanos >= highPressureNanos
            || trackedPlayerCount >= config.bonfireEntityTrackingBudgetCrowdTrackedPlayers
            || BonfireNetworkPressure.pressureState(level) != BonfirePressureState.NORMAL) {
            final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(level.getServer());
            return startupFloor.ordinal() > BonfirePressureState.HIGH.ordinal() ? startupFloor : BonfirePressureState.HIGH;
        }

        final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(level.getServer());
        return startupFloor.ordinal() > BonfirePressureState.NORMAL.ordinal() ? startupFloor : BonfirePressureState.NORMAL;
    }

    public static double averageMspt(final ServerLevel level) {
        return level.getServer().getAverageTickTimeNanos() / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND;
    }

    private static boolean isLowPriorityVisualEntity(final PurpurWorldConfig config, final Entity entity) {
        if (config.bonfireEntityTrackingBudgetThrottleDisplays && entity instanceof Display) {
            return true;
        }

        if (config.bonfireEntityTrackingBudgetThrottleInteractions && entity instanceof Interaction) {
            return true;
        }

        if (entity instanceof ArmorStand armorStand) {
            if (config.bonfireEntityTrackingBudgetThrottleMarkerArmorStands && armorStand.isMarker()) {
                return true;
            }
            if (config.bonfireEntityTrackingBudgetThrottleInvisibleNoGravityArmorStands && armorStand.isInvisible() && armorStand.isNoGravity()) {
                return true;
            }
        }

        return false;
    }

    private static long nanosFromMspt(final double mspt) {
        return Math.round(Math.max(0.0D, mspt) * TimeUtil.NANOSECONDS_PER_MILLISECOND);
    }

    private static boolean shouldRespectInterval(final Entity entity, final int interval, final int phaseSalt) {
        if (interval <= 1 || entity.tickCount <= 1) {
            return true;
        }
        final int phase = Math.floorMod(entity.getId() + phaseSalt, interval);
        return Math.floorMod(entity.tickCount, interval) == phase;
    }
}

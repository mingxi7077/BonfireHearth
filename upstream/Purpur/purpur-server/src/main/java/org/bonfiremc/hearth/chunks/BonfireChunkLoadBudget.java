package org.bonfiremc.hearth.chunks;

import net.minecraft.server.level.ServerPlayer;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.bonfiremc.hearth.pressure.BonfireServerPressure;
import org.bonfiremc.hearth.tracking.BonfirePressureState;
import org.purpurmc.purpur.PurpurWorldConfig;

public final class BonfireChunkLoadBudget {
    private static final double MIN_RATE_MULTIPLIER = 0.05D;

    private BonfireChunkLoadBudget() {
    }

    public static double loadRate(final ServerPlayer player, final double configuredRate) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        if (!config.bonfireChunkLoadBudgetEnabled) {
            return configuredRate;
        }

        return switch (pressureState(player)) {
            case NORMAL -> configuredRate;
            case HIGH -> clampRate(configuredRate, config.bonfireChunkLoadBudgetHighPressureLoadRateMultiplier);
            case EXTREME -> clampRate(configuredRate, config.bonfireChunkLoadBudgetExtremePressureLoadRateMultiplier);
        };
    }

    public static double genRate(final ServerPlayer player, final double configuredRate) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        if (!config.bonfireChunkLoadBudgetEnabled) {
            return configuredRate;
        }

        return switch (pressureState(player)) {
            case NORMAL -> configuredRate;
            case HIGH -> clampRate(configuredRate, config.bonfireChunkLoadBudgetHighPressureGenRateMultiplier);
            case EXTREME -> clampRate(configuredRate, config.bonfireChunkLoadBudgetExtremePressureGenRateMultiplier);
        };
    }

    public static long maxConcurrentLoads(final ServerPlayer player, final long configuredLimit) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        if (!config.bonfireChunkLoadBudgetEnabled) {
            return configuredLimit;
        }

        return switch (pressureState(player)) {
            case NORMAL -> configuredLimit;
            case HIGH -> clampConcurrency(configuredLimit, config.bonfireChunkLoadBudgetHighPressureMaxConcurrentLoads);
            case EXTREME -> clampConcurrency(configuredLimit, config.bonfireChunkLoadBudgetExtremePressureMaxConcurrentLoads);
        };
    }

    public static long maxConcurrentGenerates(final ServerPlayer player, final long configuredLimit) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        if (!config.bonfireChunkLoadBudgetEnabled) {
            return configuredLimit;
        }

        return switch (pressureState(player)) {
            case NORMAL -> configuredLimit;
            case HIGH -> clampConcurrency(configuredLimit, config.bonfireChunkLoadBudgetHighPressureMaxConcurrentGenerates);
            case EXTREME -> clampConcurrency(configuredLimit, config.bonfireChunkLoadBudgetExtremePressureMaxConcurrentGenerates);
        };
    }

    private static BonfirePressureState pressureState(final ServerPlayer player) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        final BonfirePressureState tickPressure = BonfireServerPressure.pressureState(
            config.bonfireChunkLoadBudgetHighPressureMspt,
            config.bonfireChunkLoadBudgetExtremePressureMspt
        );
        final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(player.level().getServer());
        return startupFloor.ordinal() > tickPressure.ordinal() ? startupFloor : tickPressure;
    }

    private static double clampRate(final double configuredRate, final double configuredMultiplier) {
        if (!(configuredRate > 0.0D)) {
            return configuredRate;
        }

        final double multiplier = Math.max(MIN_RATE_MULTIPLIER, Math.min(1.0D, configuredMultiplier));
        return Math.max(1.0D, Math.min(configuredRate, configuredRate * multiplier));
    }

    private static long clampConcurrency(final long configuredLimit, final int pressureLimit) {
        if (configuredLimit <= 0L) {
            return Math.max(0L, configuredLimit);
        }

        return Math.max(0L, Math.min(configuredLimit, Math.max(1L, (long)pressureLimit)));
    }
}

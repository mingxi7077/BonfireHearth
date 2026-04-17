package org.bonfiremc.hearth.chunks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.bonfiremc.hearth.network.BonfireNetworkPressure;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.bonfiremc.hearth.pressure.BonfireServerPressure;
import org.bonfiremc.hearth.tracking.BonfirePressureState;
import org.purpurmc.purpur.PurpurWorldConfig;

public final class BonfireChunkSendBudget {
    private BonfireChunkSendBudget() {
    }

    public static float desiredChunksPerTick(final ServerPlayer player, final float desiredChunksPerTick) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        if (!config.bonfireChunkSendBudgetEnabled) {
            return desiredChunksPerTick;
        }

        return switch (pressureState(player)) {
            case NORMAL -> desiredChunksPerTick;
            case HIGH -> clampDesired(desiredChunksPerTick, config.bonfireChunkSendBudgetHighPressureMaxChunksPerTick);
            case EXTREME -> clampDesired(desiredChunksPerTick, config.bonfireChunkSendBudgetExtremePressureMaxChunksPerTick);
        };
    }

    public static int maxUnacknowledgedBatches(final ServerPlayer player, final int configuredMaxUnacknowledgedBatches) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        if (!config.bonfireChunkSendBudgetEnabled) {
            return configuredMaxUnacknowledgedBatches;
        }

        return switch (pressureState(player)) {
            case NORMAL -> configuredMaxUnacknowledgedBatches;
            case HIGH -> Math.max(1, Math.min(configuredMaxUnacknowledgedBatches, config.bonfireChunkSendBudgetHighPressureMaxUnacknowledgedBatches));
            case EXTREME -> Math.max(1, Math.min(configuredMaxUnacknowledgedBatches, config.bonfireChunkSendBudgetExtremePressureMaxUnacknowledgedBatches));
        };
    }

    private static BonfirePressureState pressureState(final ServerPlayer player) {
        final PurpurWorldConfig config = player.level().purpurConfig;
        final BonfirePressureState tickPressure = BonfireServerPressure.pressureState(
            config.bonfireChunkSendBudgetHighPressureMspt,
            config.bonfireChunkSendBudgetExtremePressureMspt
        );
        final BonfirePressureState networkPressure = BonfireNetworkPressure.effectiveThrottlePressureState(player);
        BonfirePressureState state = tickPressure.ordinal() >= networkPressure.ordinal() ? tickPressure : networkPressure;
        final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(player.level().getServer());
        if (startupFloor.ordinal() > state.ordinal()) {
            state = startupFloor;
        }
        return state;
    }

    private static float clampDesired(final float desiredChunksPerTick, final double maxChunksPerTick) {
        return Mth.clamp((float)Math.min(desiredChunksPerTick, maxChunksPerTick), 1.0F, 64.0F);
    }
}

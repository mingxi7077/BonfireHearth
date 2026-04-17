package org.bonfiremc.hearth.pressure;

import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import org.bonfiremc.hearth.tracking.BonfirePressureState;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfireStartupGuard {
    private static final int UNINITIALIZED_TICK = Integer.MIN_VALUE;

    private static volatile int serverReadyTick = UNINITIALIZED_TICK;
    private static volatile int firstPlayerJoinTick = UNINITIALIZED_TICK;

    private BonfireStartupGuard() {
    }

    public static void markServerReady(final int currentTick) {
        if (!PurpurConfig.bonfireStartupGuardEnabled || serverReadyTick != UNINITIALIZED_TICK) {
            return;
        }
        serverReadyTick = Math.max(0, currentTick);
    }

    public static void tick(final MinecraftServer server) {
        if (!PurpurConfig.bonfireStartupGuardEnabled || server == null || serverReadyTick == UNINITIALIZED_TICK) {
            return;
        }
        if (firstPlayerJoinTick == UNINITIALIZED_TICK && !server.getPlayerList().getPlayers().isEmpty()) {
            firstPlayerJoinTick = server.getTickCount();
        }
    }

    public static boolean isActive(final MinecraftServer server) {
        if (!PurpurConfig.bonfireStartupGuardEnabled || server == null || serverReadyTick == UNINITIALIZED_TICK) {
            return false;
        }
        return server.getTickCount() - serverReadyTick < Math.max(0, PurpurConfig.bonfireStartupGuardWarmupTicks);
    }

    public static boolean isJoinProtected(final MinecraftServer server) {
        if (!PurpurConfig.bonfireStartupGuardEnabled || server == null || firstPlayerJoinTick == UNINITIALIZED_TICK) {
            return false;
        }
        return server.getTickCount() - firstPlayerJoinTick < Math.max(0, PurpurConfig.bonfireStartupGuardJoinProtectTicks);
    }

    public static BonfirePressureState pressureFloor(final MinecraftServer server) {
        if (!isActive(server)) {
            return BonfirePressureState.NORMAL;
        }
        return isJoinProtected(server) ? BonfirePressureState.EXTREME : BonfirePressureState.HIGH;
    }

    public static String phase(final MinecraftServer server) {
        if (!PurpurConfig.bonfireStartupGuardEnabled) {
            return "disabled";
        }
        if (server == null || serverReadyTick == UNINITIALIZED_TICK) {
            return "not-ready";
        }
        if (!isActive(server)) {
            return "inactive";
        }
        return isJoinProtected(server) ? "join-protect" : "warmup";
    }

    public static int remainingWarmupTicks(final MinecraftServer server) {
        if (!PurpurConfig.bonfireStartupGuardEnabled || server == null || serverReadyTick == UNINITIALIZED_TICK) {
            return 0;
        }
        return Math.max(0, PurpurConfig.bonfireStartupGuardWarmupTicks - (server.getTickCount() - serverReadyTick));
    }

    public static long syncTaskBudgetNanos() {
        return nanosFromMs(PurpurConfig.bonfireStartupGuardSyncTaskBudgetMs);
    }

    public static long cleanupBudgetNanos() {
        return nanosFromMs(PurpurConfig.bonfireStartupGuardCleanupBudgetMs);
    }

    public static int cleanupMaxEntitiesPerTick() {
        return Math.max(1, PurpurConfig.bonfireVanillaSpawningCleanupMaxEntitiesPerTick);
    }

    public static int cleanupMaxEntitiesPerChunkLoad() {
        return Math.max(1, PurpurConfig.bonfireVanillaSpawningCleanupMaxEntitiesPerChunkLoad);
    }

    public static String startupSummary(final MinecraftServer server) {
        return String.format(
            Locale.ROOT,
            "startup-guard=%s phase=%s warmup-remaining-ticks=%d sync-budget-ms=%.1f cleanup-budget-ms=%.1f",
            PurpurConfig.bonfireStartupGuardEnabled,
            phase(server),
            remainingWarmupTicks(server),
            PurpurConfig.bonfireStartupGuardSyncTaskBudgetMs,
            PurpurConfig.bonfireStartupGuardCleanupBudgetMs
        );
    }

    private static long nanosFromMs(final double ms) {
        return Math.round(Math.max(0.0D, ms) * 1_000_000.0D);
    }
}

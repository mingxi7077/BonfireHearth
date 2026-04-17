package org.bonfiremc.hearth.scheduler;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.bonfiremc.hearth.pressure.BonfireServerPressure;
import org.bonfiremc.hearth.tracking.BonfirePressureState;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfireSyncTaskBudget {
    private static final Map<Integer, Integer> LAST_DEFERRED_TICK_BY_TASK = new HashMap<>();
    private static final Map<String, Integer> NEXT_TASK_WARN_TICK_BY_PLUGIN = new HashMap<>();
    private static final Map<String, Long> CURRENT_TICK_RUNTIME_NANOS_BY_PLUGIN = new HashMap<>();
    private static final Map<String, Integer> CURRENT_TICK_DEFERRED_TASKS_BY_PLUGIN = new HashMap<>();
    private static int currentTick = -1;
    private static long currentTickTaskRuntimeNanos;
    private static int currentTickDeferredTasks;
    private static String lastRuntimeSummary = "none";
    private static String lastDeferredSummary = "none";
    private static String lastStartupPhase = "inactive";

    private BonfireSyncTaskBudget() {
    }

    public static void startHeartbeat(final int tick) {
        currentTick = tick;
        currentTickTaskRuntimeNanos = 0L;
        currentTickDeferredTasks = 0;
        CURRENT_TICK_RUNTIME_NANOS_BY_PLUGIN.clear();
        CURRENT_TICK_DEFERRED_TASKS_BY_PLUGIN.clear();
    }

    public static boolean shouldDefer(final Plugin plugin, final int taskId, final long period, final int tick) {
        final net.minecraft.server.MinecraftServer server = Bukkit.getServer() == null ? null : net.minecraft.server.MinecraftServer.getServer();
        final boolean startupGuardActive = BonfireStartupGuard.isActive(server);
        if (!PurpurConfig.bonfireSyncTaskBudgetEnabled && !startupGuardActive) {
            return false;
        }
        if (plugin == null || isExempt(plugin)) {
            return false;
        }

        final boolean repeatingTask = period > 0L;
        if (repeatingTask) {
            if (!PurpurConfig.bonfireSyncTaskBudgetDeferRepeatingTasks) {
                return false;
            }
            if (PurpurConfig.bonfireSyncTaskBudgetDeferOnlyPeriodOne && period != 1L) {
                return false;
            }
        } else if (!(startupGuardActive && PurpurConfig.bonfireStartupGuardDeferOneShotSyncTasks)) {
            return false;
        }

        final BonfirePressureState pressureState = currentPressureState();
        final long tickBudgetNanos;
        if (startupGuardActive) {
            tickBudgetNanos = BonfireStartupGuard.syncTaskBudgetNanos();
        } else if (pressureState == BonfirePressureState.NORMAL) {
            return false;
        } else {
            tickBudgetNanos = switch (pressureState) {
                case HIGH -> nanosFromMs(PurpurConfig.bonfireSyncTaskBudgetHighPressureTickBudgetMs);
                case EXTREME -> nanosFromMs(PurpurConfig.bonfireSyncTaskBudgetExtremePressureTickBudgetMs);
                case NORMAL -> Long.MAX_VALUE;
            };
        }

        if (currentTickTaskRuntimeNanos < tickBudgetNanos) {
            return false;
        }

        final Integer lastDeferredTick = LAST_DEFERRED_TICK_BY_TASK.get(taskId);
        if (lastDeferredTick != null && lastDeferredTick >= tick - 1) {
            return false;
        }

        LAST_DEFERRED_TICK_BY_TASK.put(taskId, tick);
        currentTickDeferredTasks++;
        CURRENT_TICK_DEFERRED_TASKS_BY_PLUGIN.merge(pluginKey(plugin), 1, Integer::sum);
        return true;
    }

    public static void recordExecution(final Plugin plugin, final int taskId, final long period, final Class<?> taskClass, final long runtimeNanos, final int tick) {
        currentTickTaskRuntimeNanos += runtimeNanos;
        CURRENT_TICK_RUNTIME_NANOS_BY_PLUGIN.merge(pluginKey(plugin), runtimeNanos, Long::sum);
        if (!PurpurConfig.bonfireSyncTaskBudgetEnabled || !PurpurConfig.bonfireSyncTaskBudgetLogTaskOverruns || plugin == null) {
            return;
        }

        final long warnTaskNanos = nanosFromMs(PurpurConfig.bonfireSyncTaskBudgetWarnTaskMs);
        if (runtimeNanos < warnTaskNanos) {
            return;
        }

        final String pluginName = plugin.getName();
        final int nextAllowedTick = NEXT_TASK_WARN_TICK_BY_PLUGIN.getOrDefault(pluginName, Integer.MIN_VALUE);
        if (tick < nextAllowedTick) {
            return;
        }

        NEXT_TASK_WARN_TICK_BY_PLUGIN.put(pluginName, tick + Math.max(20, PurpurConfig.bonfireSyncTaskBudgetLogCooldownTicks));
        plugin.getLogger().log(
            Level.WARNING,
            String.format(
                "[BonfireHearth] sync task #%d (%s, period=%d) took %.3f ms on the main thread at pressure=%s",
                taskId,
                taskClass == null ? "unknown" : taskClass.getName(),
                period,
                nanosToMs(runtimeNanos),
                currentPressureState()
            )
        );
    }

    public static void finishHeartbeat(final int tick) {
        if (!PurpurConfig.bonfireSyncTaskBudgetEnabled) {
            return;
        }
        if (tick % Math.max(20, PurpurConfig.bonfireSyncTaskBudgetLogCooldownTicks) != 0) {
            return;
        }

        final double runtimeMs = nanosToMs(currentTickTaskRuntimeNanos);
        final boolean shouldLogDeferrals = PurpurConfig.bonfireSyncTaskBudgetLogDeferrals && currentTickDeferredTasks > 0;
        final boolean shouldLogTopPlugins = PurpurConfig.bonfireSyncTaskBudgetLogTopPlugins
            && (currentTickDeferredTasks > 0 || runtimeMs >= PurpurConfig.bonfireSyncTaskBudgetSummaryMinRuntimeMs);
        if (!shouldLogDeferrals && !shouldLogTopPlugins) {
            return;
        }

        final String runtimeSummary = shouldLogTopPlugins ? summarizeLongMap(CURRENT_TICK_RUNTIME_NANOS_BY_PLUGIN, true) : "disabled";
        final String deferredSummary = shouldLogTopPlugins ? summarizeIntMap(CURRENT_TICK_DEFERRED_TASKS_BY_PLUGIN) : "disabled";
        lastRuntimeSummary = runtimeSummary;
        lastDeferredSummary = deferredSummary;
        lastStartupPhase = BonfireStartupGuard.phase(Bukkit.getServer() == null ? null : net.minecraft.server.MinecraftServer.getServer());

        Bukkit.getLogger().log(
            Level.INFO,
            String.format(
                "[BonfireHearth] scheduler pressure=%s startup-phase=%s runtime=%.3f ms deferred=%d top-runtime=[%s] top-deferred=[%s]",
                currentPressureState(),
                lastStartupPhase,
                runtimeMs,
                currentTickDeferredTasks,
                runtimeSummary,
                deferredSummary
            )
        );
    }

    private static BonfirePressureState currentPressureState() {
        final BonfirePressureState tickPressure = BonfireServerPressure.pressureState(
            PurpurConfig.bonfireSyncTaskBudgetHighPressureMspt,
            PurpurConfig.bonfireSyncTaskBudgetExtremePressureMspt
        );
        final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(Bukkit.getServer() == null ? null : net.minecraft.server.MinecraftServer.getServer());
        return tickPressure.ordinal() >= startupFloor.ordinal() ? tickPressure : startupFloor;
    }

    private static long nanosFromMs(final double ms) {
        return Math.round(Math.max(0.0D, ms) * 1_000_000.0D);
    }

    private static double nanosToMs(final long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static String pluginKey(final Plugin plugin) {
        return plugin == null ? "unknown" : plugin.getName();
    }

    private static boolean isExempt(final Plugin plugin) {
        final String key = plugin.getName().toLowerCase(Locale.ROOT);
        return PurpurConfig.bonfireSyncTaskBudgetExemptPlugins.contains(key)
            || PurpurConfig.bonfireStartupGuardExemptPlugins.contains(key);
    }

    public static String runtimeSummaryForDiagnostics() {
        return lastRuntimeSummary;
    }

    public static String deferredSummaryForDiagnostics() {
        return lastDeferredSummary;
    }

    public static String startupPhaseForDiagnostics() {
        return lastStartupPhase;
    }

    private static String summarizeLongMap(final Map<String, Long> values, final boolean nanos) {
        if (values.isEmpty()) {
            return "none";
        }

        final int maxEntries = Math.max(1, PurpurConfig.bonfireSyncTaskBudgetTopPluginsToLog);
        final List<String> topEntries = values.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(maxEntries)
            .map(entry -> entry.getKey() + "=" + String.format("%.3fms", nanos ? nanosToMs(entry.getValue()) : (double)entry.getValue()))
            .collect(Collectors.toList());
        return String.join(", ", topEntries);
    }

    private static String summarizeIntMap(final Map<String, Integer> values) {
        if (values.isEmpty()) {
            return "none";
        }

        final int maxEntries = Math.max(1, PurpurConfig.bonfireSyncTaskBudgetTopPluginsToLog);
        final List<String> topEntries = values.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(maxEntries)
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.toList());
        return String.join(", ", topEntries);
    }
}

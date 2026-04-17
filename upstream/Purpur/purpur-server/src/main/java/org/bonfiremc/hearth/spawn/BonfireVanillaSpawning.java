package org.bonfiremc.hearth.spawn;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.server.level.ServerLevel;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfireVanillaSpawning {
    private BonfireVanillaSpawning() {
    }

    public static boolean isNaturalSpawningDisabled(final ServerLevel level) {
        return PurpurConfig.bonfireVanillaSpawningDisableNaturalSpawning;
    }

    public static boolean shouldRunVanillaCustomSpawners(final ServerLevel level) {
        return !PurpurConfig.bonfireVanillaSpawningDisableVanillaCustomSpawners && !isNaturalSpawningDisabled(level);
    }

    public static boolean shouldAllowVanillaSpecialSpawns(final ServerLevel level) {
        return !isNaturalSpawningDisabled(level);
    }

    public static boolean shouldCleanupVanillaEntities(final ServerLevel level) {
        return PurpurConfig.bonfireVanillaSpawningCleanupEnabled && isNaturalSpawningDisabled(level);
    }

    public static boolean shouldIgnoreGameRule(final GameRules.Key<?> key) {
        return PurpurConfig.bonfireVanillaSpawningIgnoreDoMobSpawningGameRule && key == GameRules.RULE_DOMOBSPAWNING;
    }

    public static void sendCompatibilityNotice(final CommandSourceStack source, final GameRules.Key<?> key) {
        if (source == null || !PurpurConfig.bonfireVanillaSpawningWarnOnGameruleChange || !shouldIgnoreGameRule(key)) {
            return;
        }

        source.sendSuccess(
            () -> Component.literal("[BonfireHearth] doMobSpawning is compatibility-only here; vanilla natural spawning is disabled by the Bonfire core."),
            false
        );
    }

    public static String startupSummary(final MinecraftServer server) {
        return String.format(
            "low-memory=%s natural-spawning-disabled=%s vanilla-custom-spawners-disabled=%s gamerule-compat=%s cleanup-mode=%s cleanup-on-chunk-load=%s cleanup-on-world-tick=%s %s",
            PurpurConfig.bonfireLowMemoryModeEnabled,
            PurpurConfig.bonfireVanillaSpawningDisableNaturalSpawning,
            PurpurConfig.bonfireVanillaSpawningDisableVanillaCustomSpawners,
            PurpurConfig.bonfireVanillaSpawningIgnoreDoMobSpawningGameRule,
            PurpurConfig.bonfireVanillaSpawningCleanupMode.name().toLowerCase(java.util.Locale.ROOT),
            PurpurConfig.bonfireVanillaSpawningCleanupOnChunkLoad,
            PurpurConfig.bonfireVanillaSpawningCleanupOnWorldTick,
            BonfireStartupGuard.startupSummary(server)
        );
    }
}

package org.bonfiremc.hearth.pressure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.Bukkit;
import org.bonfiremc.hearth.memory.BonfireMemoryMonitor;
import org.bonfiremc.hearth.network.BonfireNetworkPressure;
import org.bonfiremc.hearth.scheduler.BonfireSyncTaskBudget;
import org.bonfiremc.hearth.tracking.BonfirePressureState;
import org.bonfiremc.hearth.tracking.BonfireEntitySyncHints;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfirePressureDiagnostics {
    private static int nextAllowedLogTick = Integer.MIN_VALUE;

    private BonfirePressureDiagnostics() {
    }

    public static void tick(final MinecraftServer server) {
        if (!PurpurConfig.bonfirePressureDiagnosticsEnabled || server == null) {
            return;
        }

        final int currentTick = server.getTickCount();
        if (currentTick < nextAllowedLogTick) {
            return;
        }

        final BonfirePressureState tickPressureState = BonfireServerPressure.pressureState(
            PurpurConfig.bonfirePressureDiagnosticsHighPressureMspt,
            PurpurConfig.bonfirePressureDiagnosticsExtremePressureMspt
        );
        final BonfirePressureState rawNetworkPressureState = BonfireNetworkPressure.rawPressureState(server);
        final BonfirePressureState effectiveNetworkPressureState = BonfireNetworkPressure.effectiveThrottlePressureState(server);
        if (tickPressureState == BonfirePressureState.NORMAL
            && rawNetworkPressureState == BonfirePressureState.NORMAL
            && effectiveNetworkPressureState == BonfirePressureState.NORMAL) {
            return;
        }

        final BonfireMemoryMonitor.Snapshot memorySnapshot = BonfireMemoryMonitor.snapshot();
        final List<BonfireNetworkPressure.WorldSnapshot> networkSnapshots = BonfireNetworkPressure.worldSnapshots(server);
        final Map<String, BonfireNetworkPressure.WorldSnapshot> networkSnapshotsByWorld = networkSnapshots.stream()
            .collect(Collectors.toMap(BonfireNetworkPressure.WorldSnapshot::worldKey, snapshot -> snapshot));

        final List<WorldSnapshot> snapshots = new ArrayList<>();
        int totalPlayers = 0;
        int totalEntities = 0;
        int totalDecorativeEntities = 0;
        int totalBlockEntityTickers = 0;

        for (final ServerLevel level : server.getAllLevels()) {
            final int playerCount = level.players().size();
            if (playerCount < Math.max(0, PurpurConfig.bonfirePressureDiagnosticsMinPlayers)) {
                continue;
            }

            final WorldSnapshot snapshot = snapshot(level, playerCount, networkSnapshotsByWorld.get(level.dimension().location().toString()));
            snapshots.add(snapshot);
            totalPlayers += snapshot.players();
            totalEntities += snapshot.totalEntities();
            totalDecorativeEntities += snapshot.decorativeEntities();
            totalBlockEntityTickers += snapshot.blockEntityTickers();
        }

        if (snapshots.isEmpty()) {
            return;
        }

        nextAllowedLogTick = currentTick + Math.max(20, PurpurConfig.bonfirePressureDiagnosticsLogCooldownTicks);

        Bukkit.getLogger().log(
            Level.INFO,
            String.format(
                "[BonfireHearth] pressure diagnostics state=%s average-mspt=%.3f worlds=%d players=%d entities=%d decorative=%d block-entity-tickers=%d",
                tickPressureState,
                BonfireServerPressure.averageMspt(),
                snapshots.size(),
                totalPlayers,
                totalEntities,
                totalDecorativeEntities,
                totalBlockEntityTickers
            )
        );
        Bukkit.getLogger().log(
            Level.INFO,
            String.format(
                "[BonfireHearth] network diagnostics raw-state=%s effective-state=%s heap-used=%.1fMB heap-max=%.1fMB old/live=%.2f gc-last-pause=%dms gc-events=%d",
                rawNetworkPressureState,
                effectiveNetworkPressureState,
                memorySnapshot.heapUsedBytes() / 1048576.0D,
                memorySnapshot.heapMaxBytes() / 1048576.0D,
                memorySnapshot.oldGenLiveRatio(),
                memorySnapshot.lastGcPauseMs(),
                memorySnapshot.lastGcEvents()
            )
        );
        Bukkit.getLogger().log(
            Level.INFO,
            String.format(
                "[BonfireHearth] startup diagnostics phase=%s runtime-hotspots=[%s] deferred-hotspots=[%s]",
                BonfireStartupGuard.phase(server),
                BonfireSyncTaskBudget.runtimeSummaryForDiagnostics(),
                BonfireSyncTaskBudget.deferredSummaryForDiagnostics()
            )
        );

        snapshots.stream()
            .sorted(Comparator.comparingLong(WorldSnapshot::sortWeight).reversed())
            .forEach(snapshot -> Bukkit.getLogger().log(
                Level.INFO,
                String.format(
                    "[BonfireHearth] pressure world=%s players=%d entities=%d decorative=%d display=%d interaction=%d armor-stand=%d marker-armor-stand=%d block-entity-tickers=%d entity-sync-bytes=%d entity-sync-packets=%d tracked-entities=%d chunk-send-packets=%d raw-network=%s effective-network=%s heavy-hints=%d static-hints=%d near-priority-hints=%d top-entity-types=[%s] top-hinted-entity-types=[%s]",
                    snapshot.worldKey(),
                    snapshot.players(),
                    snapshot.totalEntities(),
                    snapshot.decorativeEntities(),
                    snapshot.displayEntities(),
                    snapshot.interactionEntities(),
                    snapshot.armorStands(),
                    snapshot.markerArmorStands(),
                    snapshot.blockEntityTickers(),
                    snapshot.entitySyncBytes(),
                    snapshot.entitySyncPackets(),
                    snapshot.maxTrackedEntities(),
                    snapshot.chunkSendPackets(),
                    snapshot.rawPressureState(),
                    snapshot.effectiveThrottlePressureState(),
                    snapshot.heavyHintEntities(),
                    snapshot.staticHintEntities(),
                    snapshot.nearPriorityHintEntities(),
                    snapshot.topEntityTypes(),
                    snapshot.topHintedEntityTypes()
                )
            ));

        networkSnapshots.forEach(snapshot -> Bukkit.getLogger().log(
            Level.INFO,
            String.format(
                "[BonfireHearth] network world=%s players=%d raw=%s effective=%s entity-sync-bytes=%d entity-sync-packets=%d chunk-send-bytes=%d chunk-send-packets=%d max-tracked-entities=%d max-unacked-batches=%d packet-breakdown=[metadata=%d/%dB, equipment=%d/%dB, head-rotation=%d/%dB, movement=%d/%dB, chunk=%d/%dB]",
                snapshot.worldKey(),
                snapshot.players(),
                snapshot.rawPressureState(),
                snapshot.effectiveThrottlePressureState(),
                snapshot.entitySyncBytes(),
                snapshot.entitySyncPackets(),
                snapshot.chunkSendBytes(),
                snapshot.chunkSendPackets(),
                snapshot.maxTrackedEntities(),
                snapshot.maxUnacknowledgedBatches(),
                snapshot.metadataPackets(),
                snapshot.metadataBytes(),
                snapshot.equipmentPackets(),
                snapshot.equipmentBytes(),
                snapshot.headRotationPackets(),
                snapshot.headRotationBytes(),
                snapshot.movementPackets(),
                snapshot.movementBytes(),
                snapshot.chunkPackets(),
                snapshot.chunkPacketBytes()
            )
        ));
    }

    private static WorldSnapshot snapshot(
        final ServerLevel level,
        final int playerCount,
        final BonfireNetworkPressure.WorldSnapshot networkSnapshot
    ) {
        final Map<String, Integer> entityCounts = new HashMap<>();
        final Map<String, Integer> hintedEntityCounts = new HashMap<>();
        int totalEntities = 0;
        int decorativeEntities = 0;
        int displayEntities = 0;
        int interactionEntities = 0;
        int armorStands = 0;
        int markerArmorStands = 0;
        int heavyHintEntities = 0;
        int staticHintEntities = 0;
        int nearPriorityHintEntities = 0;

        for (final Entity entity : level.getAllEntities()) {
            totalEntities++;
            final String entityTypeKey = entityTypeKey(entity.getType());
            entityCounts.merge(entityTypeKey, 1, Integer::sum);

            final boolean heavyHint = BonfireEntitySyncHints.hasHeavyModelHint(entity);
            final boolean staticHint = BonfireEntitySyncHints.hasStaticModelHint(entity);
            final boolean nearPriorityHint = BonfireEntitySyncHints.hasNearPriorityHint(entity);
            if (heavyHint) {
                heavyHintEntities++;
            }
            if (staticHint) {
                staticHintEntities++;
            }
            if (nearPriorityHint) {
                nearPriorityHintEntities++;
            }
            if (heavyHint || staticHint || nearPriorityHint) {
                hintedEntityCounts.merge(entityTypeKey, 1, Integer::sum);
            }

            if (entity instanceof Display) {
                decorativeEntities++;
                displayEntities++;
                continue;
            }
            if (entity instanceof Interaction) {
                decorativeEntities++;
                interactionEntities++;
                continue;
            }
            if (entity instanceof ArmorStand armorStand) {
                decorativeEntities++;
                armorStands++;
                if (armorStand.isMarker() || (armorStand.isInvisible() && armorStand.isNoGravity())) {
                    markerArmorStands++;
                }
            }
        }

        return new WorldSnapshot(
            level.dimension().location().toString(),
            playerCount,
            totalEntities,
            decorativeEntities,
            displayEntities,
            interactionEntities,
            armorStands,
            markerArmorStands,
            blockEntityTickerCount(level),
            networkSnapshot == null ? 0L : networkSnapshot.entitySyncBytes(),
            networkSnapshot == null ? 0 : networkSnapshot.entitySyncPackets(),
            networkSnapshot == null ? 0 : networkSnapshot.chunkSendPackets(),
            networkSnapshot == null ? 0 : networkSnapshot.maxTrackedEntities(),
            networkSnapshot == null ? BonfirePressureState.NORMAL : networkSnapshot.rawPressureState(),
            networkSnapshot == null ? BonfirePressureState.NORMAL : networkSnapshot.effectiveThrottlePressureState(),
            heavyHintEntities,
            staticHintEntities,
            nearPriorityHintEntities,
            summarizeTopEntries(entityCounts),
            summarizeTopEntries(hintedEntityCounts)
        );
    }

    private static String entityTypeKey(final EntityType<?> entityType) {
        final net.minecraft.resources.ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return key == null ? entityType.toString() : key.toString();
    }

    private static String summarizeTopEntries(final Map<String, Integer> values) {
        if (values.isEmpty()) {
            return "none";
        }

        final int maxEntries = Math.max(1, PurpurConfig.bonfirePressureDiagnosticsTopEntityTypesToLog);
        return values.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(maxEntries)
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }

    private static int blockEntityTickerCount(final ServerLevel level) {
        try {
            final Object result = level.getClass().getMethod("bonfire$getBlockEntityTickerCount").invoke(level);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private record WorldSnapshot(
        String worldKey,
        int players,
        int totalEntities,
        int decorativeEntities,
        int displayEntities,
        int interactionEntities,
        int armorStands,
        int markerArmorStands,
        int blockEntityTickers,
        long entitySyncBytes,
        int entitySyncPackets,
        int chunkSendPackets,
        int maxTrackedEntities,
        BonfirePressureState rawPressureState,
        BonfirePressureState effectiveThrottlePressureState,
        int heavyHintEntities,
        int staticHintEntities,
        int nearPriorityHintEntities,
        String topEntityTypes,
        String topHintedEntityTypes
    ) {
        private long sortWeight() {
            return Math.max(this.totalEntities, this.entitySyncBytes);
        }
    }
}

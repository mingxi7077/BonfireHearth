package org.bonfiremc.hearth.network;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.bonfiremc.hearth.tracking.BonfirePressureState;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfireNetworkPressure {
    private static final Map<UUID, PlayerTickMetrics> PLAYER_METRICS = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerPressureHistory> PLAYER_PRESSURES = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerProbeWindow> PLAYER_PROBES = new ConcurrentHashMap<>();
    private static final int ESTIMATED_CHUNK_PACKET_BYTES = 24576;
    private static volatile int currentTick = Integer.MIN_VALUE;

    private BonfireNetworkPressure() {
    }

    public static void beginServerTick(final int tick) {
        if (PurpurConfig.bonfireNetworkPressureEnabled) {
            flushTickMetrics(tick);
        }
        currentTick = tick;
    }

    public static void recordChunkBatch(final ServerPlayer player, final int chunkPacketCount, final int unacknowledgedBatches) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || player == null) {
            return;
        }

        final PlayerTickMetrics metrics = metrics(player);
        final int positivePacketCount = Math.max(0, chunkPacketCount);
        metrics.chunkSendPackets += positivePacketCount;
        metrics.chunkSendBytes += (long) positivePacketCount * ESTIMATED_CHUNK_PACKET_BYTES;
        metrics.chunkPackets += positivePacketCount;
        metrics.chunkPacketBytes += (long) positivePacketCount * ESTIMATED_CHUNK_PACKET_BYTES;
        metrics.unacknowledgedChunkBatches = Math.max(metrics.unacknowledgedChunkBatches, Math.max(0, unacknowledgedBatches));
    }

    public static void recordEntitySyncBroadcast(final Entity entity, final Collection<ServerPlayerConnection> trackedPlayers, final Packet<?> packet) {
        recordEntitySyncBroadcast(entity, trackedPlayers, packet, List.of());
    }

    public static void recordEntitySyncBroadcast(
        final Entity entity,
        final Collection<ServerPlayerConnection> trackedPlayers,
        final Packet<?> packet,
        final Collection<UUID> ignoredPlayers
    ) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || entity == null || trackedPlayers == null || trackedPlayers.isEmpty() || packet == null) {
            return;
        }

        for (final ServerPlayerConnection connection : trackedPlayers) {
            final ServerPlayer player = connection.getPlayer();
            if (player == null || ignoredPlayers.contains(player.getUUID())) {
                continue;
            }

            final PlayerTickMetrics metrics = metrics(player);
            metrics.trackedEntityIds.add(entity.getId());
            recordPacket(metrics, packet);
        }
    }

    public static BonfirePressureState pressureState(final ServerPlayer player) {
        return rawPressureState(player);
    }

    public static BonfirePressureState pressureState(final ServerLevel level) {
        return rawPressureState(level);
    }

    public static BonfirePressureState pressureState(final MinecraftServer server) {
        return rawPressureState(server);
    }

    public static BonfirePressureState rawPressureState(final ServerPlayer player) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || player == null) {
            return BonfirePressureState.NORMAL;
        }

        return rawPressureState(metrics(player));
    }

    public static BonfirePressureState rawPressureState(final ServerLevel level) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || level == null) {
            return BonfirePressureState.NORMAL;
        }

        BonfirePressureState state = BonfirePressureState.NORMAL;
        for (final ServerPlayer player : level.players()) {
            state = max(state, rawPressureState(player));
            if (state == BonfirePressureState.EXTREME) {
                return state;
            }
        }
        return state;
    }

    public static BonfirePressureState rawPressureState(final MinecraftServer server) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || server == null) {
            return BonfirePressureState.NORMAL;
        }

        BonfirePressureState state = BonfirePressureState.NORMAL;
        for (final ServerLevel level : server.getAllLevels()) {
            state = max(state, rawPressureState(level));
            if (state == BonfirePressureState.EXTREME) {
                return state;
            }
        }
        return state;
    }

    public static BonfirePressureState effectiveThrottlePressureState(final ServerPlayer player) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || player == null) {
            return BonfirePressureState.NORMAL;
        }

        return withStartupFloor(player.level().getServer(), snapshot(player).effectiveThrottlePressureState());
    }

    public static BonfirePressureState effectiveThrottlePressureState(final ServerLevel level) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || level == null) {
            return BonfirePressureState.NORMAL;
        }

        BonfirePressureState state = BonfirePressureState.NORMAL;
        for (final ServerPlayer player : level.players()) {
            state = max(state, effectiveThrottlePressureState(player));
            if (state == BonfirePressureState.EXTREME) {
                return state;
            }
        }
        return withStartupFloor(level.getServer(), state);
    }

    public static BonfirePressureState effectiveThrottlePressureState(final MinecraftServer server) {
        if (!PurpurConfig.bonfireNetworkPressureEnabled || server == null) {
            return BonfirePressureState.NORMAL;
        }

        BonfirePressureState state = BonfirePressureState.NORMAL;
        for (final ServerLevel level : server.getAllLevels()) {
            state = max(state, effectiveThrottlePressureState(level));
            if (state == BonfirePressureState.EXTREME) {
                return state;
            }
        }
        return withStartupFloor(server, state);
    }

    public static List<WorldSnapshot> worldSnapshots(final MinecraftServer server) {
        final List<WorldSnapshot> snapshots = new ArrayList<>();
        if (!PurpurConfig.bonfireNetworkPressureEnabled || server == null) {
            return snapshots;
        }

        for (final ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }

            long entitySyncBytes = 0L;
            int entitySyncPackets = 0;
            long chunkSendBytes = 0L;
            int chunkSendPackets = 0;
            int maxTrackedEntities = 0;
            int maxUnacknowledgedBatches = 0;
            int metadataPackets = 0;
            long metadataBytes = 0L;
            int equipmentPackets = 0;
            long equipmentBytes = 0L;
            int headRotationPackets = 0;
            long headRotationBytes = 0L;
            int movementPackets = 0;
            long movementBytes = 0L;
            int chunkPackets = 0;
            long chunkPacketBytes = 0L;
            BonfirePressureState rawState = BonfirePressureState.NORMAL;
            BonfirePressureState effectiveState = BonfirePressureState.NORMAL;

            for (final ServerPlayer player : level.players()) {
                final PlayerSnapshot snapshot = snapshot(player);
                entitySyncBytes += snapshot.entitySyncBytes();
                entitySyncPackets += snapshot.entitySyncPackets();
                chunkSendBytes += snapshot.chunkSendBytes();
                chunkSendPackets += snapshot.chunkSendPackets();
                maxTrackedEntities = Math.max(maxTrackedEntities, snapshot.trackedEntities());
                maxUnacknowledgedBatches = Math.max(maxUnacknowledgedBatches, snapshot.unacknowledgedChunkBatches());
                metadataPackets += snapshot.metadataPackets();
                metadataBytes += snapshot.metadataBytes();
                equipmentPackets += snapshot.equipmentPackets();
                equipmentBytes += snapshot.equipmentBytes();
                headRotationPackets += snapshot.headRotationPackets();
                headRotationBytes += snapshot.headRotationBytes();
                movementPackets += snapshot.movementPackets();
                movementBytes += snapshot.movementBytes();
                chunkPackets += snapshot.chunkPackets();
                chunkPacketBytes += snapshot.chunkPacketBytes();
                rawState = max(rawState, snapshot.rawPressureState());
                effectiveState = max(effectiveState, snapshot.effectiveThrottlePressureState());
            }

            snapshots.add(new WorldSnapshot(
                level.dimension().location().toString(),
                level.players().size(),
                entitySyncBytes,
                entitySyncPackets,
                chunkSendBytes,
                chunkSendPackets,
                maxTrackedEntities,
                maxUnacknowledgedBatches,
                rawState,
                effectiveState,
                metadataPackets,
                metadataBytes,
                equipmentPackets,
                equipmentBytes,
                headRotationPackets,
                headRotationBytes,
                movementPackets,
                movementBytes,
                chunkPackets,
                chunkPacketBytes
            ));
        }

        snapshots.sort(Comparator.comparingLong(WorldSnapshot::entitySyncBytes).reversed());
        return snapshots;
    }

    private static PlayerSnapshot snapshot(final ServerPlayer player) {
        final PlayerTickMetrics metrics = metrics(player);
        final BonfirePressureState rawState = rawPressureState(metrics);
        final BonfirePressureState effectiveCandidateState = effectiveCandidatePressureState(metrics);
        final BonfirePressureState effectiveState = pressureHistory(player).update(metrics.currentTick(), effectiveCandidateState);
        return new PlayerSnapshot(
            rawState,
            effectiveState,
            metrics.trackedEntityIds.size(),
            metrics.entitySyncPackets,
            metrics.entitySyncBytes,
            metrics.chunkSendPackets,
            metrics.chunkSendBytes,
            metrics.unacknowledgedChunkBatches,
            metrics.metadataPackets,
            metrics.metadataBytes,
            metrics.equipmentPackets,
            metrics.equipmentBytes,
            metrics.headRotationPackets,
            metrics.headRotationBytes,
            metrics.movementPackets,
            metrics.movementBytes,
            metrics.chunkPackets,
            metrics.chunkPacketBytes
        );
    }

    private static BonfirePressureState withStartupFloor(final MinecraftServer server, final BonfirePressureState currentState) {
        final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(server);
        return startupFloor.ordinal() > currentState.ordinal() ? startupFloor : currentState;
    }

    private static PlayerTickMetrics metrics(final ServerPlayer player) {
        final PlayerTickMetrics metrics = PLAYER_METRICS.computeIfAbsent(player.getUUID(), ignored -> new PlayerTickMetrics());
        metrics.touchPlayer(player);
        metrics.ensureTick(resolveTick(player.getServer()));
        return metrics;
    }

    private static PlayerPressureHistory pressureHistory(final ServerPlayer player) {
        return PLAYER_PRESSURES.computeIfAbsent(player.getUUID(), ignored -> new PlayerPressureHistory());
    }

    private static int resolveTick(final MinecraftServer server) {
        return currentTick == Integer.MIN_VALUE ? (server == null ? 0 : server.getTickCount()) : currentTick;
    }

    private static void recordPacket(final PlayerTickMetrics metrics, final Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            metrics.entitySyncPackets++;
            metrics.entitySyncBytes += 4;
            for (final Packet<?> subPacket : bundlePacket.subPackets()) {
                recordPacket(metrics, subPacket);
            }
            return;
        }

        final PacketKind packetKind = classifyPacket(packet);
        final int estimatedBytes = estimatePacketBytes(packet);
        metrics.entitySyncPackets++;
        metrics.entitySyncBytes += estimatedBytes;
        switch (packetKind) {
            case METADATA -> {
                metrics.metadataPackets++;
                metrics.metadataBytes += estimatedBytes;
            }
            case EQUIPMENT -> {
                metrics.equipmentPackets++;
                metrics.equipmentBytes += estimatedBytes;
            }
            case HEAD_ROTATION -> {
                metrics.headRotationPackets++;
                metrics.headRotationBytes += estimatedBytes;
            }
            case MOVEMENT -> {
                metrics.movementPackets++;
                metrics.movementBytes += estimatedBytes;
            }
            case CHUNK -> {
                metrics.chunkPackets++;
                metrics.chunkPacketBytes += estimatedBytes;
            }
            case OTHER -> {
            }
        }
    }

    private static BonfirePressureState rawPressureState(final PlayerTickMetrics metrics) {
        return pressureState(
            metrics.trackedEntityIds.size(),
            metrics.entitySyncPackets,
            metrics.entitySyncBytes,
            metrics.chunkSendPackets,
            metrics.unacknowledgedChunkBatches,
            PurpurConfig.bonfireNetworkPressureHighTrackedEntitiesPerPlayer,
            PurpurConfig.bonfireNetworkPressureExtremeTrackedEntitiesPerPlayer,
            PurpurConfig.bonfireNetworkPressureHighEntitySyncPacketsPerTick,
            PurpurConfig.bonfireNetworkPressureExtremeEntitySyncPacketsPerTick,
            PurpurConfig.bonfireNetworkPressureHighEntitySyncBytesPerTick,
            PurpurConfig.bonfireNetworkPressureExtremeEntitySyncBytesPerTick
        );
    }

    private static BonfirePressureState effectiveCandidatePressureState(final PlayerTickMetrics metrics) {
        return pressureState(
            metrics.trackedEntityIds.size(),
            metrics.entitySyncPackets,
            metrics.entitySyncBytes,
            metrics.chunkSendPackets,
            metrics.unacknowledgedChunkBatches,
            PurpurConfig.bonfireNetworkPressureEffectiveHighTrackedEntitiesPerPlayer,
            PurpurConfig.bonfireNetworkPressureEffectiveExtremeTrackedEntitiesPerPlayer,
            PurpurConfig.bonfireNetworkPressureEffectiveHighEntitySyncPacketsPerTick,
            PurpurConfig.bonfireNetworkPressureEffectiveExtremeEntitySyncPacketsPerTick,
            PurpurConfig.bonfireNetworkPressureEffectiveHighEntitySyncBytesPerTick,
            PurpurConfig.bonfireNetworkPressureEffectiveExtremeEntitySyncBytesPerTick
        );
    }

    private static BonfirePressureState pressureState(
        final int trackedEntities,
        final int entitySyncPackets,
        final long entitySyncBytes,
        final int chunkSendPackets,
        final int unacknowledgedChunkBatches,
        final int highTrackedEntities,
        final int extremeTrackedEntities,
        final int highEntitySyncPackets,
        final int extremeEntitySyncPackets,
        final long highEntitySyncBytes,
        final long extremeEntitySyncBytes
    ) {
        if (trackedEntities >= extremeTrackedEntities
            || entitySyncPackets >= extremeEntitySyncPackets
            || entitySyncBytes >= extremeEntitySyncBytes
            || chunkSendPackets >= PurpurConfig.bonfireNetworkPressureExtremeChunkSendPacketsPerTick
            || unacknowledgedChunkBatches >= PurpurConfig.bonfireNetworkPressureExtremeUnacknowledgedChunkBatches) {
            return BonfirePressureState.EXTREME;
        }

        if (trackedEntities >= highTrackedEntities
            || entitySyncPackets >= highEntitySyncPackets
            || entitySyncBytes >= highEntitySyncBytes
            || chunkSendPackets >= PurpurConfig.bonfireNetworkPressureHighChunkSendPacketsPerTick
            || unacknowledgedChunkBatches >= PurpurConfig.bonfireNetworkPressureHighUnacknowledgedChunkBatches) {
            return BonfirePressureState.HIGH;
        }

        return BonfirePressureState.NORMAL;
    }

    private static PacketKind classifyPacket(final Packet<?> packet) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket) {
            return PacketKind.CHUNK;
        }
        if (packet instanceof ClientboundSetEntityDataPacket
            || packet instanceof ClientboundUpdateAttributesPacket
            || packet instanceof ClientboundSetPassengersPacket) {
            return PacketKind.METADATA;
        }
        if (packet instanceof ClientboundSetEquipmentPacket) {
            return PacketKind.EQUIPMENT;
        }
        if (packet instanceof ClientboundRotateHeadPacket) {
            return PacketKind.HEAD_ROTATION;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket
            || packet instanceof ClientboundMoveMinecartPacket
            || packet instanceof ClientboundMoveEntityPacket) {
            return PacketKind.MOVEMENT;
        }
        return PacketKind.OTHER;
    }

    private static int estimatePacketBytes(final Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            int bytes = 4;
            for (final Packet<?> subPacket : bundlePacket.subPackets()) {
                bytes += estimatePacketBytes(subPacket);
            }
            return bytes;
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket) {
            return ESTIMATED_CHUNK_PACKET_BYTES;
        }
        if (packet instanceof ClientboundSetEntityDataPacket) {
            return 128;
        }
        if (packet instanceof ClientboundSetEquipmentPacket) {
            return 160;
        }
        if (packet instanceof ClientboundUpdateAttributesPacket) {
            return 96;
        }
        if (packet instanceof ClientboundSetPassengersPacket) {
            return 48;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket) {
            return 40;
        }
        if (packet instanceof ClientboundMoveMinecartPacket) {
            return 72;
        }
        if (packet instanceof ClientboundMoveEntityPacket) {
            return 32;
        }
        if (packet instanceof ClientboundRotateHeadPacket) {
            return 24;
        }
        return 48;
    }

    private static void flushTickMetrics(final int tick) {
        PLAYER_METRICS.forEach((uuid, metrics) -> {
            final PlayerProbeReport report = metrics.advanceToTick(uuid, tick);
            if (report != null && shouldLogPlayerProbe(report)) {
                logPlayerProbe(report);
            }

            if (metrics.shouldExpire(tick)) {
                PLAYER_METRICS.remove(uuid, metrics);
                PLAYER_PRESSURES.remove(uuid);
                PLAYER_PROBES.remove(uuid);
            }
        });
    }

    private static PlayerProbeReport recordProbeTick(final UUID playerId, final PlayerTickMetrics metrics) {
        if (!PurpurConfig.bonfireNetworkPressurePlayerProbeEnabled) {
            return null;
        }

        final PlayerProbeWindow window = PLAYER_PROBES.computeIfAbsent(playerId, ignored -> new PlayerProbeWindow());
        return window.recordTick(metrics);
    }

    private static boolean shouldLogPlayerProbe(final PlayerProbeReport report) {
        return report != null
            && (report.maxTrackedEntities() >= Math.max(1, PurpurConfig.bonfireNetworkPressurePlayerProbeMinTrackedEntities)
            || report.totalCombinedBytes() >= Math.max(1L, PurpurConfig.bonfireNetworkPressurePlayerProbeMinCombinedBytesPerSecond));
    }

    private static void logPlayerProbe(final PlayerProbeReport report) {
        Bukkit.getLogger().info(String.format(
            Locale.ROOT,
            "[BonfireHearth] player-network-probe player=%s world=%s combined=%dB/s(%.1fKB/s) entity-sync=%dB/s(%.1fKB/s) chunk-send=%dB/s(%.1fKB/s) peak-tick=%dB avg-tick=%dB tracked-max=%d packets=[entity=%d,chunk=%d] breakdown=[metadata=%d/%dB,equipment=%d/%dB,head-rotation=%d/%dB,movement=%d/%dB,chunk=%d/%dB]",
            report.playerName(),
            report.worldKey(),
            report.totalCombinedBytes(),
            report.totalCombinedBytes() / 1024.0D,
            report.entitySyncBytes(),
            report.entitySyncBytes() / 1024.0D,
            report.chunkSendBytes(),
            report.chunkSendBytes() / 1024.0D,
            report.peakCombinedBytesPerTick(),
            report.averageCombinedBytesPerTick(),
            report.maxTrackedEntities(),
            report.entitySyncPackets(),
            report.chunkSendPackets(),
            report.metadataPackets(),
            report.metadataBytes(),
            report.equipmentPackets(),
            report.equipmentBytes(),
            report.headRotationPackets(),
            report.headRotationBytes(),
            report.movementPackets(),
            report.movementBytes(),
            report.chunkPackets(),
            report.chunkPacketBytes()
        ));
    }

    private static BonfirePressureState max(final BonfirePressureState first, final BonfirePressureState second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private enum PacketKind {
        METADATA,
        EQUIPMENT,
        HEAD_ROTATION,
        MOVEMENT,
        CHUNK,
        OTHER
    }

    private static final class PlayerTickMetrics {
        private int lastTick = Integer.MIN_VALUE;
        private int lastTouchedTick = Integer.MIN_VALUE;
        private String playerName = "unknown";
        private String worldKey = "unknown";
        private final IntOpenHashSet trackedEntityIds = new IntOpenHashSet();
        private int entitySyncPackets;
        private long entitySyncBytes;
        private int chunkSendPackets;
        private long chunkSendBytes;
        private int unacknowledgedChunkBatches;
        private int metadataPackets;
        private long metadataBytes;
        private int equipmentPackets;
        private long equipmentBytes;
        private int headRotationPackets;
        private long headRotationBytes;
        private int movementPackets;
        private long movementBytes;
        private int chunkPackets;
        private long chunkPacketBytes;

        private void touchPlayer(final ServerPlayer player) {
            this.playerName = player.getScoreboardName();
            this.worldKey = player.level().dimension().location().toString();
            this.lastTouchedTick = resolveTick(player.getServer());
        }

        private void ensureTick(final int tick) {
            if (this.lastTick == tick) {
                return;
            }

            this.lastTick = tick;
            this.trackedEntityIds.clear();
            this.entitySyncPackets = 0;
            this.entitySyncBytes = 0L;
            this.chunkSendPackets = 0;
            this.chunkSendBytes = 0L;
            this.unacknowledgedChunkBatches = 0;
            this.metadataPackets = 0;
            this.metadataBytes = 0L;
            this.equipmentPackets = 0;
            this.equipmentBytes = 0L;
            this.headRotationPackets = 0;
            this.headRotationBytes = 0L;
            this.movementPackets = 0;
            this.movementBytes = 0L;
            this.chunkPackets = 0;
            this.chunkPacketBytes = 0L;
        }

        private PlayerProbeReport advanceToTick(final UUID playerId, final int tick) {
            if (this.lastTick == Integer.MIN_VALUE || this.lastTick == tick) {
                if (this.lastTick == Integer.MIN_VALUE) {
                    this.lastTick = tick;
                }
                return null;
            }

            final PlayerProbeReport report = recordProbeTick(playerId, this);
            this.ensureTick(tick);
            return report;
        }

        private int currentTick() {
            return this.lastTick;
        }

        private boolean shouldExpire(final int tick) {
            return this.lastTouchedTick != Integer.MIN_VALUE
                && tick - this.lastTouchedTick > Math.max(200, PurpurConfig.bonfireNetworkPressurePlayerProbeRetentionTicks);
        }
    }

    private static final class PlayerPressureHistory {
        private int lastProcessedTick = Integer.MIN_VALUE;
        private BonfirePressureState effectiveState = BonfirePressureState.NORMAL;
        private int highCandidateTicks;
        private int extremeCandidateTicks;
        private int normalTicks;
        private int nonExtremeTicks;

        private BonfirePressureState update(final int tick, final BonfirePressureState candidateState) {
            if (this.lastProcessedTick == tick) {
                return this.effectiveState;
            }

            this.lastProcessedTick = tick;
            final int enterHighTicks = Math.max(1, PurpurConfig.bonfireNetworkPressureEnterHighTicks);
            final int enterExtremeTicks = Math.max(1, PurpurConfig.bonfireNetworkPressureEnterExtremeTicks);
            final int exitHighTicks = Math.max(1, PurpurConfig.bonfireNetworkPressureExitHighTicks);
            final int exitExtremeTicks = Math.max(1, PurpurConfig.bonfireNetworkPressureExitExtremeTicks);

            switch (this.effectiveState) {
                case NORMAL -> {
                    this.normalTicks = 0;
                    this.nonExtremeTicks = 0;
                    if (candidateState == BonfirePressureState.EXTREME) {
                        this.extremeCandidateTicks++;
                        this.highCandidateTicks = 0;
                        if (this.extremeCandidateTicks >= enterExtremeTicks) {
                            this.effectiveState = BonfirePressureState.EXTREME;
                            this.resetCounters();
                        }
                    } else if (candidateState == BonfirePressureState.HIGH) {
                        this.highCandidateTicks++;
                        this.extremeCandidateTicks = 0;
                        if (this.highCandidateTicks >= enterHighTicks) {
                            this.effectiveState = BonfirePressureState.HIGH;
                            this.resetCounters();
                        }
                    } else {
                        this.highCandidateTicks = 0;
                        this.extremeCandidateTicks = 0;
                    }
                }
                case HIGH -> {
                    if (candidateState == BonfirePressureState.EXTREME) {
                        this.extremeCandidateTicks++;
                        this.normalTicks = 0;
                        if (this.extremeCandidateTicks >= enterExtremeTicks) {
                            this.effectiveState = BonfirePressureState.EXTREME;
                            this.resetCounters();
                        }
                    } else if (candidateState == BonfirePressureState.NORMAL) {
                        this.normalTicks++;
                        this.extremeCandidateTicks = 0;
                        if (this.normalTicks >= exitHighTicks) {
                            this.effectiveState = BonfirePressureState.NORMAL;
                            this.resetCounters();
                        }
                    } else {
                        this.normalTicks = 0;
                        this.extremeCandidateTicks = 0;
                    }
                }
                case EXTREME -> {
                    if (candidateState == BonfirePressureState.EXTREME) {
                        this.nonExtremeTicks = 0;
                    } else {
                        this.nonExtremeTicks++;
                        if (this.nonExtremeTicks >= exitExtremeTicks) {
                            this.effectiveState = BonfirePressureState.HIGH;
                            this.resetCounters();
                            if (candidateState == BonfirePressureState.NORMAL) {
                                this.normalTicks = 1;
                            }
                        }
                    }
                }
            }

            return this.effectiveState;
        }

        private void resetCounters() {
            this.highCandidateTicks = 0;
            this.extremeCandidateTicks = 0;
            this.normalTicks = 0;
            this.nonExtremeTicks = 0;
        }
    }

    private record PlayerSnapshot(
        BonfirePressureState rawPressureState,
        BonfirePressureState effectiveThrottlePressureState,
        int trackedEntities,
        int entitySyncPackets,
        long entitySyncBytes,
        int chunkSendPackets,
        long chunkSendBytes,
        int unacknowledgedChunkBatches,
        int metadataPackets,
        long metadataBytes,
        int equipmentPackets,
        long equipmentBytes,
        int headRotationPackets,
        long headRotationBytes,
        int movementPackets,
        long movementBytes,
        int chunkPackets,
        long chunkPacketBytes
    ) {
    }

    private static final class PlayerProbeWindow {
        private int windowStartTick = Integer.MIN_VALUE;
        private int ticksInWindow;
        private String playerName = "unknown";
        private String worldKey = "unknown";
        private long entitySyncBytes;
        private int entitySyncPackets;
        private long chunkSendBytes;
        private int chunkSendPackets;
        private int maxTrackedEntities;
        private long metadataBytes;
        private int metadataPackets;
        private long equipmentBytes;
        private int equipmentPackets;
        private long headRotationBytes;
        private int headRotationPackets;
        private long movementBytes;
        private int movementPackets;
        private long chunkPacketBytes;
        private int chunkPackets;
        private long peakCombinedBytesPerTick;

        private PlayerProbeReport recordTick(final PlayerTickMetrics metrics) {
            if (this.windowStartTick == Integer.MIN_VALUE) {
                this.windowStartTick = metrics.lastTick;
            }

            this.playerName = metrics.playerName;
            this.worldKey = metrics.worldKey;
            this.ticksInWindow++;
            this.entitySyncBytes += metrics.entitySyncBytes;
            this.entitySyncPackets += metrics.entitySyncPackets;
            this.chunkSendBytes += metrics.chunkSendBytes;
            this.chunkSendPackets += metrics.chunkSendPackets;
            this.maxTrackedEntities = Math.max(this.maxTrackedEntities, metrics.trackedEntityIds.size());
            this.metadataBytes += metrics.metadataBytes;
            this.metadataPackets += metrics.metadataPackets;
            this.equipmentBytes += metrics.equipmentBytes;
            this.equipmentPackets += metrics.equipmentPackets;
            this.headRotationBytes += metrics.headRotationBytes;
            this.headRotationPackets += metrics.headRotationPackets;
            this.movementBytes += metrics.movementBytes;
            this.movementPackets += metrics.movementPackets;
            this.chunkPacketBytes += metrics.chunkPacketBytes;
            this.chunkPackets += metrics.chunkPackets;
            this.peakCombinedBytesPerTick = Math.max(this.peakCombinedBytesPerTick, metrics.entitySyncBytes + metrics.chunkSendBytes);

            if (this.ticksInWindow < Math.max(1, PurpurConfig.bonfireNetworkPressurePlayerProbeWindowTicks)) {
                return null;
            }

            final PlayerProbeReport report = new PlayerProbeReport(
                this.playerName,
                this.worldKey,
                this.entitySyncBytes,
                this.entitySyncPackets,
                this.chunkSendBytes,
                this.chunkSendPackets,
                this.metadataBytes,
                this.metadataPackets,
                this.equipmentBytes,
                this.equipmentPackets,
                this.headRotationBytes,
                this.headRotationPackets,
                this.movementBytes,
                this.movementPackets,
                this.chunkPacketBytes,
                this.chunkPackets,
                this.maxTrackedEntities,
                this.peakCombinedBytesPerTick,
                (this.entitySyncBytes + this.chunkSendBytes) / Math.max(1, this.ticksInWindow)
            );
            this.reset(metrics.lastTick + 1);
            return report;
        }

        private void reset(final int nextWindowTick) {
            this.windowStartTick = nextWindowTick;
            this.ticksInWindow = 0;
            this.entitySyncBytes = 0L;
            this.entitySyncPackets = 0;
            this.chunkSendBytes = 0L;
            this.chunkSendPackets = 0;
            this.maxTrackedEntities = 0;
            this.metadataBytes = 0L;
            this.metadataPackets = 0;
            this.equipmentBytes = 0L;
            this.equipmentPackets = 0;
            this.headRotationBytes = 0L;
            this.headRotationPackets = 0;
            this.movementBytes = 0L;
            this.movementPackets = 0;
            this.chunkPacketBytes = 0L;
            this.chunkPackets = 0;
            this.peakCombinedBytesPerTick = 0L;
        }
    }

    public record PlayerProbeReport(
        String playerName,
        String worldKey,
        long entitySyncBytes,
        int entitySyncPackets,
        long chunkSendBytes,
        int chunkSendPackets,
        long metadataBytes,
        int metadataPackets,
        long equipmentBytes,
        int equipmentPackets,
        long headRotationBytes,
        int headRotationPackets,
        long movementBytes,
        int movementPackets,
        long chunkPacketBytes,
        int chunkPackets,
        int maxTrackedEntities,
        long peakCombinedBytesPerTick,
        long averageCombinedBytesPerTick
    ) {
        public long totalCombinedBytes() {
            return this.entitySyncBytes + this.chunkSendBytes;
        }
    }

    public record WorldSnapshot(
        String worldKey,
        int players,
        long entitySyncBytes,
        int entitySyncPackets,
        long chunkSendBytes,
        int chunkSendPackets,
        int maxTrackedEntities,
        int maxUnacknowledgedBatches,
        BonfirePressureState rawPressureState,
        BonfirePressureState effectiveThrottlePressureState,
        int metadataPackets,
        long metadataBytes,
        int equipmentPackets,
        long equipmentBytes,
        int headRotationPackets,
        long headRotationBytes,
        int movementPackets,
        long movementBytes,
        int chunkPackets,
        long chunkPacketBytes
    ) {
    }
}

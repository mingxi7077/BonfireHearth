package org.bonfiremc.hearth.tracking;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.bonfiremc.hearth.network.BonfireNetworkPressure;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.purpurmc.purpur.PurpurWorldConfig;

public final class BonfireEntitySyncBudget {
    private BonfireEntitySyncBudget() {
    }

    public static boolean shouldSendChanges(final ServerLevel level, final Entity entity, final int trackedPlayerCount) {
        final int interval = trackingInterval(level, entity, trackedPlayerCount);
        return shouldRespectInterval(entity, interval, PacketKind.OTHER.phaseSalt());
    }

    public static boolean shouldBroadcastPacket(
        final ServerLevel level,
        final Entity entity,
        final int trackedPlayerCount,
        final double nearestViewerDistanceSqr,
        final Packet<?> packet
    ) {
        final boolean heavyModelHint = BonfireEntitySyncHints.hasHeavyModelHint(entity);
        final boolean nearPriorityHint = BonfireEntitySyncHints.hasNearPriorityHint(entity);
        final PacketKind packetKind = classifyPacket(packet);
        final int legacyVisualInterval = BonfireEntityTrackingBudget.trackingInterval(level, entity, trackedPlayerCount);
        if (legacyVisualInterval > 1) {
            return shouldRespectInterval(
                entity,
                applyNearPriorityRelief(
                    packetKind,
                    applyDistanceBoost(level.purpurConfig, legacyVisualInterval, nearestViewerDistanceSqr, nearPriorityHint),
                    nearPriorityHint
                ),
                packetKind.phaseSalt()
            );
        }

        final PurpurWorldConfig config = level.purpurConfig;
        if (!config.bonfireEntitySyncBudgetEnabled
            || trackedPlayerCount <= 0
            || !isBudgetableLivingEntity(config, entity)
            || packetKind == PacketKind.OTHER) {
            return true;
        }

        final BonfirePressureState state = pressureState(level, trackedPlayerCount);
        if (state == BonfirePressureState.NORMAL) {
            return true;
        }
        if (trackedPlayerCount < Math.max(1, config.bonfireEntitySyncBudgetPacketGateMinTrackedPlayers)
            && BonfireNetworkPressure.effectiveThrottlePressureState(level) == BonfirePressureState.NORMAL
            && !heavyModelHint) {
            return true;
        }

        int interval = packetInterval(config, packetKind, state);
        if (heavyModelHint) {
            interval += state == BonfirePressureState.EXTREME ? 2 : 1;
        }
        interval = applyDistanceBoost(config, interval, nearestViewerDistanceSqr, nearPriorityHint);
        interval = applyNearPriorityRelief(packetKind, interval, nearPriorityHint);
        return shouldRespectInterval(entity, interval, packetKind.phaseSalt());
    }

    public static int trackingInterval(final ServerLevel level, final Entity entity, final int trackedPlayerCount) {
        final int legacyVisualInterval = BonfireEntityTrackingBudget.trackingInterval(level, entity, trackedPlayerCount);
        if (legacyVisualInterval > 1) {
            return legacyVisualInterval;
        }

        final PurpurWorldConfig config = level.purpurConfig;
        if (!config.bonfireEntitySyncBudgetEnabled || !isBudgetableLivingEntity(config, entity) || trackedPlayerCount <= 0) {
            return 1;
        }

        return switch (pressureState(level, trackedPlayerCount)) {
            case NORMAL -> 1;
            case HIGH -> Math.max(1, config.bonfireEntitySyncBudgetHighPressureInterval);
            case EXTREME -> Math.max(1, config.bonfireEntitySyncBudgetExtremePressureInterval);
        };
    }

    private static BonfirePressureState pressureState(final ServerLevel level, final int trackedPlayerCount) {
        final BonfirePressureState tickPressure = BonfireEntityTrackingBudget.pressureState(level, trackedPlayerCount);
        final BonfirePressureState networkPressure = BonfireNetworkPressure.effectiveThrottlePressureState(level);
        BonfirePressureState state = tickPressure.ordinal() >= networkPressure.ordinal() ? tickPressure : networkPressure;
        final BonfirePressureState startupFloor = BonfireStartupGuard.pressureFloor(level.getServer());
        if (startupFloor.ordinal() > state.ordinal()) {
            state = startupFloor;
        }
        return state;
    }

    private static boolean isBudgetableLivingEntity(final PurpurWorldConfig config, final Entity entity) {
        final boolean staticModelHint = BonfireEntitySyncHints.hasStaticModelHint(entity);
        if (!config.bonfireEntitySyncBudgetThrottleStaticLivingEntities || !(entity instanceof LivingEntity livingEntity) || entity instanceof Player) {
            return false;
        }
        if (entity.hasImpulse || entity.isPassenger() || entity.getVehicle() != null || !entity.onGround() && !staticModelHint) {
            return false;
        }
        if (livingEntity.hurtTime > 0) {
            return false;
        }
        final double velocityLimit = staticModelHint
            ? Math.max(config.bonfireEntitySyncBudgetMaxStationaryVelocitySqr * 4.0D, 0.0025D)
            : config.bonfireEntitySyncBudgetMaxStationaryVelocitySqr;
        if (livingEntity.getDeltaMovement().lengthSqr() > velocityLimit) {
            return false;
        }
        if (!staticModelHint && livingEntity.tickCount < Math.max(1, config.bonfireEntitySyncBudgetRequiredStationaryTicks)) {
            return false;
        }
        if (livingEntity instanceof Mob mob) {
            if (mob.getTarget() != null || mob.isLeashed()) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldRespectInterval(final Entity entity, final int interval, final int phaseSalt) {
        if (interval <= 1 || entity.tickCount <= 1) {
            return true;
        }
        final int phase = Math.floorMod(entity.getId() + phaseSalt, interval);
        return Math.floorMod(entity.tickCount, interval) == phase;
    }

    private static PacketKind classifyPacket(final Packet<?> packet) {
        if (packet == null) {
            return PacketKind.OTHER;
        }
        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            return classifyBundlePacket(bundlePacket);
        }
        if (packet instanceof ClientboundSetEntityDataPacket) {
            return PacketKind.METADATA;
        }
        if (packet instanceof ClientboundSetEquipmentPacket) {
            return PacketKind.EQUIPMENT;
        }
        if (packet instanceof ClientboundRotateHeadPacket) {
            return PacketKind.HEAD_ROTATION;
        }
        if (packet instanceof ClientboundUpdateAttributesPacket) {
            return PacketKind.ATTRIBUTES;
        }
        return PacketKind.OTHER;
    }

    private static PacketKind classifyBundlePacket(final ClientboundBundlePacket bundlePacket) {
        PacketKind bundledKind = PacketKind.OTHER;
        for (final Packet<?> subPacket : bundlePacket.subPackets()) {
            final PacketKind subKind = classifyPacket(subPacket);
            if (subKind == PacketKind.OTHER) {
                return PacketKind.OTHER;
            }
            if (bundledKind == PacketKind.OTHER) {
                bundledKind = subKind;
                continue;
            }
            if (bundledKind != subKind) {
                return PacketKind.OTHER;
            }
        }
        return bundledKind;
    }

    private static int packetInterval(final PurpurWorldConfig config, final PacketKind packetKind, final BonfirePressureState state) {
        return switch (packetKind) {
            case METADATA -> state == BonfirePressureState.EXTREME
                ? Math.max(1, config.bonfireEntitySyncBudgetMetadataExtremePressureInterval)
                : Math.max(1, config.bonfireEntitySyncBudgetMetadataHighPressureInterval);
            case EQUIPMENT -> state == BonfirePressureState.EXTREME
                ? Math.max(1, config.bonfireEntitySyncBudgetEquipmentExtremePressureInterval)
                : Math.max(1, config.bonfireEntitySyncBudgetEquipmentHighPressureInterval);
            case HEAD_ROTATION -> state == BonfirePressureState.EXTREME
                ? Math.max(1, config.bonfireEntitySyncBudgetHeadRotationExtremePressureInterval)
                : Math.max(1, config.bonfireEntitySyncBudgetHeadRotationHighPressureInterval);
            case ATTRIBUTES -> state == BonfirePressureState.EXTREME
                ? Math.max(1, config.bonfireEntitySyncBudgetAttributesExtremePressureInterval)
                : Math.max(1, config.bonfireEntitySyncBudgetAttributesHighPressureInterval);
            case OTHER -> 1;
        };
    }

    private static int applyDistanceBoost(
        final PurpurWorldConfig config,
        final int baseInterval,
        final double nearestViewerDistanceSqr,
        final boolean nearPriorityHint
    ) {
        if (baseInterval <= 1) {
            return 1;
        }
        if (nearPriorityHint) {
            return baseInterval;
        }
        final double farDistance = Math.max(0.0D, config.bonfireEntitySyncBudgetFarViewerDistance);
        if (farDistance <= 0.0D) {
            return baseInterval;
        }
        final double farDistanceSqr = farDistance * farDistance;
        if (nearestViewerDistanceSqr >= 0.0D && nearestViewerDistanceSqr >= farDistanceSqr) {
            return Math.max(1, baseInterval + Math.max(0, config.bonfireEntitySyncBudgetFarViewerIntervalBoost));
        }
        return baseInterval;
    }

    private static int applyNearPriorityRelief(
        final PacketKind packetKind,
        final int baseInterval,
        final boolean nearPriorityHint
    ) {
        if (!nearPriorityHint || baseInterval <= 1) {
            return Math.max(1, baseInterval);
        }

        return switch (packetKind) {
            case METADATA, EQUIPMENT, HEAD_ROTATION -> Math.max(1, baseInterval - 1);
            case ATTRIBUTES, OTHER -> Math.max(1, baseInterval);
        };
    }

    private enum PacketKind {
        METADATA(11),
        EQUIPMENT(23),
        HEAD_ROTATION(37),
        ATTRIBUTES(53),
        OTHER(0);

        private final int phaseSalt;

        PacketKind(final int phaseSalt) {
            this.phaseSalt = phaseSalt;
        }

        int phaseSalt() {
            return this.phaseSalt;
        }
    }
}

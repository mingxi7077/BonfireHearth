package org.bonfiremc.hearth.spawn;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bonfiremc.hearth.pressure.BonfireStartupGuard;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfireVanillaEntityCleanup {
    public static final String EXPLICIT_SPAWN_TAG = "bonfire:explicit-spawn";

    private static final Map<String, ArrayDeque<Entity>> WORLD_QUEUES = new ConcurrentHashMap<>();
    private static final Map<String, Map<Long, Integer>> CHUNK_REMOVALS_THIS_TICK = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LAST_CHUNK_BUDGET_TICK = new ConcurrentHashMap<>();
    private static final Map<String, Long> REMOVED_SINCE_LOG = new ConcurrentHashMap<>();
    private static final Map<String, Long> PROTECTED_SINCE_LOG = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NEXT_LOG_TICK = new ConcurrentHashMap<>();

    private BonfireVanillaEntityCleanup() {
    }

    public static boolean shouldSkipEntityAdd(
        final ServerLevel level,
        final Entity entity,
        final CreatureSpawnEvent.SpawnReason spawnReason
    ) {
        if (level == null || entity == null || !BonfireVanillaSpawning.shouldCleanupVanillaEntities(level)) {
            return false;
        }

        markExplicitSource(entity, spawnReason);
        if (!shouldCleanupEntity(level, entity)) {
            if (hasStrongProtection(entity)) {
                PROTECTED_SINCE_LOG.merge(worldKey(level), 1L, Long::sum);
            }
            return false;
        }

        if (isBlockedSpawnReason(spawnReason)) {
            REMOVED_SINCE_LOG.merge(worldKey(level), 1L, Long::sum);
            return true;
        }

        if (spawnReason == null && tryConsumeChunkBudget(level, entity)) {
            REMOVED_SINCE_LOG.merge(worldKey(level), 1L, Long::sum);
            return true;
        }

        queue(level, entity);
        return false;
    }

    public static void tick(final ServerLevel level, final int currentTick) {
        if (level == null || !BonfireVanillaSpawning.shouldCleanupVanillaEntities(level) || !PurpurConfig.bonfireVanillaSpawningCleanupOnWorldTick) {
            return;
        }

        final ArrayDeque<Entity> queue = WORLD_QUEUES.computeIfAbsent(worldKey(level), ignored -> new ArrayDeque<>());
        final long budgetNanos = BonfireStartupGuard.cleanupBudgetNanos();
        final long startedAt = System.nanoTime();
        final int maxEntities = BonfireStartupGuard.cleanupMaxEntitiesPerTick();
        int processed = 0;

        while (!queue.isEmpty() && processed < maxEntities && System.nanoTime() - startedAt < budgetNanos) {
            final Entity entity = queue.pollFirst();
            if (entity == null || entity.isRemoved() || entity.level() != level) {
                continue;
            }
            if (!shouldCleanupEntity(level, entity)) {
                continue;
            }

            entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
            REMOVED_SINCE_LOG.merge(worldKey(level), 1L, Long::sum);
            processed++;
        }

        maybeLog(level, currentTick);
    }

    public static boolean shouldCleanupEntity(final ServerLevel level, final Entity entity) {
        if (level == null || entity == null || entity instanceof Player || !BonfireVanillaSpawning.shouldCleanupVanillaEntities(level)) {
            return false;
        }
        if (!(entity instanceof Mob)) {
            return false;
        }

        final ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return false;
        }
        if (entity.getTags().contains(EXPLICIT_SPAWN_TAG)) {
            return false;
        }

        return !hasStrongProtection(entity);
    }

    public static void markExplicitSource(final Entity entity, final CreatureSpawnEvent.SpawnReason spawnReason) {
        if (entity == null || spawnReason == null || isBlockedSpawnReason(spawnReason)) {
            return;
        }
        entity.addTag(EXPLICIT_SPAWN_TAG);
    }

    private static void queue(final ServerLevel level, final Entity entity) {
        WORLD_QUEUES.computeIfAbsent(worldKey(level), ignored -> new ArrayDeque<>()).offerLast(entity);
    }

    private static boolean tryConsumeChunkBudget(final ServerLevel level, final Entity entity) {
        if (!PurpurConfig.bonfireVanillaSpawningCleanupOnChunkLoad) {
            return false;
        }

        final String worldKey = worldKey(level);
        final int currentTick = level.getServer().getTickCount();
        final Integer lastTick = LAST_CHUNK_BUDGET_TICK.put(worldKey, currentTick);
        if (lastTick == null || lastTick != currentTick) {
            CHUNK_REMOVALS_THIS_TICK.put(worldKey, new HashMap<>());
        }

        final Map<Long, Integer> chunkCounts = CHUNK_REMOVALS_THIS_TICK.computeIfAbsent(worldKey, ignored -> new HashMap<>());
        final ChunkPos chunkPos = entity.chunkPosition();
        final long chunkKey = chunkPos.toLong();
        final int currentCount = chunkCounts.getOrDefault(chunkKey, 0);
        if (currentCount >= BonfireStartupGuard.cleanupMaxEntitiesPerChunkLoad()) {
            return false;
        }

        chunkCounts.put(chunkKey, currentCount + 1);
        return true;
    }

    private static boolean hasStrongProtection(final Entity entity) {
        if (entity == null) {
            return false;
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepNamed && entity.hasCustomName()) {
            return true;
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepPassengerLinked && (!entity.getPassengers().isEmpty() || entity.isPassenger() || entity.getVehicle() != null)) {
            return true;
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepPersistent && entity instanceof Mob mob && mob.isPersistenceRequired()) {
            return true;
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepTamed) {
            if (entity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame()) {
                return true;
            }
            if (entity instanceof OwnableEntity ownableEntity && ownableEntity.getOwner() != null) {
                return true;
            }
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepLeashed && entity instanceof Mob mob && mob.isLeashed()) {
            return true;
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepScoreboardTagged && hasNonBonfireScoreboardTags(entity.getTags())) {
            return true;
        }
        if (PurpurConfig.bonfireVanillaSpawningCleanupKeepPdcEntities && hasPersistentData(entity)) {
            return true;
        }

        final String lowerTags = entity.getTags().stream().map(tag -> tag.toLowerCase(Locale.ROOT)).reduce("", (left, right) -> left + " " + right);
        return lowerTags.contains("mythic")
            || lowerTags.contains("modelengine")
            || lowerTags.contains("bettermodel")
            || lowerTags.contains("bonfire");
    }

    private static boolean hasNonBonfireScoreboardTags(final Set<String> tags) {
        return tags.stream().anyMatch(tag -> !EXPLICIT_SPAWN_TAG.equals(tag));
    }

    private static boolean hasPersistentData(final Entity entity) {
        try {
            final PersistentDataContainer container = entity.getBukkitEntity().getPersistentDataContainer();
            return container != null && !container.getKeys().isEmpty();
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean isBlockedSpawnReason(final CreatureSpawnEvent.SpawnReason spawnReason) {
        if (spawnReason == null) {
            return false;
        }

        return switch (spawnReason) {
            case NATURAL,
                CHUNK_GEN,
                LIGHTNING,
                REINFORCEMENTS,
                NETHER_PORTAL,
                ENDER_PEARL,
                PATROL,
                VILLAGE_DEFENSE,
                VILLAGE_INVASION,
                RAID,
                TRAP -> true;
            default -> false;
        };
    }

    private static void maybeLog(final ServerLevel level, final int currentTick) {
        final String worldKey = worldKey(level);
        final int nextAllowed = NEXT_LOG_TICK.getOrDefault(worldKey, Integer.MIN_VALUE);
        if (currentTick < nextAllowed) {
            return;
        }

        final long removed = REMOVED_SINCE_LOG.getOrDefault(worldKey, 0L);
        final long protectedEntities = PROTECTED_SINCE_LOG.getOrDefault(worldKey, 0L);
        if (removed <= 0L && protectedEntities <= 0L) {
            return;
        }

        NEXT_LOG_TICK.put(worldKey, currentTick + (BonfireStartupGuard.isActive(level.getServer()) ? 40 : 200));
        REMOVED_SINCE_LOG.put(worldKey, 0L);
        PROTECTED_SINCE_LOG.put(worldKey, 0L);
        Bukkit.getLogger().log(
            Level.INFO,
            String.format(
                Locale.ROOT,
                "[BonfireHearth] cleanup world=%s mode=%s removed=%d protected=%d queue=%d",
                worldKey,
                PurpurConfig.bonfireVanillaSpawningCleanupMode.name().toLowerCase(Locale.ROOT),
                removed,
                protectedEntities,
                WORLD_QUEUES.getOrDefault(worldKey, new ArrayDeque<>()).size()
            )
        );
    }

    private static String worldKey(final ServerLevel level) {
        return level.dimension().location().toString();
    }
}

package org.bonfiremc.hearth.tracking;

import java.util.Set;
import net.minecraft.world.entity.Entity;

public final class BonfireEntitySyncHints {
    private static final Set<String> STATIC_MODEL_TAGS = Set.of(
        "bonfire:model-static",
        "bonfire:static-model",
        "bonfire:model-heavy",
        "bonfire:heavy-model"
    );
    private static final Set<String> HEAVY_MODEL_TAGS = Set.of(
        "bonfire:model-heavy",
        "bonfire:heavy-model",
        "bonfire:model-budget-heavy"
    );
    private static final Set<String> NEAR_PRIORITY_TAGS = Set.of(
        "bonfire:model-near-priority",
        "bonfire:sync-priority",
        "bonfire:model-sync-priority"
    );

    private BonfireEntitySyncHints() {
    }

    public static boolean hasStaticModelHint(final Entity entity) {
        return hasAnyTag(entity, STATIC_MODEL_TAGS);
    }

    public static boolean hasHeavyModelHint(final Entity entity) {
        return hasAnyTag(entity, HEAVY_MODEL_TAGS);
    }

    public static boolean hasNearPriorityHint(final Entity entity) {
        return hasAnyTag(entity, NEAR_PRIORITY_TAGS);
    }

    private static boolean hasAnyTag(final Entity entity, final Set<String> tags) {
        if (entity == null || tags.isEmpty()) {
            return false;
        }
        for (final String tag : entity.getTags()) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}

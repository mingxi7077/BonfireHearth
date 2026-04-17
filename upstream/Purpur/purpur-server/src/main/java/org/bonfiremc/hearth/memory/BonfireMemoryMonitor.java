package org.bonfiremc.hearth.memory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;
import org.purpurmc.purpur.PurpurConfig;

public final class BonfireMemoryMonitor {
    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    private static final List<MemoryPoolMXBean> MEMORY_POOLS = ManagementFactory.getMemoryPoolMXBeans();

    private static int lastTick = Integer.MIN_VALUE;
    private static long lastGcCollectionCount = -1L;
    private static long lastGcCollectionTime = -1L;
    private static long lastGcPauseMs = 0L;
    private static long lastGcEvents = 0L;

    private BonfireMemoryMonitor() {
    }

    public static void tick(final int currentTick) {
        if (!PurpurConfig.bonfireLowMemoryModeEnabled || currentTick == lastTick) {
            return;
        }

        lastTick = currentTick;
        final long gcCount = totalCollectionCount();
        final long gcTime = totalCollectionTime();
        if (lastGcCollectionCount >= 0L && gcCount > lastGcCollectionCount) {
            lastGcEvents = Math.max(0L, gcCount - lastGcCollectionCount);
            lastGcPauseMs = Math.max(0L, gcTime - lastGcCollectionTime);
        } else {
            lastGcEvents = 0L;
        }

        lastGcCollectionCount = gcCount;
        lastGcCollectionTime = gcTime;
    }

    public static Snapshot snapshot() {
        final MemoryUsage heapUsage = MEMORY_MX_BEAN.getHeapMemoryUsage();
        final long heapUsed = Math.max(0L, heapUsage.getUsed());
        final long heapMax = Math.max(0L, heapUsage.getMax());
        final long oldGenUsed = oldGenUsed();
        final double oldGenLiveRatio = heapUsed <= 0L ? 0.0D : oldGenUsed / (double) heapUsed;
        return new Snapshot(heapUsed, heapMax, oldGenUsed, oldGenLiveRatio, lastGcEvents, lastGcPauseMs);
    }

    public static String startupSummary() {
        final Snapshot snapshot = snapshot();
        return String.format(
            "heap-used=%.1fMB heap-max=%.1fMB old/live=%.2f gc-last-pause=%dms gc-events=%d",
            snapshot.heapUsedBytes() / 1048576.0D,
            snapshot.heapMaxBytes() / 1048576.0D,
            snapshot.oldGenLiveRatio(),
            snapshot.lastGcPauseMs(),
            snapshot.lastGcEvents()
        );
    }

    private static long totalCollectionCount() {
        long total = 0L;
        for (final GarbageCollectorMXBean gcBean : GC_BEANS) {
            final long count = gcBean.getCollectionCount();
            if (count >= 0L) {
                total += count;
            }
        }
        return total;
    }

    private static long totalCollectionTime() {
        long total = 0L;
        for (final GarbageCollectorMXBean gcBean : GC_BEANS) {
            final long time = gcBean.getCollectionTime();
            if (time >= 0L) {
                total += time;
            }
        }
        return total;
    }

    private static long oldGenUsed() {
        long maxUsed = 0L;
        for (final MemoryPoolMXBean pool : MEMORY_POOLS) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            final String name = pool.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("old") && !name.contains("tenured")) {
                continue;
            }
            final MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                maxUsed = Math.max(maxUsed, Math.max(0L, usage.getUsed()));
            }
        }
        return maxUsed;
    }

    public record Snapshot(
        long heapUsedBytes,
        long heapMaxBytes,
        long oldGenUsedBytes,
        double oldGenLiveRatio,
        long lastGcEvents,
        long lastGcPauseMs
    ) {
    }
}

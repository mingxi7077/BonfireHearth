package org.bonfiremc.hearth.pressure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.TimeUtil;
import org.bonfiremc.hearth.tracking.BonfirePressureState;

public final class BonfireServerPressure {
    private BonfireServerPressure() {
    }

    public static BonfirePressureState pressureState(final double highPressureMspt, final double extremePressureMspt) {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return BonfirePressureState.NORMAL;
        }

        final long averageTickTimeNanos = server.getAverageTickTimeNanos();
        final long extremePressureNanos = nanosFromMspt(extremePressureMspt);
        if (averageTickTimeNanos >= extremePressureNanos) {
            return BonfirePressureState.EXTREME;
        }

        final long highPressureNanos = nanosFromMspt(highPressureMspt);
        if (averageTickTimeNanos >= highPressureNanos) {
            return BonfirePressureState.HIGH;
        }

        return BonfirePressureState.NORMAL;
    }

    public static double averageMspt() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return 0.0D;
        }
        return server.getAverageTickTimeNanos() / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND;
    }

    private static long nanosFromMspt(final double mspt) {
        return Math.round(Math.max(0.0D, mspt) * TimeUtil.NANOSECONDS_PER_MILLISECOND);
    }
}

package aaaa0ggmc.morphext.profiler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * 整合包分析器桥接器 (Profiler Bridge)。
 * 零依赖、高容错、极低开销挂钩 aaaa0ggmc's modpack profiler。
 */
public final class ProfilerBridge {

    private static final MethodHandle IS_BENCHMARKING_HANDLE;
    private static final MethodHandle BEGIN_HANDLE;
    private static final MethodHandle END_HANDLE;
    private static final boolean ACTIVE;

    static {
        MethodHandle isBench = null;
        MethodHandle begin = null;
        MethodHandle end = null;
        boolean ok = false;
        try {
            Class<?> clazz = Class.forName("aaaa0ggmc.profiler.api.ProfilerAPI");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            isBench = lookup.findStatic(clazz, "isBenchmarking", MethodType.methodType(boolean.class));
            begin = lookup.findStatic(clazz, "begin", MethodType.methodType(long.class));
            end = lookup.findStatic(clazz, "end", MethodType.methodType(void.class, String.class, long.class));
            ok = true;
        } catch (Throwable ignored) {
        }
        IS_BENCHMARKING_HANDLE = isBench;
        BEGIN_HANDLE = begin;
        END_HANDLE = end;
        ACTIVE = ok;
    }

    private ProfilerBridge() {}

    public static boolean isBenchmarking() {
        if (!ACTIVE) return false;
        try {
            return (boolean) IS_BENCHMARKING_HANDLE.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    public static long begin() {
        if (!ACTIVE) return 0L;
        try {
            return (long) BEGIN_HANDLE.invokeExact();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static void end(String section, long startNano) {
        if (!ACTIVE || startNano <= 0L) return;
        try {
            END_HANDLE.invokeExact(section, startNano);
        } catch (Throwable ignored) {}
    }

    public static SectionScope scope(String section) {
        return new SectionScope(section);
    }

    public static class SectionScope implements AutoCloseable {
        private final String section;
        private final long start;

        public SectionScope(String section) {
            this.section = section;
            this.start = ProfilerBridge.begin();
        }

        @Override
        public void close() {
            if (this.start > 0L) {
                ProfilerBridge.end(this.section, this.start);
            }
        }
    }
}

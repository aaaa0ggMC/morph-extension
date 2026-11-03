package aaaa0ggmc.morphext.conversion.rule;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构检测器 SPI + 注册表（通用机制）。
 *
 * 规则的 place.structure 只写检测器 id；判定逻辑由注册进来的检测器实现。
 * 内置原版结构检测器（village）；模组/整合包可注册自己的
 * （要塞、神殿、末地城……），核心不维护任何模组专属清单。
 */
public final class StructureDetectors {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");

    public interface Detector {
        boolean matches(World world, BlockPos pos, int radius);
    }

    private static final Map<String, Detector> DETECTORS = new ConcurrentHashMap<>();

    private StructureDetectors() {
    }

    public static void register(String id, Detector detector) {
        if (id != null && detector != null) {
            DETECTORS.put(id.trim().toLowerCase(Locale.ROOT), detector);
        }
    }

    public static Detector get(String id) {
        return id == null ? null : DETECTORS.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** 内置原版检测器（核心 init 期调用）。 */
    public static void registerBuiltins() {
        register("village", (world, pos, radius) -> {
            try {
                return world.getVillageCollection() != null
                        && world.getVillageCollection().getNearestVillage(pos, radius) != null;
            } catch (Throwable t) {
                return false;
            }
        });
        LOGGER.info("[morphext] Registered {} structure detectors", DETECTORS.size());
    }
}

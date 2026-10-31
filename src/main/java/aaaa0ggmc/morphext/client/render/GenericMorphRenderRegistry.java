package aaaa0ggmc.morphext.client.render;

import aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition;
import aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用形态渲染调整注册中心 (Generic Morph Render Registry)。
 * 
 * 作用：解决特定生物在变身 (Morph) 状态下渲染角度异常（例如巨龙类生物在变身时朝向反转 180°）的问题。
 * 外部 patch 模组（如 morphext-patch-abyssalcraft）可通过此 DI 接口对生物进行无侵入定制。
 *
 * 优先级规范：
 * 1. 优先级 1：形态显式配置（来自外部 JSON 或各模组 morph_abilities.json 中的 rotate180 / yawOffset）。
 * 2. 优先级 2：各 patch 模组通过 Java DI API 显式注册的形态 ID 或实体类。
 * 3. 优先级 3：默认内置规则（原版 EntityDragon）。
 */
@SideOnly(Side.CLIENT)
public final class GenericMorphRenderRegistry {
    private static final Logger LOGGER = LogManager.getLogger("morphext-render-registry");

    private static final Set<String> ROTATE_180_MORPHS = ConcurrentHashMap.newKeySet();
    private static final Set<Class<? extends Entity>> ROTATE_180_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Map<String, Float> YAW_OFFSET_BY_MORPH = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Entity>, Float> YAW_OFFSET_BY_CLASS = new ConcurrentHashMap<>();

    static {
        // 内置原版末影龙
        registerRotate180(EntityDragon.class);
    }

    private GenericMorphRenderRegistry() {}

    /**
     * DI 注册：指定形态 ID 在变身渲染时反转 180 度。
     * 自动兼容 namespace:id 与 namespace.id 以及无 namespace 的形式。
     */
    public static void registerRotate180(String morphId) {
        if (morphId == null || morphId.trim().isEmpty()) return;
        String normalized = morphId.trim().toLowerCase();
        ROTATE_180_MORPHS.add(normalized);
        if (normalized.contains(":")) {
            ROTATE_180_MORPHS.add(normalized.replace(':', '.'));
            ROTATE_180_MORPHS.add(normalized.substring(normalized.indexOf(':') + 1));
        } else if (normalized.contains(".")) {
            ROTATE_180_MORPHS.add(normalized.replace('.', ':'));
            ROTATE_180_MORPHS.add(normalized.substring(normalized.indexOf('.') + 1));
        }
        LOGGER.info("[morphext-render] Registered 180° rotation for morph ID: {}", morphId);
    }

    /**
     * DI 注册：指定实体类在变身渲染时反转 180 度。
     */
    public static void registerRotate180(Class<? extends Entity> entityClass) {
        if (entityClass == null) return;
        ROTATE_180_CLASSES.add(entityClass);
        LOGGER.info("[morphext-render] Registered 180° rotation for entity class: {}", entityClass.getName());
    }

    /**
     * DI 注册：指定形态 ID 在变身渲染时的偏航角偏移 (度)。
     */
    public static void registerYawOffset(String morphId, float offsetDegrees) {
        if (morphId == null || morphId.trim().isEmpty()) return;
        String normalized = morphId.trim().toLowerCase();
        YAW_OFFSET_BY_MORPH.put(normalized, offsetDegrees);
        if (normalized.contains(":")) {
            YAW_OFFSET_BY_MORPH.put(normalized.replace(':', '.'), offsetDegrees);
            YAW_OFFSET_BY_MORPH.put(normalized.substring(normalized.indexOf(':') + 1), offsetDegrees);
        } else if (normalized.contains(".")) {
            YAW_OFFSET_BY_MORPH.put(normalized.replace('.', ':'), offsetDegrees);
            YAW_OFFSET_BY_MORPH.put(normalized.substring(normalized.indexOf('.') + 1), offsetDegrees);
        }
    }

    /**
     * DI 注册：指定实体类在变身渲染时的偏航角偏移 (度)。
     */
    public static void registerYawOffset(Class<? extends Entity> entityClass, float offsetDegrees) {
        if (entityClass == null) return;
        YAW_OFFSET_BY_CLASS.put(entityClass, offsetDegrees);
    }

    /**
     * 判断指定形态与实体在变身渲染时是否需要旋转 180 度。
     */
    public static boolean shouldRotate180(String morphName, Entity entity) {
        // 1. JSON 配置检查
        if (morphName != null) {
            MorphAbilityDefinition def = MorphJsonConfig.get(morphName);
            if (def != null && Boolean.TRUE.equals(def.rotate180)) {
                return true;
            }
        }
        // 2. 形态 ID 注册检查
        if (morphName != null) {
            String lower = morphName.toLowerCase();
            if (ROTATE_180_MORPHS.contains(lower)) return true;
            if (lower.contains(":")) {
                if (ROTATE_180_MORPHS.contains(lower.replace(':', '.'))) return true;
                if (ROTATE_180_MORPHS.contains(lower.substring(lower.indexOf(':') + 1))) return true;
            } else if (lower.contains(".")) {
                if (ROTATE_180_MORPHS.contains(lower.replace('.', ':'))) return true;
                if (ROTATE_180_MORPHS.contains(lower.substring(lower.indexOf('.') + 1))) return true;
            }
        }
        // 3. 实体类检查（含 EntityDragon）
        if (entity != null) {
            for (Class<? extends Entity> clazz : ROTATE_180_CLASSES) {
                if (clazz.isInstance(entity)) return true;
            }
        }
        return false;
    }

    /**
     * 计算指定形态与实体在变身渲染时的总偏航角额外偏移 (度)。
     */
    public static float getYawOffset(String morphName, Entity entity) {
        float total = 0.0F;
        if (shouldRotate180(morphName, entity)) {
            total += 180.0F;
        }
        // JSON 覆盖自定义 yawOffset
        if (morphName != null) {
            MorphAbilityDefinition def = MorphJsonConfig.get(morphName);
            if (def != null && def.yawOffset != null) {
                total += def.yawOffset;
            }
        }
        // 代码注册的形态 yawOffset
        if (morphName != null) {
            Float offset = YAW_OFFSET_BY_MORPH.get(morphName.toLowerCase());
            if (offset != null) {
                total += offset;
            }
        }
        // 代码注册的类 yawOffset
        if (entity != null) {
            for (Map.Entry<Class<? extends Entity>, Float> entry : YAW_OFFSET_BY_CLASS.entrySet()) {
                if (entry.getKey().isInstance(entity)) {
                    total += entry.getValue();
                    break;
                }
            }
        }
        return total;
    }
}

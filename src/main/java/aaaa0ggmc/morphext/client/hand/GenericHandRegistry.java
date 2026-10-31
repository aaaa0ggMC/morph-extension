package aaaa0ggmc.morphext.client.hand;

import aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition;
import aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用手部骨骼注册与检索中心 (Generic Hand Registry)。
 *
 * 1. 优先级 1：形态显式配置（来自外部 JSON 或各模组 morph_abilities.json 中的 rightHand / leftHand）。
 * 2. 优先级 2：模型类/形态显式预设映射（内置 Quark、AbyssalCraft、BOP、Dawn of Time 与原版非标准模型）。
 * 3. 优先级 3：自动通用启发式骨骼探测（ModelBoneScanner）。
 */
public final class GenericHandRegistry {
    private static final Logger LOGGER = LogManager.getLogger("morphext-hand-registry");
    private static final Map<String, HandPreset> PRESETS_BY_MORPH = new ConcurrentHashMap<>();
    private static final Map<String, HandPreset> PRESETS_BY_MODEL = new ConcurrentHashMap<>();

    static {
        // --- Minecraft 原版特殊/非 Biped 模型预设 (Vanilla Special / Non-Biped Models) ---
        registerMorph("minecraft:villager_golem", new HandPreset("ironGolemRightArm", "ironGolemLeftArm"));
        registerMorph("villager_golem", new HandPreset("ironGolemRightArm", "ironGolemLeftArm"));
        registerMorph("iron_golem", new HandPreset("ironGolemRightArm", "ironGolemLeftArm"));
        registerModel("ModelIronGolem", new HandPreset("ironGolemRightArm", "ironGolemLeftArm"));

        registerMorph("minecraft:snowman", new HandPreset("rightHand", "leftHand"));
        registerMorph("snowman", new HandPreset("rightHand", "leftHand"));
        registerModel("ModelSnowMan", new HandPreset("rightHand", "leftHand"));

        registerMorph("minecraft:spider", new HandPreset("spiderLeg1", "spiderLeg2"));
        registerMorph("minecraft:cave_spider", new HandPreset("spiderLeg1", "spiderLeg2"));
        registerModel("ModelSpider", new HandPreset("spiderLeg1", "spiderLeg2"));

        registerMorph("minecraft:bat", new HandPreset("batRightWing", "batLeftWing"));
        registerMorph("bat", new HandPreset("batRightWing", "batLeftWing"));
        registerModel("ModelBat", new HandPreset("batRightWing", "batLeftWing"));

        registerMorph("minecraft:squid", new HandPreset("squidTentacles", "squidTentacles"));
        registerMorph("squid", new HandPreset("squidTentacles", "squidTentacles"));
        registerModel("ModelSquid", new HandPreset("squidTentacles", "squidTentacles"));

        registerMorph("minecraft:blaze", new HandPreset("blazeSticks", "blazeSticks"));
        registerMorph("blaze", new HandPreset("blazeSticks", "blazeSticks"));
        registerModel("ModelBlaze", new HandPreset("blazeSticks", "blazeSticks"));
    }

    public static void registerMorph(String morphId, HandPreset preset) {
        if (morphId != null && preset != null) {
            PRESETS_BY_MORPH.put(morphId.toLowerCase(Locale.ROOT), preset);
            if (morphId.contains(":")) {
                PRESETS_BY_MORPH.put(morphId.substring(morphId.indexOf(':') + 1).toLowerCase(Locale.ROOT), preset);
                PRESETS_BY_MORPH.put(morphId.replace(':', '.').toLowerCase(Locale.ROOT), preset);
            }
        }
    }

    public static void registerModel(String modelClassName, HandPreset preset) {
        if (modelClassName != null && preset != null) {
            PRESETS_BY_MODEL.put(modelClassName, preset);
        }
    }

    public static boolean hasPreset(String morphId) {
        if (morphId == null) return false;
        String lower = morphId.toLowerCase(Locale.ROOT);
        if (PRESETS_BY_MORPH.containsKey(lower)) return true;
        if (lower.contains(":") && PRESETS_BY_MORPH.containsKey(lower.replaceFirst(":", "."))) return true;
        if (lower.contains(".") && PRESETS_BY_MORPH.containsKey(lower.replaceFirst("\\.", ":"))) return true;
        MorphAbilityDefinition def = MorphJsonConfig.get(lower);
        return def != null && (Boolean.TRUE.equals(def.hands) || def.rightHand != null || def.leftHand != null);
    }

    public static HandPair findHands(EntityMorph morph) {
        if (morph == null || morph.renderer == null) {
            return new HandPair(null, null, null);
        }
        ModelBase model = morph.renderer.getMainModel();
        if (model == null) {
            return new HandPair(null, null, null);
        }

        String morphName = morph.name != null ? morph.name.toLowerCase(Locale.ROOT) : "";
        HandPreset preset = null;

        // 1. 检查数据驱动定义 (JSON)
        MorphAbilityDefinition def = MorphJsonConfig.get(morphName);
        if (def != null && (def.rightHand != null || def.leftHand != null)) {
            float rotX = def.handRotationX != null ? (float) Math.toRadians(def.handRotationX) : 0;
            float rotY = def.handRotationY != null ? (float) Math.toRadians(def.handRotationY) : 0;
            float rotZ = def.handRotationZ != null ? (float) Math.toRadians(def.handRotationZ) : 0;
            float offX = def.handOffsetX != null ? def.handOffsetX : 0;
            float offY = def.handOffsetY != null ? def.handOffsetY : 0;
            float offZ = def.handOffsetZ != null ? def.handOffsetZ : 0;
            float scale = def.handScale != null ? def.handScale : 0;
            preset = new HandPreset(def.rightHand, def.leftHand, rotX, rotY, rotZ, offX, offY, offZ, scale);
        }

        // 2. 检查形态预设
        if (preset == null) {
            preset = PRESETS_BY_MORPH.get(morphName);
            if (preset == null && morphName.contains(":")) {
                preset = PRESETS_BY_MORPH.get(morphName.replaceFirst(":", "."));
            }
            if (preset == null && morphName.contains(".")) {
                preset = PRESETS_BY_MORPH.get(morphName.replaceFirst("\\.", ":"));
            }
        }

        // 3. 检查模型类预设
        if (preset == null) {
            String modelSimple = model.getClass().getSimpleName();
            preset = PRESETS_BY_MODEL.get(modelSimple);
        }

        // 若有显式预设，直接提取对应字段
        if (preset != null) {
            Map<String, ModelRenderer> parts = ModelBoneScanner.collectModelParts(model);
            ModelRenderer right = findPartInsensitive(parts, preset.rightFieldName);
            ModelRenderer left = findPartInsensitive(parts, preset.leftFieldName);
            if (right != null || left != null) {
                return new HandPair(right, left, preset);
            }
        }

        // 4. 无显式规则或未匹配到零件：委托通用启发式骨骼扫描器
        return ModelBoneScanner.scan(model);
    }

    private static ModelRenderer findPartInsensitive(Map<String, ModelRenderer> parts, String targetName) {
        if (targetName == null || targetName.isEmpty() || parts == null) return null;
        for (Map.Entry<String, ModelRenderer> entry : parts.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private GenericHandRegistry() {
    }
}

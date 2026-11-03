package aaaa0ggmc.morphext.integration.metamorph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据驱动的形态能力与免疫配置加载器。
 * 支持：
 * 1. 扫描所有模组资源中的 assets/<modid>/morphext/morph_abilities.json；
 * 2. 读取并由本地 config/morphext/morph_abilities.json 进行覆盖与扩展；
 * 3. 运行时外部代码 DI 注册 (register)。
 */
public final class MorphJsonConfig {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, MorphAbilityDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private static File configFile;

    public static void init(File configDir) {
        File dir = new File(configDir, "morphext");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        configFile = new File(dir, "morph_abilities.json");
        reload();
    }

    public static void reload() {
        DEFINITIONS.clear();

        // 1. 扫描所有模组 classpath 中的资源文件
        loadModResources();

        // 2. 读取用户/整合包覆盖文件
        loadConfigFile();

        LOGGER.info("[morphext] Loaded morph ability definitions for {} morphs", DEFINITIONS.size());
    }

    /**
     * 扫描各模组 jar 内的 assets/<modid>/morphext/morph_abilities.json
     */
    private static void loadModResources() {
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase();
            String path = "assets/" + modId + "/morphext/morph_abilities.json";
            try (InputStream is = MorphJsonConfig.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        Map<String, MorphAbilityDefinition> map = parseDefinitionMap(path, reader);
                        if (!map.isEmpty()) {
                            mergeDefinitions(map);
                            LOGGER.info("[morphext] Loaded {} embedded morph abilities from {}", map.size(), path);
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("[morphext] Failed to read embedded morph abilities from mod {}", modId, t);
            }
        }
    }

    /**
     * 读取本地 config/morphext/morph_abilities.json
     */
    private static void loadConfigFile() {
        if (configFile == null) {
            return;
        }

        if (!configFile.exists()) {
            writeDefaultConfig();
            return;
        }

        try (InputStream is = new FileInputStream(configFile);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Map<String, MorphAbilityDefinition> map = parseDefinitionMap(configFile.getPath(), reader);
            if (!map.isEmpty()) {
                mergeDefinitions(map);
            }
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to load config/morphext/morph_abilities.json", t);
        }
    }

    /**
     * 逐条目容错解析（Dual-Safe Entry Parse）：
     * 先按 {@code Map<String, JsonElement>} 读入，再逐条反序列化。
     * 单个条目写错（字段类型不符 / 值不是对象）只跳过该条目并告警，
     * 绝不拖垮整个文件——此前一个非对象条目（如整合包用 {@code "_doc": "..."} 写说明）
     * 会让 Gson 在 beginObject 处抛异常，整份 morph_abilities.json 被静默作废，
     * 连带 husk 等形态的 damageImmunities 全部丢失（表现为免疫完全不生效）。
     * 以 {@code _} 或 {@code #} 开头的键按注释/元数据约定静默跳过，不刷告警。
     */
    private static Map<String, MorphAbilityDefinition> parseDefinitionMap(String source, Reader reader) {
        Type type = new TypeToken<Map<String, JsonElement>>() {}.getType();
        Map<String, JsonElement> raw = GSON.fromJson(reader, type);
        Map<String, MorphAbilityDefinition> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (!value.isJsonObject()) {
                if (key != null && (key.startsWith("_") || key.startsWith("#"))) {
                    continue;
                }
                LOGGER.warn("[morphext] {}: entry '{}' is not a JSON object, skipped", source, key);
                continue;
            }
            try {
                MorphAbilityDefinition def = GSON.fromJson(value, MorphAbilityDefinition.class);
                sanitize(def);
                out.put(key, def);
            } catch (Throwable t) {
                LOGGER.warn("[morphext] {}: entry '{}' failed to parse, skipped: {}", source, key, t.toString());
            }
        }
        return out;
    }

    /**
     * Gson 对显式 {@code null} 字段会覆盖字段初始化器（如 {@code "abilities": null}），
     * 这里把 null 集合还原为空集合，保证 mergeDefinitions/查询侧不会因单条脏数据
     * 抛 NPE 而把同一文件剩余条目全部丢掉（与逐条容错解析同一目标）。
     */
    private static void sanitize(MorphAbilityDefinition def) {
        if (def == null) {
            return;
        }
        if (def.abilities == null) def.abilities = new java.util.ArrayList<>();
        if (def.actions == null) def.actions = new java.util.ArrayList<>();
        if (def.removeAbilities == null) def.removeAbilities = new java.util.ArrayList<>();
        if (def.immunities == null) def.immunities = new java.util.ArrayList<>();
        if (def.damageImmunities == null) def.damageImmunities = new java.util.ArrayList<>();
        if (def.silentActions == null) def.silentActions = new java.util.ArrayList<>();
        if (def.factions == null) def.factions = new LinkedHashMap<>();
        if (def.attributes == null) def.attributes = new LinkedHashMap<>();
        if (def.actionAnimation == null) def.actionAnimation = new LinkedHashMap<>();
    }

    private static void writeDefaultConfig() {
        try {
            Map<String, MorphAbilityDefinition> defaults = new HashMap<>(DEFINITIONS);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            }
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to create default morph_abilities.json", t);
        }
    }

    private static void mergeDefinitions(Map<String, MorphAbilityDefinition> map) {
        for (Map.Entry<String, MorphAbilityDefinition> entry : map.entrySet()) {
            String morph = entry.getKey();
            MorphAbilityDefinition incoming = entry.getValue();
            if (incoming == null) continue;

            MorphAbilityDefinition existing = DEFINITIONS.get(morph);
            if (existing == null) {
                DEFINITIONS.put(morph, incoming);
            } else {
                // 合并能力列表与免疫列表
                for (String ab : incoming.abilities) {
                    if (!existing.abilities.contains(ab)) {
                        existing.abilities.add(ab);
                    }
                }
                if (incoming.removeAbilities != null) {
                    for (String rm : incoming.removeAbilities) {
                        if (rm != null && !existing.removeAbilities.contains(rm)) {
                            existing.removeAbilities.add(rm);
                        }
                    }
                }
                if (incoming.action != null && !incoming.action.isEmpty()) {
                    existing.action = incoming.action;
                }
                if (incoming.actions != null) {
                    for (String act : incoming.actions) {
                        if (!existing.actions.contains(act)) {
                            existing.actions.add(act);
                        }
                    }
                }
                for (String imm : incoming.immunities) {
                    if (!existing.immunities.contains(imm)) {
                        existing.immunities.add(imm);
                    }
                }
                for (String dmg : incoming.damageImmunities) {
                    if (!existing.damageImmunities.contains(dmg)) {
                        existing.damageImmunities.add(dmg);
                    }
                }
                if (incoming.hands != null) {
                    existing.hands = incoming.hands;
                }
                if (incoming.rightHand != null) {
                    existing.rightHand = incoming.rightHand;
                }
                if (incoming.leftHand != null) {
                    existing.leftHand = incoming.leftHand;
                }
                if (incoming.handScale != null) {
                    existing.handScale = incoming.handScale;
                }
                if (incoming.handOffsetX != null) {
                    existing.handOffsetX = incoming.handOffsetX;
                }
                if (incoming.handOffsetY != null) {
                    existing.handOffsetY = incoming.handOffsetY;
                }
                if (incoming.handOffsetZ != null) {
                    existing.handOffsetZ = incoming.handOffsetZ;
                }
                if (incoming.handRotationX != null) {
                    existing.handRotationX = incoming.handRotationX;
                }
                if (incoming.handRotationY != null) {
                    existing.handRotationY = incoming.handRotationY;
                }
                if (incoming.handRotationZ != null) {
                    existing.handRotationZ = incoming.handRotationZ;
                }
                if (incoming.factions != null && !incoming.factions.isEmpty()) {
                    existing.factions.putAll(incoming.factions);
                }
                if (incoming.rotate180 != null) {
                    existing.rotate180 = incoming.rotate180;
                }
                if (incoming.yawOffset != null) {
                    existing.yawOffset = incoming.yawOffset;
                }
                if (incoming.speed != null) {
                    existing.speed = incoming.speed;
                }
                // 合并语义补齐（CORE-15）：以下字段此前不参与合并（先到者胜、后者静默丢弃），
                // 双键漂移或跨文件覆盖时会吞字段；补齐为“后到非 null 者胜 / 列表并集”，与 speed/hands 同语义。
                if (incoming.attackDamage != null) {
                    existing.attackDamage = incoming.attackDamage;
                }
                if (incoming.armor != null) {
                    existing.armor = incoming.armor;
                }
                if (incoming.armorToughness != null) {
                    existing.armorToughness = incoming.armorToughness;
                }
                if (incoming.knockbackResistance != null) {
                    existing.knockbackResistance = incoming.knockbackResistance;
                }
                if (incoming.attributes != null && !incoming.attributes.isEmpty()) {
                    existing.attributes.putAll(incoming.attributes);
                }
                if (incoming.actionAnimation != null && !incoming.actionAnimation.isEmpty()) {
                    existing.actionAnimation.putAll(incoming.actionAnimation);
                }
                if (incoming.silentActions != null) {
                    for (String silent : incoming.silentActions) {
                        if (silent != null && !existing.silentActions.contains(silent)) {
                            existing.silentActions.add(silent);
                        }
                    }
                }
                if (incoming.animateSameMorph != null) {
                    existing.animateSameMorph = incoming.animateSameMorph;
                }
            }

            // 双轨双向归一化索引映射 (Dual-Track Normalization)
            if (morph.startsWith("minecraft:")) {
                String bare = morph.substring("minecraft:".length());
                if (!DEFINITIONS.containsKey(bare)) {
                    DEFINITIONS.put(bare, DEFINITIONS.get(morph));
                }
            } else if (!morph.contains(":") && !morph.contains(".")) {
                String mcKey = "minecraft:" + morph;
                if (!DEFINITIONS.containsKey(mcKey)) {
                    DEFINITIONS.put(mcKey, DEFINITIONS.get(morph));
                }
            }
        }
    }

    /**
     * DI 注册入口：允许其他模组在代码层注入特定形态的配置
     */
    public static void register(String morphName, MorphAbilityDefinition def) {
        if (morphName == null || def == null) return;
        sanitize(def);
        Map<String, MorphAbilityDefinition> single = new HashMap<>();
        single.put(morphName, def);
        mergeDefinitions(single);
    }

    public static MorphAbilityDefinition get(String morphName) {
        if (morphName == null || morphName.isEmpty()) return null;
        MorphAbilityDefinition def = DEFINITIONS.get(morphName);
        if (def != null) {
            return def;
        }

        // 1. 双向前缀探测与剥离
        if (morphName.startsWith("minecraft:")) {
            String bare = morphName.substring("minecraft:".length());
            def = DEFINITIONS.get(bare);
            if (def != null) return def;
        } else if (!morphName.contains(":") && !morphName.contains(".")) {
            String mcKey = "minecraft:" + morphName;
            def = DEFINITIONS.get(mcKey);
            if (def != null) return def;
        }

        // 2. 命名空间与点号跨系统兼容
        if (morphName.contains(":")) {
            def = DEFINITIONS.get(morphName.replaceFirst(":", "."));
            if (def != null) return def;
            def = DEFINITIONS.get(morphName.substring(morphName.indexOf(':') + 1));
            if (def != null) return def;
        } else if (morphName.contains(".")) {
            def = DEFINITIONS.get(morphName.replaceFirst("\\.", ":"));
            if (def != null) return def;
            def = DEFINITIONS.get(morphName.substring(morphName.indexOf('.') + 1));
            if (def != null) return def;
        }

        // 3. 不区分大小写兜底检索
        for (Map.Entry<String, MorphAbilityDefinition> entry : DEFINITIONS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(morphName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public static Map<String, MorphAbilityDefinition> getAll() {
        return DEFINITIONS;
    }

    private MorphJsonConfig() {
    }
}

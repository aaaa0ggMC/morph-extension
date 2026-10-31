package aaaa0ggmc.morphext.integration.metamorph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
        Type type = new TypeToken<Map<String, MorphAbilityDefinition>>() {}.getType();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase();
            String path = "assets/" + modId + "/morphext/morph_abilities.json";
            try (InputStream is = MorphJsonConfig.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        Map<String, MorphAbilityDefinition> map = GSON.fromJson(reader, type);
                        if (map != null) {
                            mergeDefinitions(map);
                            LOGGER.info("[morphext] Loaded embedded morph abilities from {}", path);
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

        Type type = new TypeToken<Map<String, MorphAbilityDefinition>>() {}.getType();
        try (InputStream is = new FileInputStream(configFile);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Map<String, MorphAbilityDefinition> map = GSON.fromJson(reader, type);
            if (map != null) {
                mergeDefinitions(map);
            }
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to load config/morphext/morph_abilities.json", t);
        }
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
            }
        }
    }

    /**
     * DI 注册入口：允许其他模组在代码层注入特定形态的配置
     */
    public static void register(String morphName, MorphAbilityDefinition def) {
        if (morphName == null || def == null) return;
        Map<String, MorphAbilityDefinition> single = new HashMap<>();
        single.put(morphName, def);
        mergeDefinitions(single);
    }

    public static MorphAbilityDefinition get(String morphName) {
        if (morphName == null) return null;
        MorphAbilityDefinition def = DEFINITIONS.get(morphName);
        if (def != null) {
            return def;
        }
        if (morphName.contains(":")) {
            def = DEFINITIONS.get(morphName.replaceFirst(":", "."));
            if (def != null) return def;
            return DEFINITIONS.get(morphName.substring(morphName.indexOf(':') + 1));
        } else if (morphName.contains(".")) {
            def = DEFINITIONS.get(morphName.replaceFirst("\\.", ":"));
            if (def != null) return def;
            return DEFINITIONS.get(morphName.substring(morphName.indexOf('.') + 1));
        } else {
            return DEFINITIONS.get("minecraft:" + morphName);
        }
    }

    public static Map<String, MorphAbilityDefinition> getAll() {
        return DEFINITIONS;
    }

    private MorphJsonConfig() {
    }
}

package aaaa0ggmc.morphext.faction;

import com.google.gson.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 阵营关系数据驱动加载器 (Faction JSON & Datapack Loader)。
 *
 * 支持多层级叠加与覆盖机制：
 * 1. 模组内嵌规范 (Embedded Mod Assets):
 *    自动从 classpath 加载 morphext 及其各大 patch 补丁模组（深渊国度、夸克、整合包等）预置的阵营关系；
 * 2. 整合包全局配置 (Global Config Directory):
 *    <instance>/config/morphext/faction_relations.json
 *    <instance>/config/morphext/factions/*.json
 * 3. 存档与数据包层级 (World Save / Datapacks):
 *    <world>/data/morphext/faction_relations.json
 *    <world>/data/morphext/factions/*.json
 *    <world>/datapacks/<pack>/morphext/faction_relations.json
 *    <world>/datapacks/<pack>/morphext/factions/*.json
 */
public final class FactionJsonConfig {
    private static final Logger LOGGER = LogManager.getLogger("morphext-faction-loader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File configDirectory;

    private FactionJsonConfig() {}

    public static void init(File cfgDir) {
        configDirectory = cfgDir;
        reload();
    }

    /**
     * 执行全量重载：重置图引擎并依次加载内嵌文件、全局配置与世界数据。
     */
    public static synchronized void reload() {
        FactionGraphEngine engine = FactionGraphEngine.getInstance();
        engine.clear();

        // 1. 加载所有已安装模组内嵌的阵营关系（核心与 patch 都使用同一约定）
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase(Locale.ROOT);
            loadFromClasspath("assets/" + modId + "/morphext/faction_relations.json");
        }

        // 2. 加载 config/morphext/ 下的整合包全局自定义文件
        if (configDirectory != null) {
            File dir = new File(configDirectory, "morphext");
            File globalFile = new File(dir, "faction_relations.json");
            if (globalFile.isFile()) {
                loadFile(globalFile);
            }
            File factionsSubdir = new File(dir, "factions");
            if (factionsSubdir.isDirectory()) {
                loadDirectory(factionsSubdir);
            }
        }

        LOGGER.info("[morphext-faction] Loaded faction relationships into Graph Engine successfully.");
    }

    /**
     * 当进入或加载某个世界时，扫描并加载该世界专属的自定义数据包配置。
     */
    public static synchronized void onWorldLoad(World world) {
        if (world == null || world.isRemote) return;
        try {
            File worldDir = world.getSaveHandler().getWorldDirectory();
            if (worldDir == null || !worldDir.isDirectory()) return;

            // <world>/data/morphext/
            File dataMorphext = new File(worldDir, "data/morphext");
            if (dataMorphext.isDirectory()) {
                File relFile = new File(dataMorphext, "faction_relations.json");
                if (relFile.isFile()) loadFile(relFile);
                File sub = new File(dataMorphext, "factions");
                if (sub.isDirectory()) loadDirectory(sub);
            }

            // <world>/datapacks/
            File datapacksDir = new File(worldDir, "datapacks");
            if (datapacksDir.isDirectory()) {
                File[] packs = datapacksDir.listFiles(File::isDirectory);
                if (packs != null) {
                    for (File pack : packs) {
                        File packMorphext = new File(pack, "morphext");
                        if (packMorphext.isDirectory()) {
                            File f = new File(packMorphext, "faction_relations.json");
                            if (f.isFile()) loadFile(f);
                            File sub = new File(packMorphext, "factions");
                            if (sub.isDirectory()) loadDirectory(sub);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[morphext-faction] Error loading world-specific faction datapacks", t);
        }
    }

    private static void loadFromClasspath(String path) {
        try (InputStream in = FactionJsonConfig.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                    parseAndApply(root, "classpath:" + path);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[morphext-faction] Embedded asset not present or unreadable: {}", path);
        }
    }

    private static void loadDirectory(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                loadFile(f);
            }
        }
    }

    private static void loadFile(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            parseAndApply(root, file.getAbsolutePath());
        } catch (Throwable t) {
            LOGGER.error("[morphext-faction] Failed to load faction JSON: " + file.getAbsolutePath(), t);
        }
    }

    /**
     * 解析单个 JSON 结构并注入图引擎。
     */
    private static void parseAndApply(JsonObject root, String sourceName) {
        FactionGraphEngine engine = FactionGraphEngine.getInstance();

        // 1. 解析 factions 声明与继承树
        if (root.has("factions") && root.get("factions").isJsonObject()) {
            JsonObject factionsObj = root.getAsJsonObject("factions");
            for (Map.Entry<String, JsonElement> entry : factionsObj.entrySet()) {
                String fId = entry.getKey();
                if (entry.getValue().isJsonObject()) {
                    JsonObject fData = entry.getValue().getAsJsonObject();
                    if (fData.has("parent") && fData.get("parent").isJsonPrimitive()) {
                        String parent = fData.get("parent").getAsString();
                        engine.registerParent(fId, parent);
                    }
                    if (fData.has("parents") && fData.get("parents").isJsonArray()) {
                        for (JsonElement pElem : fData.getAsJsonArray("parents")) {
                            if (pElem.isJsonPrimitive()) {
                                engine.registerParent(fId, pElem.getAsString());
                            }
                        }
                    }
                }
            }
        }

        // 2. 解析 entity_mappings (实体注册名或形态映射到阵营标签)
        if (root.has("entity_mappings") && root.get("entity_mappings").isJsonObject()) {
            JsonObject mappingsObj = root.getAsJsonObject("entity_mappings");
            for (Map.Entry<String, JsonElement> entry : mappingsObj.entrySet()) {
                String entityId = entry.getKey();
                if (entry.getValue().isJsonArray()) {
                    for (JsonElement fElem : entry.getValue().getAsJsonArray()) {
                        if (fElem.isJsonPrimitive()) {
                            engine.registerEntityMapping(entityId, fElem.getAsString());
                        }
                    }
                } else if (entry.getValue().isJsonPrimitive()) {
                    engine.registerEntityMapping(entityId, entry.getValue().getAsString());
                }
            }
        }

        // 3. 解析 relations (阵营间关系规则)
        if (root.has("relations") && root.get("relations").isJsonArray()) {
            JsonArray relArray = root.getAsJsonArray("relations");
            for (JsonElement elem : relArray) {
                if (elem.isJsonObject()) {
                    JsonObject rObj = elem.getAsJsonObject();
                    if (rObj.has("from") && rObj.has("to") && rObj.has("relation")) {
                        String from = rObj.get("from").getAsString();
                        String to = rObj.get("to").getAsString();
                        String relStr = rObj.get("relation").getAsString();
                        boolean symmetric = !rObj.has("symmetric") || rObj.get("symmetric").getAsBoolean();

                        FactionRelation rel = FactionRelation.fromString(relStr);
                        engine.registerRelation(from, to, rel, symmetric);
                    }
                }
            }
        }

        LOGGER.debug("[morphext-faction] Applied faction data from {}", sourceName);
    }
}

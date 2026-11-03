package aaaa0ggmc.morphext.form;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 形态类别注册表（通用机制，纯数据驱动）。
 *
 * 类别是给机制判定用的形态分组标签，例如 ul:undead（不死者）、ul:bone（骨架）。
 * 核心不内置任何类别，也不认识任何具体形态——类别由整合包/支线模组自己声明。
 *
 * 数据来源（与 morph_abilities.json 同一套约定）：
 *   1. 所有已安装模组的 assets/&lt;modid&gt;/morphext/form_categories.json；
 *   2. config/morphext/form_categories.json 覆盖与扩展（首次运行写出当前合并结果）。
 *
 * JSON 格式（扁平映射，成员为形态 id 列表，裸名自动补 minecraft:）：
 * <pre>{"ul:undead": ["zombie", "husk", "zombie_pigman", "zombie_villager", "giant"]}</pre>
 */
public final class FormCategories {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();

    /** category -&gt; members（归一化后的形态 id 集合）。 */
    private static final Map<String, Set<String>> CATEGORIES = new ConcurrentHashMap<>();
    private static File configFile;

    private FormCategories() {
    }

    public static void init(File configDir) {
        File dir = new File(configDir, "morphext");
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[morphext] Cannot create config dir {}", dir);
        }
        configFile = new File(dir, "form_categories.json");
        reload();
    }

    public static void reload() {
        CATEGORIES.clear();
        loadModResources();
        loadConfigFile();
        LOGGER.info("[morphext] Loaded {} form categories", CATEGORIES.size());
    }

    private static void loadModResources() {
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase(Locale.ROOT);
            String path = "assets/" + modId + "/morphext/form_categories.json";
            try (InputStream is = FormCategories.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    Map<String, List<String>> map = GSON.fromJson(reader, MAP_TYPE);
                    if (map != null) {
                        merge(map);
                        LOGGER.info("[morphext] Loaded embedded form categories from {}", path);
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("[morphext] Failed to read embedded form categories from mod {}", modId, t);
            }
        }
    }

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
            Map<String, List<String>> map = GSON.fromJson(reader, MAP_TYPE);
            if (map != null) {
                merge(map);
            }
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to load config/morphext/form_categories.json", t);
        }
    }

    private static void writeDefaultConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot(), writer);
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to create default form_categories.json", t);
        }
    }

    private static void merge(Map<String, List<String>> map) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String category = normalizeCategory(entry.getKey());
            if (category == null || entry.getValue() == null) {
                continue;
            }
            Set<String> members = CATEGORIES.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet());
            for (String raw : entry.getValue()) {
                String form = normalize(raw);
                if (form != null) {
                    members.add(form);
                }
            }
        }
    }

    /** 形态 id 归一：去空白、小写、无命名空间补 minecraft:。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return s.contains(":") ? s : "minecraft:" + s;
    }

    private static String normalizeCategory(String raw) {
        return normalize(raw);
    }

    /** 该形态是否属于指定类别。 */
    public static boolean has(String formId, String category) {
        Set<String> members = CATEGORIES.get(normalizeCategory(category));
        return members != null && members.contains(normalize(formId));
    }

    /** 该形态所属的全部类别（只读视图）。 */
    public static Set<String> categoriesOf(String formId) {
        String form = normalize(formId);
        if (form == null) {
            return Collections.emptySet();
        }
        Set<String> out = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, Set<String>> entry : CATEGORIES.entrySet()) {
            if (entry.getValue().contains(form)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /** 全部类别快照（category -&gt; members），调试用。 */
    public static Map<String, Set<String>> snapshot() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : CATEGORIES.entrySet()) {
            out.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return out;
    }
}

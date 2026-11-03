package aaaa0ggmc.morphext.skill;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.MorphExtTuning;
import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.form.FormCategories;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通用技能系统（数据驱动，核心不认识任何具体技能）。
 *
 * 数据来源（与 morph_abilities.json 同一套约定）：
 *   1. 所有已安装模组的 assets/&lt;modid&gt;/morphext/skills.json；
 *   2. config/morphext/skills.json 覆盖与扩展（首次运行写出当前合并结果）。
 *
 * 文件外形：<pre>{"skills": [SkillDefinition, ...]}</pre>
 * 同 id 后加载者赢（config 覆盖模组资源）。
 *
 * 玩家存档内的存储（经 PlayerAbilityStore，键必须是合法 id，故用对象包一层数组）：
 * <pre>"states": {
 *   "ul.skills":     { "ids": ["ul:bone_armor"] },
 *   "ul.forms_seen": { "ids": ["minecraft:skeleton"] }
 * }</pre>
 *
 * 自动授予条件与外部状态键约定（由整合包/其他机制写入，核心只做存在性判定）：
 * <ul>
 *   <li>{@code forms_seen_all}：grant.forms 全部出现在 ul.forms_seen；</li>
 *   <li>{@code forms_seen_any}：grant.forms 任意一个出现在 ul.forms_seen；</li>
 *   <li>{@code boss_defeated}：状态键 {@code boss_defeated:&lt;skillId&gt;} 存在；</li>
 *   <li>{@code advancement}：状态键等于 definition.advancement；</li>
 *   <li>{@code item_used}：状态键 {@code item_used:&lt;skillId&gt;} 存在；</li>
 *   <li>{@code manual}（默认）：不自动授予，只接受指令或外部调用 grant()。</li>
 * </ul>
 *
 * 服务端主线程专用；所有读写都走 {@link PlayerAbilityStore}，不维护第二份玩家缓存。
 */
@Mod.EventBusSubscriber(modid = MorphExtension.MODID)
public final class SkillRegistry {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 已拥有技能列表的存档键。 */
    public static final String SKILLS_KEY = "ul.skills";
    /** 已见过形态列表的存档键。 */
    public static final String FORMS_SEEN_KEY = "ul.forms_seen";
    /** 状态对象里装字符串数组的成员名。 */
    private static final String IDS_MEMBER = "ids";

    private static final SkillRegistry INSTANCE = new SkillRegistry();
    private static File configFile;
    /** 只对未知条件类型警告一次，避免每 tick 刷屏。 */
    private static final Set<String> WARNED_TYPES = ConcurrentHashMap.newKeySet();

    /** 按 id 去重后加载（后者覆盖前者），保持声明顺序。 */
    private final Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
    private final List<SkillDefinition> ordered = new CopyOnWriteArrayList<>();

    private SkillRegistry() {
    }

    // ---------------------------------------------------------------- 加载

    public static void init(File configDir) {
        File dir = new File(configDir, "morphext");
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[morphext] Cannot create config dir {}", dir);
        }
        configFile = new File(dir, "skills.json");
        reload();
    }

    public static void reload() {
        INSTANCE.definitions.clear();
        loadModResources();
        loadConfigFile();
        INSTANCE.ordered.clear();
        INSTANCE.ordered.addAll(INSTANCE.definitions.values());
        LOGGER.info("[morphext] Loaded {} skills", INSTANCE.ordered.size());
    }

    private static void loadModResources() {
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase(Locale.ROOT);
            String path = "assets/" + modId + "/morphext/skills.json";
            try (InputStream is = SkillRegistry.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    merge(GSON.fromJson(reader, SkillFile.class), path);
                }
            } catch (Throwable t) {
                LOGGER.warn("[morphext] Failed to read embedded skills from mod {}", modId, t);
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
            merge(GSON.fromJson(reader, SkillFile.class), configFile.getPath());
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to load config/morphext/skills.json", t);
        }
    }

    private static void writeDefaultConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            SkillFile file = new SkillFile();
            file.skills.addAll(INSTANCE.ordered);
            GSON.toJson(file, writer);
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to create default skills.json", t);
        }
    }

    private static void merge(SkillFile file, String from) {
        if (file == null || file.skills == null) {
            return;
        }
        int added = 0;
        for (SkillDefinition skill : file.skills) {
            if (skill == null || skill.id == null || skill.id.trim().isEmpty()) {
                LOGGER.warn("[morphext] Skipping malformed skill in {} (need id)", from);
                continue;
            }
            skill.id = skill.id.trim().toLowerCase(Locale.ROOT);
            if (skill.grant == null) {
                skill.grant = new SkillDefinition.GrantSpec();
            }
            if (skill.grant.forms == null) {
                skill.grant.forms = new ArrayList<>();
            }
            if (skill.requiresFormCategory == null) {
                skill.requiresFormCategory = new ArrayList<>();
            }
            if (skill.abilities == null) {
                skill.abilities = new ArrayList<>();
            }
            INSTANCE.definitions.remove(skill.id);
            INSTANCE.definitions.put(skill.id, skill);
            added++;
        }
        if (added > 0) {
            LOGGER.info("[morphext] Loaded {} skills from {}", added, from);
        }
    }

    // ---------------------------------------------------------------- 查询

    /** 全部技能定义快照（只读）。 */
    public static List<SkillDefinition> all() {
        return new ArrayList<>(INSTANCE.ordered);
    }

    public static SkillDefinition byId(String id) {
        if (id == null) {
            return null;
        }
        return INSTANCE.definitions.get(id.trim().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- 玩家数据

    /** 记录玩家见过的形态（归一化后去重；已记录则跳过，不落盘）。 */
    public static void noteForm(EntityPlayerMP player, String formId) {
        String form = FormCategories.normalize(formId);
        if (form == null || player == null) {
            return;
        }
        if (readIds(PlayerAbilityStore.get(player), FORMS_SEEN_KEY).contains(form)) {
            return;
        }
        PlayerAbilityStore.update(player, data -> writeId(data, FORMS_SEEN_KEY, form));
    }

    /** 玩家是否已获得该技能。 */
    public static boolean has(EntityPlayerMP player, String skillId) {
        if (player == null || skillId == null) {
            return false;
        }
        String id = skillId.trim().toLowerCase(Locale.ROOT);
        return readIds(PlayerAbilityStore.get(player), SKILLS_KEY).contains(id);
    }

    /** 玩家已获得技能 id 的快照（只读，含已无定义的遗留 id）。 */
    public static Set<String> ownedSnapshot(EntityPlayerMP player) {
        if (player == null) {
            return java.util.Collections.emptySet();
        }
        return java.util.Collections.unmodifiableSet(readIds(PlayerAbilityStore.get(player), SKILLS_KEY));
    }

    /**
     * 授予技能：写 ul.skills，definition.advancement 非空则写入带 tick 的进度状态，
     * 并向 {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} 抛 {@link SkillGrantedEvent}。
     * 已拥有时静默返回（不重复发事件）。
     */
    public static void grant(EntityPlayerMP player, String skillId) {
        if (player == null || skillId == null) {
            return;
        }
        String id = skillId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || has(player, id)) {
            return;
        }
        SkillDefinition definition = byId(id);
        PlayerAbilityStore.update(player, data -> {
            writeId(data, SKILLS_KEY, id);
            if (definition != null && definition.advancement != null && !definition.advancement.trim().isEmpty()) {
                String key = definition.advancement.trim();
                if (!PlayerAbilityData.validId(key)) {
                    LOGGER.warn("[morphext] Skill {} has invalid advancement key {}", id, key);
                    return;
                }
                JsonObject state = new JsonObject();
                state.addProperty("skill", id);
                state.addProperty("tick", PlayerAbilityStore.now(player));
                data.putState(key, state);
            }
        });
        MinecraftForge.EVENT_BUS.post(new SkillGrantedEvent(player, id));
        LOGGER.info("[morphext] skill granted: id={} player={}", id, player.getName());
    }

    /** 撤销技能（只移除 ul.skills；advancement 进度状态作为历史记录保留）。 */
    public static void revoke(EntityPlayerMP player, String skillId) {
        if (player == null || skillId == null) {
            return;
        }
        String id = skillId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || !has(player, id)) {
            return;
        }
        PlayerAbilityStore.update(player, data -> {
            Set<String> owned = readIds(data, SKILLS_KEY);
            owned.remove(id);
            writeIds(data, SKILLS_KEY, owned);
        });
        LOGGER.info("[morphext] skill revoked: id={} player={}", id, player.getName());
    }

    /**
     * 该技能对玩家当前形态是否「可用」：requiresFormCategory 命中当前形态类别才为 true。
     * 只判形态，不判是否拥有——调用方请配合 {@link #has}。
     */
    public static boolean isUsable(EntityPlayerMP player, String skillId) {
        SkillDefinition definition = byId(skillId);
        if (definition == null || player == null) {
            return false;
        }
        if (definition.requiresFormCategory.isEmpty()) {
            return true;
        }
        Set<String> current = currentCategories(player);
        for (String want : definition.requiresFormCategory) {
            if (want == null) {
                continue;
            }
            if (current.contains(want.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tick 评估

    /** 服务端主线程：定期记录当前形态并评估未获得技能的授予条件。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.world.isRemote || player.isDead) {
            return;
        }
        if (player.ticksExisted % MorphExtTuning.SKILL_GRANT_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        try {
            if (INSTANCE.ordered.isEmpty()) {
                return;
            }
            noteCurrentForm(player);
            evaluate(player);
        } catch (Throwable t) {
            LOGGER.warn("[morphext] Skill tick evaluation failed for {}", player.getName(), t);
        }
    }

    private static void evaluate(EntityPlayerMP player) {
        Set<String> owned = readIds(PlayerAbilityStore.get(player), SKILLS_KEY);
        if (owned.size() == INSTANCE.ordered.size()) {
            return;
        }
        for (SkillDefinition definition : INSTANCE.ordered) {
            if (owned.contains(definition.id)) {
                continue;
            }
            if (matches(definition, player)) {
                grant(player, definition.id);
            }
        }
    }

    private static boolean matches(SkillDefinition definition, EntityPlayerMP player) {
        String type = definition.grant == null || definition.grant.type == null
                ? "manual"
                : definition.grant.type.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "manual":
                return false;
            case "forms_seen_all": {
                List<String> forms = definition.grant == null ? null : definition.grant.forms;
                if (forms == null || forms.isEmpty()) {
                    return false;
                }
                Set<String> seen = readIds(PlayerAbilityStore.get(player), FORMS_SEEN_KEY);
                for (String raw : forms) {
                    String form = FormCategories.normalize(raw);
                    if (form == null || !seen.contains(form)) {
                        return false;
                    }
                }
                return true;
            }
            case "forms_seen_any": {
                List<String> forms = definition.grant == null ? null : definition.grant.forms;
                if (forms == null || forms.isEmpty()) {
                    return false;
                }
                Set<String> seen = readIds(PlayerAbilityStore.get(player), FORMS_SEEN_KEY);
                for (String raw : forms) {
                    String form = FormCategories.normalize(raw);
                    if (form != null && seen.contains(form)) {
                        return true;
                    }
                }
                return false;
            }
            case "boss_defeated":
                return hasState(player, "boss_defeated:" + definition.id);
            case "advancement":
                return definition.advancement != null && !definition.advancement.trim().isEmpty()
                        && hasState(player, definition.advancement.trim());
            case "item_used":
                return hasState(player, "item_used:" + definition.id);
            default:
                if (WARNED_TYPES.add(type)) {
                    LOGGER.warn("[morphext] Unknown skill grant type '{}' (skill {})", type, definition.id);
                }
                return false;
        }
    }

    /** 玩家状态键是否存在（供外部机制以状态形式提供条件）。 */
    private static boolean hasState(EntityPlayerMP player, String key) {
        return PlayerAbilityStore.get(player).toJson().getAsJsonObject("states").has(key);
    }

    /** 当前形态所属类别；未变身或取不到能力时为空集。 */
    private static Set<String> currentCategories(EntityPlayerMP player) {
        String formId = currentFormId(player);
        return formId == null ? java.util.Collections.emptySet() : FormCategories.categoriesOf(formId);
    }

    /** 当前形态 id；未变身 / 取不到能力 / 非服务侧返回 null。 */
    private static String currentFormId(EntityPlayerMP player) {
        try {
            IMorphing morphing = Morphing.get(player);
            if (morphing != null && morphing.isMorphed() && morphing.getCurrentMorph() != null) {
                return FormCategories.normalize(morphing.getCurrentMorph().name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void noteCurrentForm(EntityPlayerMP player) {
        noteForm(player, currentFormId(player));
    }

    // ---------------------------------------------------------------- 存档读写

    private static Set<String> readIds(PlayerAbilityData data, String key) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        JsonElement ids = data.getState(key).get(IDS_MEMBER);
        if (ids == null || !ids.isJsonArray()) {
            return out;
        }
        for (JsonElement element : ids.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                out.add(element.getAsString());
            }
        }
        return out;
    }

    private static void writeIds(PlayerAbilityData data, String key, Set<String> values) {
        JsonObject state = new JsonObject();
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        state.add(IDS_MEMBER, array);
        data.putState(key, state);
    }

    private static void writeId(PlayerAbilityData data, String key, String value) {
        Set<String> values = readIds(data, key);
        values.add(value);
        writeIds(data, key, values);
    }

    /** JSON 文件外形：{"skills": [...]}。 */
    public static final class SkillFile {
        public List<SkillDefinition> skills = new ArrayList<>();
    }
}

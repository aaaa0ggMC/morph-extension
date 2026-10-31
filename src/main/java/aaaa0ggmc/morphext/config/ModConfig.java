package aaaa0ggmc.morphext.config;

import aaaa0ggmc.morphext.ability.WheelConfigState;
import com.google.gson.JsonObject;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * 全局结构性配置：轮盘上限（maxSlots / profileCount）、HUD 位置、快捷键与接管开关。
 *
 * <p>轮盘的槽位绑定、激活 profile、profile 名与各 profile 选中能力属于“玩家+存档”数据，
 * 存在 {@code <存档>/data/morphext/players/<uuid>.json} 的
 * {@code states["morphext:wheel"]}（见 {@link WheelConfigState}），不再存全局。</p>
 *
 * <p>本文件里的 {@code slotN}/{@code profileN_slotM}/{@code activeAbility}/
 * {@code profileN_active}/{@code activeProfile}/{@code profileN_name} 键现在只作为
 * <b>一次性迁移种子</b>保留：第一个缺少轮盘状态的玩家数据创建时读取一次，随后写入
 * {@code wheel_state_migrated=true}，之后新建的存档/玩家一律从默认轮盘布局开始，
 * 不会反复继承这份全局种子。</p>
 *
 * <p>Profile 快捷键（{@code profileN_key}）是输入设备绑定，仍属全局配置。
 * 本类在专用服务器上也会加载，因此不能引用 LWJGL 的 {@code Keyboard}；
 * 快捷键以字符串形式保存（按键名或数字键码），由客户端解析。</p>
 */
public class ModConfig {
    public static final int MAX_SLOTS_HARD_CAP = WheelConfigState.SLOT_COUNT;
    public static final int MAX_PROFILES = WheelConfigState.PROFILE_COUNT;

    private static final String[] DEFAULT_PROFILE_KEYS = {"Q", "W", "E", "R", "T", "Y"};

    private static Configuration config;

    public static int maxSlots = MAX_SLOTS_HARD_CAP;
    public static int profileCount = 3;

    /** [profile][slot] 绑定的能力 id（迁移种子，见类注释）。 */
    private static final String[][] PROFILE_SLOT_ABILITY_IDS = new String[MAX_PROFILES][MAX_SLOTS_HARD_CAP];
    /** 每套 Profile 当前选中的能力（迁移种子）。 */
    private static final String[] PROFILE_ACTIVE_ABILITY = new String[MAX_PROFILES];
    /** 每套 Profile 的显示名（迁移种子）。 */
    private static final String[] PROFILE_NAMES = new String[MAX_PROFILES];
    /** 每套 Profile 的快捷切换键（按键名或数字键码的字符串；全局输入绑定）。 */
    private static final String[] PROFILE_KEYS = new String[MAX_PROFILES];
    /** 迁移种子中的激活 profile（迁移种子）。 */
    private static int seedActiveProfile = 0;

    /** 轮盘布局迁移是否已完成（一次性全局标记，防止每个新存档重复继承种子）。 */
    private static boolean wheelStateMigrated = false;

    public static int hudHorizontal = 0;
    public static int hudVertical = 2;
    public static int hudMargin = 0;

    /** 是否接管并禁用生存模式下的 Metamorph 自主操作（禁止自主变回、禁止打开形态菜单、锁死灵魂掉落）。 */
    public static boolean overwriteMetamorph = true;

    /** 是否接管并禁用 Metamorph 原生的盲目 Hostile 伪装判定，改由细分阵营被动系统 (Faction System) 接管。 */
    public static boolean bypassMetamorphHostile = true;

    public static synchronized void init(File file) {
        config = new Configuration(file);
        load();
    }

    public static synchronized void load() {
        hudHorizontal = config.getInt("hudHorizontal", "hud", 0, 0, 2, "0=left, 1=middle, 2=right");
        hudVertical = config.getInt("hudVertical", "hud", 2, 0, 2, "0=top, 1=center, 2=bottom");
        hudMargin = config.getInt("margin", "hud", 0, 0, 200, "HUD margin in GUI pixels");
        overwriteMetamorph = config.getBoolean("overwrite_metamorph", Configuration.CATEGORY_GENERAL,
                true, "Completely disable survival Metamorph operations (demorphing, morph menu, soul drops)");
        bypassMetamorphHostile = config.getBoolean("bypass_metamorph_hostile", Configuration.CATEGORY_GENERAL,
                true, "Disable Metamorph's naive blanket Hostile disguise check and use morphext's Faction System instead");
        applyMetamorphBypass();
        maxSlots = config.getInt("maxSlots", Configuration.CATEGORY_GENERAL,
                MAX_SLOTS_HARD_CAP, 1, MAX_SLOTS_HARD_CAP,
                "Max abilities bound to the wheel (the circle is evenly divided by this count)");

        profileCount = config.getInt("profileCount", Configuration.CATEGORY_GENERAL,
                3, 1, MAX_PROFILES, "Number of wheel profiles (each profile has its own slot bindings)");
        wheelStateMigrated = config.getBoolean("wheel_state_migrated", Configuration.CATEGORY_GENERAL,
                false, "One-time migration marker: the legacy global wheel layout has been seeded "
                        + "into the first player data and must not be inherited by new saves");

        for (int p = 0; p < MAX_PROFILES; p++) {
            PROFILE_NAMES[p] = config.getString("profile" + (p + 1) + "_name", Configuration.CATEGORY_GENERAL,
                    "Profile " + (p + 1), "Legacy wheel layout seed: display name of profile " + (p + 1));
            PROFILE_KEYS[p] = config.getString("profile" + (p + 1) + "_key", Configuration.CATEGORY_GENERAL,
                    DEFAULT_PROFILE_KEYS[p], "Quick-switch key for profile " + (p + 1)
                            + " (key name such as Q, or a numeric LWJGL key code; empty disables it)");

            for (int s = 0; s < MAX_SLOTS_HARD_CAP; s++) {
                String key = p == 0 ? "slot" + (s + 1) : "profile" + (p + 1) + "_slot" + (s + 1);
                PROFILE_SLOT_ABILITY_IDS[p][s] = config.getString(key, Configuration.CATEGORY_GENERAL, "",
                        "Legacy wheel layout seed: ability id bound to profile " + (p + 1) + " slot " + (s + 1));
            }
            String activeKey = p == 0 ? "activeAbility" : "profile" + (p + 1) + "_active";
            PROFILE_ACTIVE_ABILITY[p] = config.getString(activeKey, Configuration.CATEGORY_GENERAL, "",
                    "Legacy wheel layout seed: selected ability of profile " + (p + 1));
        }
        seedActiveProfile = Math.max(0, Math.min(MAX_PROFILES - 1, config.getInt("activeProfile",
                Configuration.CATEGORY_GENERAL, 0, 0, MAX_PROFILES - 1,
                "Legacy wheel layout seed: active wheel profile (0-based)")));

        if (config.hasChanged()) {
            config.save();
        }
    }

    /* ---------------- 结构性上限 ---------------- */

    public static int profileCount() {
        return Math.max(1, Math.min(MAX_PROFILES, profileCount));
    }

    public static synchronized void setProfileCount(int value) {
        profileCount = Math.max(1, Math.min(MAX_PROFILES, value));
        config.get(Configuration.CATEGORY_GENERAL, "profileCount", 3, "", 1, MAX_PROFILES).set(profileCount);
        config.save();
    }

    public static synchronized void setMaxSlots(int value) {
        maxSlots = Math.max(1, Math.min(MAX_SLOTS_HARD_CAP, value));
        config.get(Configuration.CATEGORY_GENERAL, "maxSlots", MAX_SLOTS_HARD_CAP, "", 1, MAX_SLOTS_HARD_CAP).set(maxSlots);
        config.save();
    }

    /** 当前生效槽位数（受 maxSlots 限制）。 */
    public static int activeSlotCount() {
        return Math.max(1, Math.min(maxSlots, MAX_SLOTS_HARD_CAP));
    }

    /** Profile 快捷切换键（全局输入绑定，不属于存档数据）。 */
    public static String getProfileKeyName(int profile) {
        int p = Math.max(0, Math.min(MAX_PROFILES - 1, profile));
        String key = PROFILE_KEYS[p];
        return key == null ? "" : key.trim();
    }

    /* ---------------- 一次性迁移种子 ---------------- */

    /**
     * 消费全局轮盘布局种子（每个玩家数据首次缺少 {@code morphext:wheel} 时调用一次）。
     *
     * <p>第一次调用返回 cfg 里的旧全局布局并写入 {@code wheel_state_migrated=true}；
     * 之后（含重载配置后）永远返回默认布局，防止每个新存档重复继承。</p>
     */
    public static synchronized JsonObject consumeWheelSeed() {
        if (wheelStateMigrated || config == null) {
            return WheelConfigState.defaults().toJson();
        }
        wheelStateMigrated = true;
        config.get(Configuration.CATEGORY_GENERAL, "wheel_state_migrated", false,
                "One-time migration marker: the legacy global wheel layout has been seeded "
                        + "into the first player data and must not be inherited by new saves").set(true);
        config.save();
        return WheelConfigState.of(seedActiveProfile, PROFILE_SLOT_ABILITY_IDS,
                PROFILE_ACTIVE_ABILITY, PROFILE_NAMES).toJson();
    }

    /* ---------------- 其他 ---------------- */

    /** HUD 位置修改，立即持久化。 */
    public static synchronized void setHudPosition(int horizontal, int vertical, int margin) {
        hudHorizontal = Math.max(0, Math.min(2, horizontal));
        hudVertical = Math.max(0, Math.min(2, vertical));
        hudMargin = Math.max(0, Math.min(200, margin));
        config.get("hud", "hudHorizontal", 0).set(hudHorizontal);
        config.get("hud", "hudVertical", 2).set(hudVertical);
        config.get("hud", "margin", 0).set(hudMargin);
        config.save();
    }

    /** 动态应用 Metamorph 敌对伪装禁用设置。 */
    public static void applyMetamorphBypass() {
        if (net.minecraftforge.fml.common.Loader.isModLoaded("metamorph")) {
            try {
                mchorse.metamorph.Metamorph.disableMorphDisguise.set(bypassMetamorphHostile);
            } catch (Throwable ignored) {
            }
        }
    }
}

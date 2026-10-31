package aaaa0ggmc.morphext.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * 轮盘配置：最大槽位数（1-8）、多套 Profile（每套独立槽位与当前选中能力）以及 HUD 位置。
 *
 * <p>Profile 用于保存不同的“配装方案”：切换 Profile 时轮盘上 1..8 号槽位的绑定一起切换，
 * 因此玩家可以为战斗/探索/副本站位准备不同的技能组合。</p>
 *
 * <p>存储兼容旧配置：Profile 1 继续读写旧的 {@code slot1..slot8} / {@code activeAbility}，
 * Profile 2 起使用 {@code profile2_slot1..} / {@code profile2_active}。</p>
 *
 * <p>本类在专用服务器上也会加载，因此不能引用 LWJGL 的 {@code Keyboard}。
 * Profile 快捷键以字符串形式保存（按键名或数字键码），由客户端解析。</p>
 */
public class ModConfig {
    public static final int MAX_SLOTS_HARD_CAP = 8;
    public static final int MAX_PROFILES = 6;

    private static final String[] DEFAULT_PROFILE_KEYS = {"Q", "W", "E", "R", "T", "Y"};

    private static Configuration config;

    public static int maxSlots = MAX_SLOTS_HARD_CAP;
    public static int profileCount = 3;
    public static int activeProfile = 0;

    /** [profile][slot] 绑定的能力 id。 */
    private static final String[][] PROFILE_SLOT_ABILITY_IDS = new String[MAX_PROFILES][MAX_SLOTS_HARD_CAP];
    /** 每套 Profile 当前选中的能力（按释放键触发）。 */
    private static final String[] PROFILE_ACTIVE_ABILITY = new String[MAX_PROFILES];
    /** 每套 Profile 的显示名。 */
    private static final String[] PROFILE_NAMES = new String[MAX_PROFILES];
    /** 每套 Profile 的快捷切换键（按键名或数字键码的字符串）。 */
    private static final String[] PROFILE_KEYS = new String[MAX_PROFILES];

    public static int hudHorizontal = 0;
    public static int hudVertical = 2;
    public static int hudMargin = 0;

    /** 是否接管并禁用生存模式下的 Metamorph 自主操作（禁止自主变回、禁止打开形态菜单、锁死灵魂掉落）。 */
    public static boolean overwriteMetamorph = true;

    /** 是否接管并禁用 Metamorph 原生的盲目 Hostile 伪装判定，改由细分阵营被动系统 (Faction System) 接管。 */
    public static boolean bypassMetamorphHostile = true;

    public static void init(File file) {
        config = new Configuration(file);
        load();
    }

    public static void load() {
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
        activeProfile = clampProfile(config.getInt("activeProfile", Configuration.CATEGORY_GENERAL,
                0, 0, MAX_PROFILES - 1, "Currently active wheel profile (0-based)"));

        for (int p = 0; p < MAX_PROFILES; p++) {
            PROFILE_NAMES[p] = config.getString("profile" + (p + 1) + "_name", Configuration.CATEGORY_GENERAL,
                    "Profile " + (p + 1), "Display name of wheel profile " + (p + 1));
            PROFILE_KEYS[p] = config.getString("profile" + (p + 1) + "_key", Configuration.CATEGORY_GENERAL,
                    DEFAULT_PROFILE_KEYS[p], "Quick-switch key for profile " + (p + 1)
                            + " (key name such as Q, or a numeric LWJGL key code; empty disables it)");

            for (int s = 0; s < MAX_SLOTS_HARD_CAP; s++) {
                String key = p == 0 ? "slot" + (s + 1) : "profile" + (p + 1) + "_slot" + (s + 1);
                PROFILE_SLOT_ABILITY_IDS[p][s] = config.getString(key, Configuration.CATEGORY_GENERAL, "",
                        "Ability id bound to profile " + (p + 1) + " wheel slot " + (s + 1));
            }
            String activeKey = p == 0 ? "activeAbility" : "profile" + (p + 1) + "_active";
            PROFILE_ACTIVE_ABILITY[p] = config.getString(activeKey, Configuration.CATEGORY_GENERAL, "",
                    "Currently selected ability of profile " + (p + 1) + " (triggered by the cast key)");
        }

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static int clampProfile(int profile) {
        return Math.max(0, Math.min(MAX_PROFILES - 1, profile));
    }

    /* ---------------- Profile ---------------- */

    public static int profileCount() {
        return Math.max(1, Math.min(MAX_PROFILES, profileCount));
    }

    public static int activeProfile() {
        return Math.max(0, Math.min(profileCount() - 1, activeProfile));
    }

    public static void setActiveProfile(int profile) {
        activeProfile = clampProfile(profile);
        config.get(Configuration.CATEGORY_GENERAL, "activeProfile", 0).set(activeProfile);
        config.save();
    }

    public static String getProfileName(int profile) {
        int p = clampProfile(profile);
        String name = PROFILE_NAMES[p];
        return name == null || name.trim().isEmpty() ? "Profile " + (p + 1) : name;
    }

    public static String getProfileKeyName(int profile) {
        int p = clampProfile(profile);
        String key = PROFILE_KEYS[p];
        return key == null ? "" : key.trim();
    }

    public static void setProfileCount(int value) {
        profileCount = Math.max(1, Math.min(MAX_PROFILES, value));
        if (activeProfile >= profileCount) {
            activeProfile = profileCount - 1;
            config.get(Configuration.CATEGORY_GENERAL, "activeProfile", 0).set(activeProfile);
        }
        config.get(Configuration.CATEGORY_GENERAL, "profileCount", 3, "", 1, MAX_PROFILES).set(profileCount);
        config.save();
    }

    public static String getProfileSlot(int profile, int index) {
        int p = clampProfile(profile);
        if (index < 0 || index >= MAX_SLOTS_HARD_CAP) {
            return "";
        }
        String id = PROFILE_SLOT_ABILITY_IDS[p][index];
        return id == null ? "" : id;
    }

    public static void setProfileSlot(int profile, int index, String abilityId) {
        int p = clampProfile(profile);
        if (index < 0 || index >= MAX_SLOTS_HARD_CAP) {
            return;
        }
        PROFILE_SLOT_ABILITY_IDS[p][index] = abilityId == null ? "" : abilityId;
        String key = p == 0 ? "slot" + (index + 1) : "profile" + (p + 1) + "_slot" + (index + 1);
        config.get(Configuration.CATEGORY_GENERAL, key, "", "").set(PROFILE_SLOT_ABILITY_IDS[p][index]);
        config.save();
    }

    public static String getProfileActiveAbility(int profile) {
        int p = clampProfile(profile);
        String id = PROFILE_ACTIVE_ABILITY[p];
        return id == null ? "" : id;
    }

    public static void setProfileActiveAbility(int profile, String abilityId) {
        int p = clampProfile(profile);
        PROFILE_ACTIVE_ABILITY[p] = abilityId == null ? "" : abilityId;
        String key = p == 0 ? "activeAbility" : "profile" + (p + 1) + "_active";
        config.get(Configuration.CATEGORY_GENERAL, key, "", "").set(PROFILE_ACTIVE_ABILITY[p]);
        config.save();
    }

    /* ---------------- 当前 Profile 的兼容入口 ---------------- */

    /** 当前 Profile 的槽位绑定 id。 */
    public static String getSlot(int index) {
        return getProfileSlot(activeProfile(), index);
    }

    public static void setSlot(int index, String abilityId) {
        setProfileSlot(activeProfile(), index, abilityId);
    }

    public static void clearSlot(int index) {
        setSlot(index, "");
    }

    /** 当前 Profile 选中的能力。 */
    public static String getActiveAbility() {
        return getProfileActiveAbility(activeProfile());
    }

    public static void setActiveAbility(String abilityId) {
        setProfileActiveAbility(activeProfile(), abilityId);
    }

    /* ---------------- 其他 ---------------- */

    /** HUD 位置修改，立即持久化。 */
    public static void setHudPosition(int horizontal, int vertical, int margin) {
        hudHorizontal = Math.max(0, Math.min(2, horizontal));
        hudVertical = Math.max(0, Math.min(2, vertical));
        hudMargin = Math.max(0, Math.min(200, margin));
        config.get("hud", "hudHorizontal", 0).set(hudHorizontal);
        config.get("hud", "hudVertical", 2).set(hudVertical);
        config.get("hud", "margin", 0).set(hudMargin);
        config.save();
    }

    public static void setMaxSlots(int value) {
        maxSlots = Math.max(1, Math.min(MAX_SLOTS_HARD_CAP, value));
        config.get(Configuration.CATEGORY_GENERAL, "maxSlots", MAX_SLOTS_HARD_CAP, "", 1, MAX_SLOTS_HARD_CAP).set(maxSlots);
        config.save();
    }

    /** 当前生效槽位数（受 maxSlots 限制）。 */
    public static int activeSlotCount() {
        return Math.max(1, Math.min(maxSlots, MAX_SLOTS_HARD_CAP));
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

package aaaa0ggmc.morphext.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * 轮盘配置：最大槽位数（1-8，可配置）与 8 个槽位绑定的能力 id。
 * 存于 config/aaaa0ggmc_morph_extension.cfg，游戏中改、即时生效。
 */
public class ModConfig {
    public static final int MAX_SLOTS_HARD_CAP = 8;

    private static Configuration config;

    public static int maxSlots = MAX_SLOTS_HARD_CAP;
    public static final String[] SLOT_ABILITY_IDS = new String[MAX_SLOTS_HARD_CAP];

    /** 当前选中的能力（轮盘切换的结果，按释放键时触发）。 */
    public static String activeAbility = "";
    public static int hudHorizontal = 0;
    public static int hudVertical = 2;
    public static int hudMargin = 0;

    public static void init(File file) {
        config = new Configuration(file);
        load();
    }

    public static void load() {
        hudHorizontal = config.getInt("hudHorizontal", "hud", 0, 0, 2, "0=left, 1=middle, 2=right");
        hudVertical = config.getInt("hudVertical", "hud", 2, 0, 2, "0=top, 1=center, 2=bottom");
        hudMargin = config.getInt("margin", "hud", 0, 0, 200, "HUD margin in GUI pixels");
        maxSlots = config.getInt("maxSlots", Configuration.CATEGORY_GENERAL,
                MAX_SLOTS_HARD_CAP, 1, MAX_SLOTS_HARD_CAP,
                "Max abilities bound to the wheel (the circle is evenly divided by this count)");
        for (int i = 0; i < MAX_SLOTS_HARD_CAP; i++) {
            SLOT_ABILITY_IDS[i] = config.get(
                    Configuration.CATEGORY_GENERAL, "slot" + (i + 1), "",
                    "Ability id bound to wheel slot " + (i + 1)
            ).getString();
        }
        activeAbility = config.get(
                Configuration.CATEGORY_GENERAL, "activeAbility", "",
                "Currently selected ability (triggered by the cast key)"
        ).getString();
        if (config.hasChanged()) {
            config.save();
        }
    }

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

    /** 轮盘切换：设置当前选中能力。 */
    public static void setActiveAbility(String abilityId) {
        activeAbility = abilityId == null ? "" : abilityId;
        config.get(Configuration.CATEGORY_GENERAL, "activeAbility", "", "").set(activeAbility);
        config.save();
    }

    public static void setSlot(int index, String abilityId) {
        if (index < 0 || index >= MAX_SLOTS_HARD_CAP) {
            return;
        }
        SLOT_ABILITY_IDS[index] = abilityId == null ? "" : abilityId;
        config.get(Configuration.CATEGORY_GENERAL, "slot" + (index + 1), "", "").set(SLOT_ABILITY_IDS[index]);
        config.save();
    }

    public static void clearSlot(int index) {
        setSlot(index, "");
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
}

package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 轮盘配置的“玩家+存档”存储值对象（{@code states["morphext:wheel"]}）。
 *
 * <p>包含各 profile 的槽位绑定、当前选中能力、激活的 profile 与 profile 显示名。
 * 结构性上限（profileCount / maxSlots）与按键绑定留在全局 {@code ModConfig}，
 * 不随存档变化；这里只保存随玩家/存档走的布局数据。</p>
 *
 * <p>不可变值对象；JSON 解析是宽容归一化（越界钳制、坏 id 清空、缺省补齐），
 * 因此 {@code fromJson(toJson(x))} 与 {@code toJson(fromJson(j))} 均稳定幂等，
 * 服务端收到客户端更新时也只会落下合法形状。</p>
 */
public final class WheelConfigState {
    /** 在 {@code PlayerAbilityData.states} 中的键。 */
    public static final String ID = "morphext:wheel";
    /** 槽位数硬上限（与 ModConfig.MAX_SLOTS_HARD_CAP 同源）。 */
    public static final int SLOT_COUNT = 8;
    /** Profile 数硬上限（与 ModConfig.MAX_PROFILES 同源）。 */
    public static final int PROFILE_COUNT = 6;
    /** Profile 显示名最大长度（超出截断）。 */
    public static final int MAX_NAME_LENGTH = 48;

    private final int activeProfile;
    private final String[][] slots;
    private final String[] activeAbilities;
    private final String[] names;

    private WheelConfigState(int activeProfile, String[][] slots, String[] activeAbilities, String[] names) {
        this.activeProfile = activeProfile;
        this.slots = slots;
        this.activeAbilities = activeAbilities;
        this.names = names;
    }

    /** 默认布局：全空槽位、默认名、无选中能力。迁移完成后新建的存档从这里开始。 */
    public static WheelConfigState defaults() {
        return of(0, new String[PROFILE_COUNT][SLOT_COUNT], new String[PROFILE_COUNT], new String[PROFILE_COUNT]);
    }

    /** 按给定原始值构造并归一化（供一次性迁移种子与客户端写回使用）。 */
    public static WheelConfigState of(int activeProfile, String[][] slots, String[] activeAbilities, String[] names) {
        int active = clampProfile(activeProfile);
        String[][] normalizedSlots = new String[PROFILE_COUNT][SLOT_COUNT];
        String[] normalizedActive = new String[PROFILE_COUNT];
        String[] normalizedNames = new String[PROFILE_COUNT];
        for (int p = 0; p < PROFILE_COUNT; p++) {
            for (int s = 0; s < SLOT_COUNT; s++) {
                normalizedSlots[p][s] = sanitizeId(slots != null && p < slots.length && slots[p] != null
                        && s < slots[p].length ? slots[p][s] : null);
            }
            normalizedActive[p] = sanitizeId(activeAbilities != null && p < activeAbilities.length
                    ? activeAbilities[p] : null);
            normalizedNames[p] = sanitizeName(names != null && p < names.length ? names[p] : null, p);
        }
        return new WheelConfigState(active, normalizedSlots, normalizedActive, normalizedNames);
    }

    /** 宽容解析：缺省补齐、越界钳制、坏值清空；任何形状的 JSON 都不会抛出。 */
    public static WheelConfigState fromJson(JsonObject json) {
        int active = 0;
        String[][] slots = new String[PROFILE_COUNT][SLOT_COUNT];
        String[] activeAbilities = new String[PROFILE_COUNT];
        String[] names = new String[PROFILE_COUNT];
        if (json == null) {
            return defaults();
        }
        active = clampProfile(readInt(json.get("activeProfile"), 0));
        JsonArray profiles = json.get("profiles") != null && json.get("profiles").isJsonArray()
                ? json.getAsJsonArray("profiles") : null;
        for (int p = 0; p < PROFILE_COUNT; p++) {
            JsonObject profile = profiles != null && p < profiles.size() && profiles.get(p).isJsonObject()
                    ? profiles.get(p).getAsJsonObject() : null;
            if (profile == null) {
                continue;
            }
            JsonArray bound = profile.get("slots") != null && profile.get("slots").isJsonArray()
                    ? profile.getAsJsonArray("slots") : null;
            if (bound != null) {
                for (int s = 0; s < SLOT_COUNT && s < bound.size(); s++) {
                    slots[p][s] = sanitizeId(readString(bound.get(s)));
                }
            }
            activeAbilities[p] = sanitizeId(readString(profile.get("active")));
            names[p] = sanitizeName(readString(profile.get("name")), p);
        }
        return of(active, slots, activeAbilities, names);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("activeProfile", activeProfile);
        JsonArray profiles = new JsonArray();
        for (int p = 0; p < PROFILE_COUNT; p++) {
            JsonObject profile = new JsonObject();
            profile.addProperty("name", names[p]);
            JsonArray bound = new JsonArray();
            for (int s = 0; s < SLOT_COUNT; s++) {
                bound.add(slots[p][s]);
            }
            profile.add("slots", bound);
            profile.addProperty("active", activeAbilities[p]);
            profiles.add(profile);
        }
        json.add("profiles", profiles);
        return json;
    }

    public int activeProfile() {
        return activeProfile;
    }

    public String slot(int profile, int index) {
        if (profile < 0 || profile >= PROFILE_COUNT || index < 0 || index >= SLOT_COUNT) {
            return "";
        }
        return slots[profile][index];
    }

    public String activeAbility(int profile) {
        if (profile < 0 || profile >= PROFILE_COUNT) {
            return "";
        }
        return activeAbilities[profile];
    }

    public String name(int profile) {
        if (profile < 0 || profile >= PROFILE_COUNT) {
            return "";
        }
        return names[profile];
    }

    public WheelConfigState withActiveProfile(int profile) {
        return of(clampProfile(profile), slots, activeAbilities, names);
    }

    public WheelConfigState withSlot(int profile, int index, String abilityId) {
        if (profile < 0 || profile >= PROFILE_COUNT || index < 0 || index >= SLOT_COUNT) {
            return this;
        }
        String[][] copy = copySlots();
        copy[profile][index] = sanitizeId(abilityId);
        return of(activeProfile, copy, activeAbilities, names);
    }

    public WheelConfigState withActiveAbility(int profile, String abilityId) {
        if (profile < 0 || profile >= PROFILE_COUNT) {
            return this;
        }
        String[] copy = activeAbilities.clone();
        copy[profile] = sanitizeId(abilityId);
        return of(activeProfile, slots, copy, names);
    }

    private String[][] copySlots() {
        String[][] copy = new String[PROFILE_COUNT][SLOT_COUNT];
        for (int p = 0; p < PROFILE_COUNT; p++) {
            copy[p] = slots[p].clone();
        }
        return copy;
    }

    private static int clampProfile(int profile) {
        return Math.max(0, Math.min(PROFILE_COUNT - 1, profile));
    }

    /** 能力 id 必须过 PlayerAbilityData 的 id 校验，否则清空（保留槽位但不绑非法 id）。 */
    private static String sanitizeId(String id) {
        return id == null || !PlayerAbilityData.validId(id) ? "" : id;
    }

    private static String sanitizeName(String name, int profile) {
        if (name == null) {
            return "Profile " + (profile + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length() && sb.length() < MAX_NAME_LENGTH; i++) {
            char c = name.charAt(i);
            if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        String trimmed = sb.toString().trim();
        return trimmed.isEmpty() ? "Profile " + (profile + 1) : trimmed;
    }

    private static int readInt(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsInt();
    }

    private static String readString(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }
}

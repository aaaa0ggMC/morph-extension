package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/** 单个玩家的数据值对象；文件提交成功后才替换缓存，不暴露内部可变 JSON。 */
public final class PlayerAbilityData {
    public static final int SCHEMA = 1;
    private final JsonObject root;

    public PlayerAbilityData(UUID owner) {
        root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("owner", owner.toString());
        root.addProperty("cooldownClock", "overworld_total_ticks");
        root.add("unlocked", new JsonArray());
        root.add("removed", new JsonArray());
        root.add("states", new JsonObject());
        root.add("cooldowns", new JsonObject());
    }

    public PlayerAbilityData(UUID owner, JsonObject json) {
        root = copy(json);
        if (!root.has("schema") || !root.get("schema").isJsonPrimitive()
                || !root.get("schema").getAsJsonPrimitive().isNumber()
                || !Integer.toString(SCHEMA).equals(root.get("schema").getAsString())
                || !root.has("owner") || !root.get("owner").isJsonPrimitive()
                || !root.get("owner").getAsJsonPrimitive().isString()
                || !owner.toString().equals(root.get("owner").getAsString())) {
            throw new IllegalArgumentException("Unsupported player data schema or owner mismatch");
        }
        for (String key : new String[]{"unlocked", "removed"}) {
            if (!root.has(key) || !root.get(key).isJsonArray()) {
                throw new IllegalArgumentException("Invalid permission array: " + key);
            }
            for (JsonElement element : root.getAsJsonArray(key)) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                        || !validId(element.getAsString())) {
                    throw new IllegalArgumentException("Invalid ability id");
                }
            }
        }
        if (!root.has("states") || !root.get("states").isJsonObject()
                || !root.has("cooldowns") || !root.get("cooldowns").isJsonObject()) {
            throw new IllegalArgumentException("Invalid player state objects");
        }
        if (root.has("cooldownClock") && !"overworld_total_ticks".equals(root.get("cooldownClock").getAsString())) {
            throw new IllegalArgumentException("Unsupported cooldown clock");
        }
        root.addProperty("cooldownClock", "overworld_total_ticks");
        for (java.util.Map.Entry<String, JsonElement> entry : root.getAsJsonObject("states").entrySet()) {
            if (!validId(entry.getKey()) || !entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Invalid ability state");
            }
        }
        for (java.util.Map.Entry<String, JsonElement> entry : root.getAsJsonObject("cooldowns").entrySet()) {
            if (!validId(entry.getKey()) || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isNumber() || entry.getValue().getAsLong() < 0) {
                throw new IllegalArgumentException("Invalid cooldown deadline");
            }
        }
    }

    public static boolean validId(String id) {
        if (id == null || id.isEmpty() || id.length() > 256) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (Character.isWhitespace(id.charAt(i)) || Character.isISOControl(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static JsonObject copy(JsonObject json) {
        return new JsonParser().parse(json.toString()).getAsJsonObject();
    }

    public JsonObject toJson() { return copy(root); }

    public boolean isAllowed(String id, boolean naturallyAvailable) {
        return !contains("removed", id) && (naturallyAvailable || contains("unlocked", id));
    }

    public boolean isRemoved(String id) { return contains("removed", id); }

    private boolean contains(String key, String id) {
        for (JsonElement value : root.getAsJsonArray(key)) {
            if (id.equals(value.getAsString())) { return true; }
        }
        return false;
    }

    private void remove(String key, String id) {
        JsonArray values = new JsonArray();
        for (JsonElement value : root.getAsJsonArray(key)) {
            if (!id.equals(value.getAsString())) { values.add(value); }
        }
        root.add(key, values);
    }

    public void setUnlocked(String id, boolean unlocked) {
        if (!validId(id)) { throw new IllegalArgumentException("Invalid ability id"); }
        remove("unlocked", id);
        remove("removed", id);
        root.getAsJsonArray(unlocked ? "unlocked" : "removed").add(id);
    }

    public JsonObject getState(String id) {
        JsonElement value = root.getAsJsonObject("states").get(id);
        return value == null ? new JsonObject() : copy(value.getAsJsonObject());
    }

    public void putState(String id, JsonObject state) {
        if (!validId(id)) { throw new IllegalArgumentException("Invalid ability id"); }
        root.getAsJsonObject("states").add(id, copy(state));
    }

    public void wipe(String id) {
        root.getAsJsonObject("states").remove(id);
        root.getAsJsonObject("cooldowns").remove(id);
    }

    public int getCooldown(String id, long now) {
        JsonElement deadline = root.getAsJsonObject("cooldowns").get(id);
        if (deadline == null) { return 0; }
        long remaining = deadline.getAsLong() - now;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, remaining));
    }

    public void setCooldown(String id, long now, int ticks) {
        if (!validId(id)) { throw new IllegalArgumentException("Invalid ability id"); }
        if (ticks > 0) { root.getAsJsonObject("cooldowns").addProperty(id, now + ticks); }
        else { root.getAsJsonObject("cooldowns").remove(id); }
    }

    /** 快照仍使用截止时间戳，附带服务端当前 tick 供客户端初始化本地预测。 */
    public JsonObject snapshot(long now) {
        JsonObject snapshot = toJson();
        snapshot.addProperty("serverTick", now);
        return snapshot;
    }
}

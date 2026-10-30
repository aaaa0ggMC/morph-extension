package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;

import java.io.File;

/**
 * 当前客户端连接的只读能力状态镜像。
 *
 * 不用 NBT 的原因：状态结构由各能力自定义（布尔/枚举/数值/复合结构都可能），
 * 数量多时 NBT 二进制既不可读也不好维护；JSON 可读、可手改、可diff。
 *
 * 权威文件由 PlayerAbilityStore 按存档/UUID 保存。此类不再写旧全局文件。
 */
public final class AbilityStateStore {
    private static JsonObject root = new JsonObject();

    private AbilityStateStore() {
    }

    public static void init(File modConfigDirectory) {
        load();
    }

    public static void load() {
        root = new JsonObject();
    }

    public static void replaceSnapshot(JsonObject states) {
        root = PlayerAbilityData.copy(states);
        applyToAbilities();
    }

    /** 取某能力的存档状态（没有则返回空对象）。 */
    public static JsonObject get(String abilityId) {
        if (root.has(abilityId) && root.get(abilityId).isJsonObject()) {
            return PlayerAbilityData.copy(root.getAsJsonObject(abilityId));
        }
        return new JsonObject();
    }

    /** @deprecated 请通过服务端 PlayerAbilityStore.update 写入玩家状态。 */
    @Deprecated
    public static void put(String abilityId, JsonObject state) {
        throw new UnsupportedOperationException("Use PlayerAbilityStore.update on the server");
    }

    /** @deprecated 请通过服务端 PlayerAbilityStore.update 清除玩家状态。 */
    @Deprecated
    public static void remove(String abilityId) {
        throw new UnsupportedOperationException("Use PlayerAbilityStore.update on the server");
    }

    @Deprecated
    public static void flush() {
        throw new UnsupportedOperationException("Client state mirror is read-only");
    }

    /** 把存档状态推送给所有已注册的状态型能力（登录世界时调用）。 */
    public static void applyToAbilities() {
        for (IAbility ability : AbilityRegistry.all()) {
            if (ability instanceof IStatefulAbility) {
                ((IStatefulAbility) ability).loadState(get(ability.getId()));
            }
        }
    }
}

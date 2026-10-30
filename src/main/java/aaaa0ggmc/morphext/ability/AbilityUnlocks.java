package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前客户端连接的能力权限镜像，不作为服务端授权依据。
 *
 * 未解锁的能力：搜索不到、不能绑定、不能释放。
 * 服务端 addon 在条件达成时使用 PlayerAbilityStore.update，再向该玩家同步快照。
 * 旧全局 JSON 只在单人存档主人首次迁移时读取，本类不再读写该文件。
 */
public final class AbilityUnlocks {
    private static final Set<String> UNLOCKED = new LinkedHashSet<>();
    private static final Set<String> REMOVED = new LinkedHashSet<>();
    private static boolean ready;

    private AbilityUnlocks() {
    }

    public static void init(File modConfigDirectory) {
        // 旧文件仅由 PlayerAbilityStore 在单人存档主人首次导入时读取。
        load();
    }

    public static void load() {
        UNLOCKED.clear();
        REMOVED.clear();
        ready = false;
    }

    /** 客户端主线程：只接受服务端快照，未同步时默认不可用。 */
    public static void replaceSnapshot(JsonObject snapshot) {
        UNLOCKED.clear();
        REMOVED.clear();
        readArray(snapshot, "unlocked", UNLOCKED);
        readArray(snapshot, "removed", REMOVED);
        ready = true;
    }

    public static boolean isReady() { return ready; }

    private static void readArray(JsonObject root, String key, Set<String> target) {
        JsonArray array = root.getAsJsonArray(key);
        if (array != null) {
            for (JsonElement element : array) {
                target.add(element.getAsString());
            }
        }
    }

    /** 该能力当前是否被玩家拥有（天生能力可被卷轴显式剔除；变身临时能力由当前形态保证）。 */
    public static boolean isUnlocked(IAbility ability) {
        if (!ready || ability == null) {
            return false;
        }
        // 变身临时授予的能力（包含被动与动作）属于当前形态专属，不受旧 removed 污染影响
        if (AbilityRegistry.isGranted(ability.getId())) {
            return true;
        }
        if (REMOVED.contains(ability.getId())) {
            return false;
        }
        if (ability.isInnate()) {
            return true;
        }
        return UNLOCKED.contains(ability.getId());
    }

    public static boolean isUnlocked(String abilityId) {
        if (!ready || abilityId == null) { return false; }
        if (AbilityRegistry.isGranted(abilityId)) {
            return true;
        }
        if (REMOVED.contains(abilityId)) { return false; }
        IAbility ability = AbilityRegistry.get(abilityId);
        return ability != null ? isUnlocked(ability) : UNLOCKED.contains(abilityId);
    }

    /** @deprecated 权威变更请用服务端 PlayerAbilityStore.update，随后同步快照。 */
    @Deprecated
    public static boolean unlock(String abilityId) {
        throw new UnsupportedOperationException("Use PlayerAbilityStore.update on the server");
    }

    /** @deprecated 权威变更请用 PlayerAbilityStore.update。 */
    @Deprecated
    public static boolean lock(String abilityId) {
        throw new UnsupportedOperationException("Use PlayerAbilityStore.update on the server");
    }

    @Deprecated
    public static void flush() {
        throw new UnsupportedOperationException("Client permission mirror is read-only");
    }

}

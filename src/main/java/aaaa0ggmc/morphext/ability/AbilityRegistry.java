package aaaa0ggmc.morphext.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 能力注册表（addon 的唯一入口）。
 *
 * 用法（addon 模组中）：
 * <pre>
 *     AbilityRegistry.register(new Ability("mymod:fire_breath", "Fire Breath") {
 *         public void onActivate(EntityPlayer player) { ... }
 *     });
 * </pre>
 */
public class AbilityRegistry {
    private static final Map<String, IAbility> ABILITIES = new LinkedHashMap<>();
    private static final Map<String, Map<String, IAbility>> GRANTS = new LinkedHashMap<>();
    /**
     * 最近一次见过的能力原型（包含已撤销的变身临时能力）。
     * 轮盘槽位需要在能力暂时不可用时仍能显示名称/图标并标记为“已遗忘”，
     * 因此 get() 允许回退到这份只读缓存；是否可释放仍由 AbilityUnlocks 判定。
     */
    private static final Map<String, IAbility> KNOWN = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register(IAbility ability) {
        if (ability != null && ability.getId() != null && !ability.getId().isEmpty()) {
            ABILITIES.put(ability.getId(), ability);
            KNOWN.put(ability.getId(), ability);
        }
    }

    public static void unregister(String id) {
        if (id != null) {
            ABILITIES.remove(id);
        }
    }

    public static IAbility get(String id) {
        if (id == null) {
            return null;
        }
        IAbility ability = ABILITIES.get(id);
        if (ability != null) {
            return ability;
        }
        for (Map<String, IAbility> grants : GRANTS.values()) {
            if (grants.containsKey(id)) {
                return grants.get(id);
            }
        }
        return KNOWN.get(id);
    }

    public static Collection<IAbility> all() {
        Map<String, IAbility> snapshot = new LinkedHashMap<>(ABILITIES);
        for (Map<String, IAbility> grants : GRANTS.values()) {
            for (IAbility ability : grants.values()) {
                snapshot.putIfAbsent(ability.getId(), ability);
            }
        }
        return new ArrayList<>(snapshot.values());
    }

    public static boolean isGranted(String id) {
        for (Map<String, IAbility> grants : GRANTS.values()) {
            if (grants.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    /** 获取当前所有已授权/激活的临时能力，避免全量遍历所有注册能力 */
    public static Collection<IAbility> getGrantedAbilities() {
        Map<String, IAbility> result = new LinkedHashMap<>();
        for (Map<String, IAbility> grants : GRANTS.values()) {
            result.putAll(grants);
        }
        return result.values();
    }

    /** 客户端主线程：按来源差量替换临时能力，不写永久解锁文件，不重复触发生命周期。 */
    public static void replaceGrants(String source, Collection<IAbility> abilities, EntityPlayer player) {
        if (source == null || source.isEmpty()) {
            return;
        }
        if (abilities == null) {
            abilities = java.util.Collections.emptyList();
        }
        Map<String, IAbility> old = GRANTS.getOrDefault(source, java.util.Collections.emptyMap());
        Map<String, IAbility> next = new LinkedHashMap<>();
        for (IAbility ability : abilities) {
            if (ability == null || ability.getId() == null || ability.getId().isEmpty()) {
                continue;
            }
            // 支持动态装载已注册能力（如 UAPA 架构中已在全局 ABILITIES 注册的被动与动作原型）
            next.put(ability.getId(), old.getOrDefault(ability.getId(), ability));
            KNOWN.putIfAbsent(ability.getId(), ability);
        }
        GRANTS.put(source, next);
        for (IAbility ability : old.values()) {
            if (!next.containsKey(ability.getId())) {
                try {
                    ability.onRevoked(player);
                } catch (Throwable t) {
                    // Safe guard
                }
            }
        }
        for (IAbility ability : next.values()) {
            if (!old.containsKey(ability.getId())) {
                try {
                    ability.onGranted(player);
                } catch (Throwable t) {
                    // Safe guard
                }
            }
        }
    }

    public static void clearGrants(EntityPlayer player) {
        for (String source : new ArrayList<>(GRANTS.keySet())) {
            replaceGrants(source, java.util.Collections.emptyList(), player);
        }
        GRANTS.clear();
    }

    /** 玩家当前拥有（已解锁）的能力。 */
    public static Collection<IAbility> available() {
        List<IAbility> out = new ArrayList<>();
        for (IAbility ability : all()) {
            if (AbilityUnlocks.isUnlocked(ability) && !ability.isPassive()) {
                out.add(ability);
            }
        }
        return out;
    }

    public static int count() {
        return all().size();
    }

    /** 按 id / 名称模糊搜索（忽略大小写）；空查询返回全部已解锁能力。 */
    public static List<IAbility> search(String query) {
        List<IAbility> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            out.addAll(available());
            return out;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (IAbility ability : available()) {
            if (ability.getId().toLowerCase(Locale.ROOT).contains(q)
                    || ability.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(ability);
            }
        }
        return out;
    }
}

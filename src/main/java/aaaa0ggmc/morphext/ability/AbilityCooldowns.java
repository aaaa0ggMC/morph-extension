package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力冷却追踪 + 冷却缩减注册表。
 *
 * 设计：
 *  - 冷却按能力 id 追踪（tick），到点自动解除
 *  - 基础冷却由能力 {@link IAbility#getCooldownTicks(EntityPlayer)} 给出
 *  - 缩减通过 {@link CooldownReducer} 注册表叠加——预留给 trinkets（饰品）和
 *    “能力本身缩减冷却”这类后期机制，只需 registerReducer
 */
public final class AbilityCooldowns {
    private static final Map<String, Integer> COOLDOWNS = new HashMap<>();
    private static final Map<String, Integer> TOTALS = new HashMap<>();
    private static final Map<String, Integer> EXTERNAL = new HashMap<>();
    private static final Map<String, Integer> EXTERNAL_TOTALS = new HashMap<>();
    private static final Map<String, Integer> CONFIRMED = new HashMap<>();
    private static final Map<String, Integer> CONFIRMED_TOTALS = new HashMap<>();
    private static final List<CooldownReducer> REDUCERS = new ArrayList<>();

    private AbilityCooldowns() {
    }

    /** 每个 client tick 调用（由 AbilityTickHandler 驱动）。 */
    public static void tick() {
        decrement(COOLDOWNS, TOTALS);
        decrement(CONFIRMED, CONFIRMED_TOTALS);
    }

    private static void decrement(Map<String, Integer> remaining, Map<String, Integer> totals) {
        remaining.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            if (left <= 0) {
                totals.remove(entry.getKey());
                return true;
            }
            entry.setValue(left);
            return false;
        });
    }

    public static boolean isReady(String abilityId) {
        return getRemaining(abilityId) <= 0;
    }

    /** 剩余冷却（tick），0 表示就绪。 */
    public static int getRemaining(String abilityId) {
        return Math.max(CONFIRMED.getOrDefault(abilityId, 0),
                Math.max(COOLDOWNS.getOrDefault(abilityId, 0), EXTERNAL.getOrDefault(abilityId, 0)));
    }

    /** 总冷却（tick），用于进度条比例。 */
    public static int getTotal(String abilityId) {
        if (CONFIRMED.getOrDefault(abilityId, 0) > 0
                && CONFIRMED.getOrDefault(abilityId, 0) == getRemaining(abilityId)) {
            return CONFIRMED_TOTALS.getOrDefault(abilityId, 0);
        }
        return EXTERNAL.getOrDefault(abilityId, 0) > COOLDOWNS.getOrDefault(abilityId, 0)
                ? EXTERNAL_TOTALS.getOrDefault(abilityId, 0) : TOTALS.getOrDefault(abilityId, 0);
    }

    /** 剩余比例 0~1（1=刚开始冷却，0=就绪）。 */
    public static float getRatio(String abilityId) {
        int total = getTotal(abilityId);
        if (total <= 0) {
            return 0F;
        }
        return Math.min(1F, Math.max(0F, (float) getRemaining(abilityId) / total));
    }

    /**
     * 同步“外部冷却”（能力自带体系里的冷却，如 Metamorph 的 MOB_ABILITY_COOLDOWN 药效）。
     * 每 tick 读取客户端已同步的权威剩余量，0 表示外部冷却已结束。
     * 外部冷却单独存储，不本地递减、不覆盖能力自身冷却；重复采样不重置进度条总量。
     */
    public static void syncExternal(String abilityId, int remaining) {
        if (abilityId == null) {
            return;
        }
        if (remaining <= 0) {
            EXTERNAL.remove(abilityId);
            EXTERNAL_TOTALS.remove(abilityId);
            return;
        }
        int oldRemaining = EXTERNAL.getOrDefault(abilityId, 0);
        if (remaining > oldRemaining || !EXTERNAL_TOTALS.containsKey(abilityId)) {
            /* 重新释放：剩余跳升，视为新一轮冷却，重记总量 */
            EXTERNAL_TOTALS.put(abilityId, remaining);
        }
        EXTERNAL.put(abilityId, remaining);
    }

    /** 退出世界后清除客户端缓存，避免下一次进入世界继承冷却显示。 */
    public static void clear() {
        COOLDOWNS.clear();
        TOTALS.clear();
        EXTERNAL.clear();
        EXTERNAL_TOTALS.clear();
        CONFIRMED.clear();
        CONFIRMED_TOTALS.clear();
    }

    /** 服务端冷却确认；重复的拒绝响应不得把进度条总量重置为剩余时间。 */
    public static void confirm(String abilityId, int ticks) {
        if (ticks > 0) {
            // 服务端已经计算过冷却，不再应用客户端缩减器。
            CONFIRMED.put(abilityId, ticks);
            CONFIRMED_TOTALS.put(abilityId, Math.max(CONFIRMED_TOTALS.getOrDefault(abilityId, 0), ticks));
        }
    }

    public static void replaceConfirmed(com.google.gson.JsonObject cooldowns, long serverTick) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : cooldowns.entrySet()) {
            long remaining = entry.getValue().getAsLong() - serverTick;
            if (remaining > 0) {
                ids.add(entry.getKey());
                confirm(entry.getKey(), (int) Math.min(Integer.MAX_VALUE, remaining));
            }
        }
        CONFIRMED.keySet().retainAll(ids);
        CONFIRMED_TOTALS.keySet().retainAll(ids);
    }

    public static void clearAbility(String id) {
        COOLDOWNS.remove(id);
        TOTALS.remove(id);
        EXTERNAL.remove(id);
        EXTERNAL_TOTALS.remove(id);
        CONFIRMED.remove(id);
        CONFIRMED_TOTALS.remove(id);
    }

    /** 剩余冷却（秒，一位小数）。 */
    public static String getRemainingText(String abilityId) {
        int ticks = getRemaining(abilityId);
        if (ticks <= 0) {
            return "0.0";
        }
        return String.format("%.1f", ticks / 20.0D);
    }

    /** 开始冷却：基础 tick 经过所有已注册缩减器后生效。 */
    public static void start(EntityPlayer player, String abilityId, int baseTicks) {
        if (abilityId == null || baseTicks <= 0) {
            return;
        }
        int ticks = baseTicks;
        for (CooldownReducer reducer : REDUCERS) {
            ticks = reducer.reduce(player, abilityId, ticks);
        }
        if (ticks > 0) {
            COOLDOWNS.put(abilityId, ticks);
            TOTALS.put(abilityId, ticks);
        } else {
            COOLDOWNS.remove(abilityId);
            TOTALS.remove(abilityId);
        }
    }

    /** 注册冷却缩减器（trinkets / 能力特效后期接入点）。 */
    public static void registerReducer(CooldownReducer reducer) {
        if (reducer != null && !REDUCERS.contains(reducer)) {
            REDUCERS.add(reducer);
        }
    }

    public interface CooldownReducer {
        /** 返回缩减后的冷却 tick（不小于 0）。 */
        int reduce(EntityPlayer player, String abilityId, int baseTicks);
    }
}

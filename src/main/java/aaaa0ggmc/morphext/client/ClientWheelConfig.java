package aaaa0ggmc.morphext.client;

import aaaa0ggmc.morphext.ability.WheelConfigState;
import aaaa0ggmc.morphext.config.ModConfig;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import com.google.gson.JsonObject;

/**
 * 客户端轮盘配置镜像（玩家+存档数据 {@code states["morphext:wheel"]}）。
 *
 * <p>读取走本地缓存：登录快照（{@link ClientPlayerData#accept}）整份刷新，
 * 本地 UI 改动乐观更新缓存并把完整状态发给服务端，由服务端写进
 * {@code <存档>/data/morphext/players/<uuid>.json} 后随下一次快照权威回传。
 * 联机下客户端不接触存档文件，服务端是唯一权威。</p>
 */
public final class ClientWheelConfig {
    private static WheelConfigState state = WheelConfigState.defaults();

    private ClientWheelConfig() { }

    /** 快照到达时整份替换（快照缺失该键时回到默认布局）。 */
    public static void replaceSnapshot(JsonObject states) {
        JsonObject wheel = states != null && states.has(WheelConfigState.ID)
                && states.get(WheelConfigState.ID).isJsonObject()
                ? states.getAsJsonObject(WheelConfigState.ID) : null;
        state = wheel == null ? WheelConfigState.defaults() : WheelConfigState.fromJson(wheel);
    }

    /** 断开连接/换玩家时重置。 */
    public static void reset() {
        state = WheelConfigState.defaults();
    }

    /** 当前激活 profile（0-based），始终钳制在 profileCount 内。 */
    public static int activeProfile() {
        return Math.max(0, Math.min(ModConfig.profileCount() - 1, state.activeProfile()));
    }

    public static String getSlot(int index) {
        return state.slot(activeProfile(), index);
    }

    public static String getActiveAbility() {
        return state.activeAbility(activeProfile());
    }

    public static String getProfileName(int profile) {
        return state.name(Math.max(0, Math.min(ModConfig.profileCount() - 1, profile)));
    }

    public static void setActiveProfile(int profile) {
        int current = activeProfile();
        int next = Math.max(0, Math.min(ModConfig.profileCount() - 1, profile));
        if (next != current) {
            commit(state.withActiveProfile(next));
        }
    }

    /** 向当前 profile 的槽位写绑定（空串=清槽；能力遗忘/未解锁不清槽，由轮盘显示锁定）。 */
    public static void setSlot(int index, String abilityId) {
        if (index < 0 || index >= WheelConfigState.SLOT_COUNT) {
            return;
        }
        commit(state.withSlot(activeProfile(), index, abilityId));
    }

    public static void setActiveAbility(String abilityId) {
        commit(state.withActiveAbility(activeProfile(), abilityId));
    }

    /** 乐观更新本地缓存并把完整状态发往服务端；服务端落盘后随快照权威回传。 */
    private static void commit(WheelConfigState next) {
        int clamped = Math.max(0, Math.min(ModConfig.profileCount() - 1, next.activeProfile()));
        if (clamped != next.activeProfile()) {
            next = next.withActiveProfile(clamped);
        }
        state = next;
        AbilityNetwork.sendWheelConfig(next.toJson());
    }
}

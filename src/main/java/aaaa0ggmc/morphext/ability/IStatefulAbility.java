package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;

import java.util.Collections;
import java.util.List;

/**
 * 有状态的能力：HUD 会显示其状态标签与附加信息。
 *
 * 状态从当前玩家的服务端快照或世界推导，不应将多人状态保存在共享能力实例字段中，
 * 因此切换选中其他能力不会影响它——状态类能力的效果持续到被再次触发或自然结束。
 */
public interface IStatefulAbility extends IAbility {
    /** 状态短标签，如 "On" / "Off" / "Ready"。 */
    String getStateLabel(EntityPlayer player);

    /** 附加信息行（HUD 显示用），可为空。 */
    default List<String> getStateInfo(EntityPlayer player) {
        return Collections.emptyList();
    }

    /** 该状态当前是否处于“已修改/生效中”（总览页面据此过滤）。 */
    default boolean isActive(EntityPlayer player) {
        return false;
    }

    /**
     * 状态持续期间每个 client tick 调用——持久化效果在这里施加
     * （如疾跑：开启期间每 tick 保持 setSprinting(true)）。
     */
    default void onTick(EntityPlayer player) {
    }

    /** 旧客户端能力的兼容接口；新服务端能力用 PlayerAbilityStore 保存玩家状态。 */
    default com.google.gson.JsonObject saveState() {
        return new com.google.gson.JsonObject();
    }

    /** 客户端收到当前连接的状态快照时调用；空对象表示没有该能力的存档状态。 */
    default void loadState(com.google.gson.JsonObject state) {
    }
}

package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;

/**
 * 主动能力接口 (Active Ability Contract)。
 * 适用于需要通过轮盘、快捷键或生物动作按键触发的技能。
 */
public interface IActiveAbility extends IAbility {

    @Override
    default AbilityType getType() {
        return AbilityType.ACTIVE;
    }

    @Override
    default boolean isPassive() {
        return false;
    }

    /**
     * 激活能力。
     * @param player 释放技能的玩家
     * @param context 变身上下文
     * @return 激活结果及进入的冷却时间（tick）
     */
    ActivationResult activate(EntityPlayer player, MorphContext context);

    @Override
    default ActivationResult activate(EntityPlayer player) {
        return activate(player, new MorphContext(player, null));
    }

    /**
     * 基础冷却时间（tick，20 ticks = 1 秒）。
     */
    @Override
    default int getCooldownTicks(EntityPlayer player) {
        return 0;
    }

    /**
     * 执行侧：主动能力默认在服务端安全执行。
     */
    @Override
    default ExecutionSide getExecutionSide() {
        return ExecutionSide.SERVER;
    }

    /**
     * 是否支持准星视线自由释放（无需强行锁定具体实体）。
     */
    default boolean supportsFreeFire() {
        return true;
    }
}

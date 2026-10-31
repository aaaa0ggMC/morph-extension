package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;

/**
 * 冷却代理 (Cooldown Action Proxy)。
 * <p>
 * metamorph 原版动作（Fireball / Teleport / Potions 等）只实现 {@link IAction}，
 * 服务端执行通道（MetamorphServerAbilities）对非 ICooldownAction 恒返回 0 冷却，
 * 可以无间隔连发。本代理以 1:1 委托方式包装这些原版动作，在不改动 metamorph 源码、
 * 不改变其执行语义的前提下补上固有冷却，满足"主动动作必须有 ICooldownAction 冷却"的
 * 平衡红线。注册时以相同 id 写入 MorphManager.INSTANCE.actions
 * （与 shulker_bullet / mob_act / ai_target 的既有覆盖惯例一致）。
 */
public class CooldownActionProxy implements IAction, ICooldownAction {
    private final IAction delegate;
    private final int cooldownTicks;

    public CooldownActionProxy(IAction delegate, int cooldownTicks) {
        this.delegate = delegate;
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return cooldownTicks;
    }

    /** 供 GatedActionProxy 别名归一：返回被代理的原版动作实现。 */
    public IAction getDelegate() {
        return delegate;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (delegate != null) {
            delegate.execute(entity, morph);
        }
    }
}

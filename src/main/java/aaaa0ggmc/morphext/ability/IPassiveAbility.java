package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/**
 * 被动能力契约 (Passive Ability Contract)。
 * 被动能力拥有完整的全生命周期钩子，彻底告别死板硬编码，具备高度模块化和可扩展性。
 */
public interface IPassiveAbility extends IAbility {

    @Override
    default AbilityType getType() {
        return AbilityType.PASSIVE;
    }

    @Override
    default boolean isPassive() {
        return true;
    }

    @Override
    default boolean isStateToggleable() {
        return false;
    }

    /**
     * 形态装载/能力生效时触发（可用于注册属性修饰符、重置状态等）。
     */
    default void onApplied(EntityPlayer player, MorphContext context) {}

    /**
     * 形态卸载/解除变身/能力移除时触发（可用于清除属性修饰符、还原状态等）。
     */
    default void onRemoved(EntityPlayer player, MorphContext context) {}

    /**
     * 周期性 tick 驱动（在玩家 Tick 的 Phase.END 调用）。
     */
    default void onUpdate(EntityPlayer player, MorphContext context) {}

    /**
     * 受到伤害时的判定钩子。
     * @param player 受击玩家
     * @param source 伤害源
     * @param amount 伤害数值
     * @param context 变身上下文
     * @return true 表示完全免疫/取消该次伤害并阻断后续伤害计算；false 表示正常承受
     */
    default boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        return false;
    }

    /**
     * 对其他目标发起攻击时的判定钩子（可用于附加攻击特效、攻击附加药水等）。
     * @param player 攻击者
     * @param target 被攻击的目标
     * @param source 伤害源
     * @param amount 伤害数值
     * @param context 变身上下文
     */
    default void onAttack(EntityPlayer player, EntityLivingBase target, DamageSource source, float amount, MorphContext context) {}

    /**
     * 试图被施加药水效果时的判定钩子。
     * @param player 宿主玩家
     * @param effect 欲施加的药水效果
     * @param context 变身上下文
     * @return true 表示完全免疫该药水效果，阻止其附加
     */
    default boolean onPotionApplicable(EntityPlayer player, PotionEffect effect, MorphContext context) {
        return false;
    }

    /**
     * 与其他实体发生物理接触/碰撞时的钩子。
     * @param player 宿主玩家
     * @param other 碰撞的实体
     * @param context 变身上下文
     */
    default void onCollide(EntityPlayer player, EntityLivingBase other, MorphContext context) {}
}

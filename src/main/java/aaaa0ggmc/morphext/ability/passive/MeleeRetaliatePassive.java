package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

/**
 * 近身反噬被动 (Melee Retaliate Passive)。
 * <p>
 * 被近身实体攻击命中时，攻击者遭到躯体特性的反噬：
 * 毒液反噬（POISON）、黏胶拖拽（SLOWNESS）、墨汁遮眼（BLINDNESS）或熔岩灼体（BURN）。
 * 仅对近身接触生效（箭矢等投射物的直接来源不是活体，不会触发）。
 */
public class MeleeRetaliatePassive extends AbstractPassiveAbility {

    public enum Effect {
        POISON,
        SLOWNESS,
        BLINDNESS,
        BURN
    }

    private final Effect effect;
    private final int amplifier;
    private final int durationTicks;

    public MeleeRetaliatePassive(String id, String nameKey, String descKey, ItemStack icon,
                                 Effect effect, int amplifier, int durationTicks) {
        super(id, nameKey, descKey, icon);
        this.effect = effect;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    public MeleeRetaliatePassive(String id, Effect effect, int amplifier, int durationTicks) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc",
                ItemStack.EMPTY, effect, amplifier, durationTicks);
    }

    /** 反噬降级伤害类型（镜像局免疫降级用）：命中此类型的伤害不再触发反噬，阻断链式递归。 */
    public static final String REFLECT_DAMAGE_TYPE = "morphext.reflect";

    /** 反噬降级伤害源：标记为魔法伤害，荆棘系被动的魔法分支不会连锁回弹。 */
    public static DamageSource reflectSource(EntityLivingBase reflector) {
        return new EntityDamageSource(REFLECT_DAMAGE_TYPE, reflector).setMagicDamage();
    }

    /** 判断是否为反噬降级伤害（反噬系被动入口自检，防镜像局递归互殴）。 */
    public static boolean isReflectDamage(DamageSource source) {
        return source != null && REFLECT_DAMAGE_TYPE.equals(source.getDamageType());
    }

    @Override
    public float onHurt(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || source == null || amount <= 0
                || isReflectDamage(source)) {
            return amount;
        }
        Entity attacker = source.getImmediateSource();
        if (!(attacker instanceof EntityLivingBase) || attacker == player) {
            return amount;
        }

        EntityLivingBase target = (EntityLivingBase) attacker;
        switch (effect) {
            case POISON:
                applyOrFallback(target, new PotionEffect(MobEffects.POISON, durationTicks, amplifier, false, true), player);
                break;
            case SLOWNESS:
                applyOrFallback(target, new PotionEffect(MobEffects.SLOWNESS, durationTicks, amplifier, false, true), player);
                break;
            case BLINDNESS:
                applyOrFallback(target, new PotionEffect(MobEffects.BLINDNESS, durationTicks, amplifier, false, true), player);
                break;
            case BURN:
                if (target.isImmuneToFire()) {
                    // 镜像局（火免对手）降级为无属性附加小额反噬伤害，避免整套反噬互废（CORE-05）
                    fallbackDamage(player, target);
                } else {
                    target.setFire(Math.max(1, durationTicks / 20));
                }
                break;
            default:
                break;
        }
        return amount;
    }

    /** 施效前预检目标免疫：被 DENY 时降级为小额直接反噬伤害（镜像对局不再整套互废）。 */
    private void applyOrFallback(EntityLivingBase target, PotionEffect effect, EntityPlayer player) {
        if (target.isPotionApplicable(effect)) {
            target.addPotionEffect(effect);
        } else {
            fallbackDamage(player, target);
        }
    }

    private void fallbackDamage(EntityPlayer player, EntityLivingBase target) {
        target.attackEntityFrom(reflectSource(player), 2.0F);
    }
}

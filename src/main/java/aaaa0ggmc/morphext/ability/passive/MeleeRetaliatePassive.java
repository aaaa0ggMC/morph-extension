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

    @Override
    public float onHurt(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || source == null || amount <= 0) {
            return amount;
        }
        Entity attacker = source.getImmediateSource();
        if (!(attacker instanceof EntityLivingBase) || attacker == player) {
            return amount;
        }

        EntityLivingBase target = (EntityLivingBase) attacker;
        switch (effect) {
            case POISON:
                target.addPotionEffect(new PotionEffect(MobEffects.POISON, durationTicks, amplifier, false, true));
                break;
            case SLOWNESS:
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, durationTicks, amplifier, false, true));
                break;
            case BLINDNESS:
                target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, durationTicks, amplifier, false, true));
                break;
            case BURN:
                target.setFire(Math.max(1, durationTicks / 20));
                break;
            default:
                break;
        }
        return amount;
    }
}

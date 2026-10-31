package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/**
 * 受击应激被动 (On-Hurt Effect Passive)。
 * <p>
 * 被其他活体攻击命中时获得一小段增益应激（如猪人之怒：受击后短暂加速，
 * 对应 EntityPigZombie 愤怒时 MOVEMENT_SPEED +0.05 的源码行为）。
 */
public class OnHurtEffectPassive extends AbstractPassiveAbility {
    private final Potion potion;
    private final int amplifier;
    private final int durationTicks;

    public OnHurtEffectPassive(String id, String nameKey, String descKey, ItemStack icon,
                               Potion potion, int amplifier, int durationTicks) {
        super(id, nameKey, descKey, icon);
        this.potion = potion;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    public OnHurtEffectPassive(String id, Potion potion, int amplifier, int durationTicks) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc",
                ItemStack.EMPTY, potion, amplifier, durationTicks);
    }

    @Override
    public float onHurt(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || source == null || amount <= 0 || potion == null) {
            return amount;
        }
        Entity attacker = source.getTrueSource();
        if (!(attacker instanceof EntityLivingBase) || attacker == player) {
            return amount;
        }
        player.addPotionEffect(new PotionEffect(potion, durationTicks, amplifier, false, true));
        return amount;
    }
}

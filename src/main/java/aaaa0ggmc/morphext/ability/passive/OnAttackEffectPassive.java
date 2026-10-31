package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/**
 * 命中附效被动 (On-Attack Effect Passive)。
 * <p>
 * 近战命中目标时附加一小段负面状态（如凋灵之触的凋零 I），
 * 是"攻击附加特效"的通用小被动（对应 CaveSpiderPoisonPassive 的同型机制）。
 */
public class OnAttackEffectPassive extends AbstractPassiveAbility {
    private final Potion potion;
    private final int amplifier;
    private final int durationTicks;

    public OnAttackEffectPassive(String id, String nameKey, String descKey, ItemStack icon,
                                 Potion potion, int amplifier, int durationTicks) {
        super(id, nameKey, descKey, icon);
        this.potion = potion;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    public OnAttackEffectPassive(String id, Potion potion, int amplifier, int durationTicks) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc",
                ItemStack.EMPTY, potion, amplifier, durationTicks);
    }

    @Override
    public void onAttack(EntityPlayer player, EntityLivingBase target, DamageSource source, float amount, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || target == null || amount <= 0 || potion == null) {
            return;
        }
        target.addPotionEffect(new PotionEffect(potion, durationTicks, amplifier, false, true));
    }
}

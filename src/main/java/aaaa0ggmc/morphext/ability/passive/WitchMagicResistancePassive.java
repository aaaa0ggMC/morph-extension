package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;

/**
 * 女巫秘术抗性被动 (Witch Magic Resistance Passive)。
 * 严格遵照原版 EntityWitch 源码体系：
 * 1. 自身药水/魔法反噬豁免：当伤害源为自身 (source.getTrueSource() == this) 时完全免疫并阻断伤害；
 * 2. 85% 魔法伤害抗性：受到外部魔法伤害 (source.isMagicDamage() 或 "magic") 时，伤害数值乘算 0.15F（减免 85%）。
 */
public class WitchMagicResistancePassive extends AbstractPassiveAbility {

    public WitchMagicResistancePassive(String id) {
        super(id, "morphext.passive.witch_magic_resistance.name", "morphext.passive.witch_magic_resistance.desc", ItemStack.EMPTY);
    }

    public WitchMagicResistancePassive() {
        this("witch_magic_resistance");
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null) {
            return false;
        }
        // 原版女巫源码：if (source.getTrueSource() == this) f = 0.0F; 彻底豁免自身造成的药水反噬
        if (source.getTrueSource() == player) {
            return true;
        }
        return false;
    }

    @Override
    public float onHurt(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null) {
            return amount;
        }

        // 原版女巫源码：if (source.isMagicDamage()) f = (float)((double)f * 0.15D);
        if (source.isMagicDamage() || "magic".equalsIgnoreCase(source.getDamageType())) {
            float reduced = amount * 0.15F;
            if (player.world.isRemote && player.ticksExisted % 6 == 0) {
                player.world.playSound(player.posX, player.posY, player.posZ,
                        SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.7F, 1.4F, false);
            }
            return reduced;
        }

        return amount;
    }
}

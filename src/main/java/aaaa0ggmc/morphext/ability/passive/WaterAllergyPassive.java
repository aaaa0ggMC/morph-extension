package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;

/**
 * 恐水/亲火生物遇水侵蚀被动 (Water Allergy Passive)。
 * 原生还原烈焰人、末影人、雪傀儡接触雨水或浸入水中时的伤害机制。
 */
public class WaterAllergyPassive extends AbstractPassiveAbility {
    public WaterAllergyPassive(String id) {
        super(id, "morphext.passive.water_allergy.name", "morphext.passive.water_allergy.desc", ItemStack.EMPTY);
    }

    public WaterAllergyPassive() {
        this("water_allergy");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.world.isRemote || player.isDead || player.capabilities.isCreativeMode) {
            return;
        }

        if (player.isWet() && player.ticksExisted % 20 == 0) {
            player.attackEntityFrom(DamageSource.DROWN, 1.0F);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5F, 1.6F);
        }
    }
}

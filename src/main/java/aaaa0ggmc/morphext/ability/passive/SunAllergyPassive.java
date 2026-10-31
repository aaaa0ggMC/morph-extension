package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * 阳光灼伤被动 (Sun Allergy Passive)。
 * 原生还原亡灵生物在烈日直射下的自燃机制：
 * 若头顶穿戴头盔，则损耗头盔耐久作为庇护；若无头盔直视天穹日光，则持续引燃自身。
 */
public class SunAllergyPassive extends AbstractPassiveAbility {
    public SunAllergyPassive(String id) {
        super(id, "morphext.passive.sun_allergy.name", "morphext.passive.sun_allergy.desc", ItemStack.EMPTY);
    }

    public SunAllergyPassive() {
        this("sun_allergy");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.world.isRemote || player.isDead || player.capabilities.isCreativeMode) {
            return;
        }

        if (player.world.isDaytime() && player.ticksExisted % 40 == 0) {
            float brightness = player.getBrightness();
            BlockPos headPos = new BlockPos(player.posX, player.posY + (double) player.getEyeHeight(), player.posZ);

            if (brightness > 0.5F && player.world.canSeeSky(headPos)) {
                ItemStack helmet = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    if (helmet.isItemStackDamageable()) {
                        helmet.damageItem(1, player);
                        if (helmet.isEmpty()) {
                            player.renderBrokenItemStack(helmet);
                        }
                    }
                } else {
                    player.setFire(8);
                }
            }
        }
    }
}

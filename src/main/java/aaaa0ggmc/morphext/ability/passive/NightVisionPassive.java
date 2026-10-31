package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

/**
 * 洞悉黑暗被动 (Night Vision Passive)。
 * 适用于蝙蝠、深海生物、暗影生物等，周期性赋予无闪烁夜视视野。
 */
public class NightVisionPassive extends AbstractPassiveAbility {
    public NightVisionPassive(String id) {
        super(id, "morphext.passive.night_vision.name", "morphext.passive.night_vision.desc", ItemStack.EMPTY);
    }

    public NightVisionPassive() {
        this("night_vision");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.world.isRemote || player.isDead) return;
        // 维持平稳的夜视效果（300 ticks，无闪烁）
        if (player.ticksExisted % 40 == 0) {
            player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 300, 0, false, false));
        }
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (!player.world.isRemote) {
            player.removePotionEffect(MobEffects.NIGHT_VISION);
        }
    }
}

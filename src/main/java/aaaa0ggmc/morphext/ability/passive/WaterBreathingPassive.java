package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 水下呼吸与免溺水被动 (Water Breathing Passive)。
 * 适用于守卫者、鱿鱼、深海食尸鬼、修格斯等水生生物。
 */
public class WaterBreathingPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:water_breath";

    public WaterBreathingPassive(String id) {
        super(id);
    }

    public WaterBreathingPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.isInWater() || player.getAir() < 300) {
            player.setAir(300);
        }
    }
}

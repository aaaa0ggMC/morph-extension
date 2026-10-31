package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * 水生生物极速游动被动 (Fast Swim Passive)。
 * 原生还原水生生物（鱿鱼、守卫者等）在水下高速滑翔推进机制。
 */
public class SwimPassive extends AbstractPassiveAbility {
    public SwimPassive(String id) {
        super(id, "morphext.passive.swim.name", "morphext.passive.swim.desc", ItemStack.EMPTY);
    }

    public SwimPassive() {
        this("swim");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.isInWater() && !player.onGround) {
            Vec3d look = player.getLook(1.0F);
            // 只要按下前进键（基于水平运动），顺着视线方向平滑推力加速
            double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
            if (speed > 0.05D) {
                player.motionX += look.x * 0.03D;
                player.motionY += look.y * 0.03D;
                player.motionZ += look.z * 0.03D;
            }
        }
    }
}

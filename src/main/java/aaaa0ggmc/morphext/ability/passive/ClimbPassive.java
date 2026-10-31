package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 攀爬被动 (Wall Climbing Passive)。
 * 原生还原蜘蛛沿竖直墙面自由攀爬与附着能力：
 * 1. 贴墙前进自动向上攀登；
 * 2. 贴墙按住潜行 (Shift) 可悬停附着在墙面上不掉落；
 * 3. 攀爬期间重置坠落伤害距离。
 */
public class ClimbPassive extends AbstractPassiveAbility {
    public static final String ID = "climb";

    public ClimbPassive(String id) {
        super(id, "morphext.passive.climb.name", "morphext.passive.climb.desc", ItemStack.EMPTY);
    }

    public ClimbPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.collidedHorizontally && !player.isOnLadder()) {
            if (player.isSneaking()) {
                // 潜行时壁虎附着定格
                player.motionY = 0.0D;
            } else {
                // 前推时平滑向上攀爬
                player.motionY = 0.22D;
            }
            player.fallDistance = 0.0F;
        }
    }
}

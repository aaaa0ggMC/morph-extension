package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 攀爬被动 (Wall Climbing Passive)。
 * 原生还原蜘蛛沿竖直墙面自由攀爬与附着能力：
 * 1. 面向墙面并有前进输入时才向上攀登（不满足条件不写 motionY，避免与疾跑跳/击退等运动互锁）；
 * 2. 贴墙按住潜行 (Shift) 可悬停附着在墙面上不掉落；
 * 3. 攀爬期间重置坠落伤害距离。
 * <p>
 * 防抖：只在 motionY 低于攀爬速度时上拉（不吞上抛/跳跃速度），条件失效后立即交还重力。
 */
public class ClimbPassive extends AbstractPassiveAbility {
    public static final String ID = "climb";

    /** 平滑向上攀爬的速度。 */
    private static final double CLIMB_SPEED = 0.22D;

    /** 面向墙判定：视线水平前方一小段距离处为实心方块。 */
    private static final double FACING_REACH = 0.6D;

    public ClimbPassive(String id) {
        super(id, "morphext.passive.climb.name", "morphext.passive.climb.desc", ItemStack.EMPTY);
    }

    public ClimbPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null) {
            return;
        }
        if (!player.collidedHorizontally || player.isOnLadder()) {
            return;
        }
        // 面向墙 + 前进输入才攀爬：被外力推撞墙面、或背对墙面滑动时不触发
        if (player.moveForward <= 0.0F || !isFacingWall(player)) {
            return;
        }
        if (player.isSneaking()) {
            // 潜行时壁虎附着定格
            player.motionY = 0.0D;
        } else if (player.motionY < CLIMB_SPEED) {
            // 前推时平滑向上攀爬；上抛/跳跃速度更高时不吞（防抖，不破坏跳墙手感）
            player.motionY = CLIMB_SPEED;
        }
        player.fallDistance = 0.0F;
    }

    private static boolean isFacingWall(EntityPlayer player) {
        Vec3d look = player.getLookVec();
        BlockPos pos = new BlockPos(
                player.posX + look.x * FACING_REACH,
                player.posY + player.height * 0.5D,
                player.posZ + look.z * FACING_REACH);
        return player.world.getBlockState(pos).getMaterial().isSolid();
    }
}

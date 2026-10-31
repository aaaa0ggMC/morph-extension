package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 苦力怕威慑光环被动 (Creeper Repel Passive)。
 * 猫与豹猫天生自带：周围 16 格内的苦力怕会惊恐转向并加速远离宿主玩家。
 */
public class CreeperRepelPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:creeper_repel";

    public CreeperRepelPassive() {
        super(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.world.isRemote || player.ticksExisted % 5 != 0) {
            return;
        }

        List<EntityCreeper> creepers = player.world.getEntitiesWithinAABB(EntityCreeper.class,
                player.getEntityBoundingBox().grow(16.0D));

        for (EntityCreeper creeper : creepers) {
            if (creeper.isEntityAlive()) {
                // 如果苦力怕正把该玩家当作攻击目标，立刻强行清空
                if (creeper.getAttackTarget() == player) {
                    creeper.setAttackTarget(null);
                }
                // 向远离玩家的方向施加推力逃跑
                Vec3d away = creeper.getPositionVector().subtract(player.getPositionVector()).normalize();
                if (away.lengthSquared() > 0.0001D) {
                    creeper.motionX += away.x * 0.15D;
                    creeper.motionZ += away.z * 0.15D;
                }
            }
        }
    }
}

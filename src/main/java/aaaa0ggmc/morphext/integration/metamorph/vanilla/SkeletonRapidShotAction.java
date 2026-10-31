package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

/**
 * 骷髅主动技能：快速连发疾射 (Skeleton Rapid Shot)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntitySkeleton} 弓术连射机理：
 * 1. 【疾速点射连击】：利用服务端动作调度器在 6 ticks 内高速连射 3 发穿透箭矢；
 * 2. 【安全射击起点】：1.0 格安全前向发射点，确保高速位移中绝不碰撞自身；
 * 3. 【真实射击反馈】：每发射击均触发独立的手臂挥动、微量后坐力、音调递增的破空音效与暴击微粒。
 */
public class SkeletonRapidShotAction implements IAction, ICooldownAction {
    public static final String ID = "skeleton_rapid_shot";
    public static final int COOLDOWN = 80; // 4 秒冷却 (80 ticks)

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }

        // 高速 3 连发射击序列：第 0 tick、第 3 tick、第 6 tick
        for (int i = 0; i < 3; i++) {
            final int shotIndex = i;
            int delay = i * 3;

            ServerActionScheduler.schedule(delay, () -> {
                if (entity == null || entity.world == null || !entity.isEntityAlive() || entity.world.isRemote) {
                    return;
                }

                entity.swingArm(EnumHand.MAIN_HAND);
                Vec3d look = entity.getLookVec();

                // 微弱后坐力推力
                entity.motionX -= look.x * 0.04D;
                entity.motionZ -= look.z * 0.04D;
                entity.velocityChanged = true;

                Vec3d safeSpawn = VanillaActionHelper.getSafeSpawnPos(entity, 1.0D);

                EntityTippedArrow arrow = new EntityTippedArrow(entity.world, entity);
                arrow.setPosition(safeSpawn.x, safeSpawn.y, safeSpawn.z);
                arrow.shoot(entity, entity.rotationPitch, entity.rotationYaw, 0.0F, 2.5F, 1.0F);
                arrow.setDamage(2.5D);
                arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;
                entity.world.spawnEntity(arrow);

                // 暴击微粒与射箭音效（音调微幅递增增强节律感）
                if (entity.world instanceof WorldServer) {
                    ((WorldServer) entity.world).spawnParticle(EnumParticleTypes.CRIT,
                            safeSpawn.x, safeSpawn.y, safeSpawn.z, 5, 0.1D, 0.1D, 0.1D, 0.05D);
                }

                entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.2F, 1.0F + shotIndex * 0.08F);
            });
        }
    }
}

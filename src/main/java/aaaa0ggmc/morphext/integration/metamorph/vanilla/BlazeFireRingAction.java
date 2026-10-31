package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 烈焰人主动技能：烈焰环爆发与炽热护体 (Inferno Ring & Thermal Barrier)。
 * <p>
 * 1. 核心能量爆发：瞬时激活烈焰充能状态 {@link EntityBlaze#setOnFire(boolean)}；
 * 2. 8 方向烈焰弹幕：向正北、东北、正东等八方射出 {@link EntitySmallFireball} 环形弹幕（安全半径 1.4 格规避本体）；
 * 3. 炽热热浪护体：震退周围 5 格内的敌对生物，点燃目标 5 秒并造成火焰震荡伤害；
 * 4. 熔岩与烈焰喷涌粒子呈现强烈的下界视觉张力。
 */
public class BlazeFireRingAction implements IAction, ICooldownAction {
    public static final String ID = "blaze_fire_ring";
    public static final int COOLDOWN = 140; // 7 秒

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }

        World world = entity.world;
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;
        double eyeY = y + (double) entity.getEyeHeight() - 0.2D;

        // 1. 点亮全身火柱充能动画
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityBlaze) {
                ((EntityBlaze) inner).setOnFire(true);
            }
        }

        // 2. 向 8 个方向发射环形小火球（半径 1.4 格偏移，彻底防止自身碰撞）
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0D);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);

            double sx = x + dirX * 1.4D;
            double sz = z + dirZ * 1.4D;

            EntitySmallFireball fb = new EntitySmallFireball(world, entity, dirX * 0.35D, 0.0D, dirZ * 0.35D);
            fb.setPosition(sx, eyeY, sz);
            world.spawnEntity(fb);
        }

        // 3. 炽热热浪震荡：点燃并击退周围 5 格内的敌人
        double shockRadius = 5.0D;
        AxisAlignedBB area = entity.getEntityBoundingBox().grow(shockRadius, 2.0D, shockRadius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, area,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            target.attackEntityFrom(source, 4.0F);
            target.setFire(5);

            double dx = target.posX - x;
            double dz = target.posZ - z;
            Vec3d push = new Vec3d(dx, 0.0D, dz).normalize();
            target.motionX += push.x * 0.85D;
            target.motionY += 0.30D;
            target.motionZ += push.z * 0.85D;
            target.velocityChanged = true;
        }

        // 4. 烈焰喷涌粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.FLAME, x, eyeY, z, 50, 1.2, 0.6, 1.2, 0.12);
            ws.spawnParticle(EnumParticleTypes.LAVA, x, eyeY, z, 20, 0.9, 0.5, 0.9, 0.0);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, eyeY, z, 25, 1.0, 0.5, 1.0, 0.05);
        }

        world.playSound(null, x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2.0F, 0.85F);
        world.playSound(null, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.5F, 1.0F);

        // 15 Tick 后关闭充能特效
        ServerActionScheduler.schedule(15, () -> {
            if (morph instanceof EntityMorph) {
                EntityLivingBase inner = ((EntityMorph) morph).getEntity();
                if (inner instanceof EntityBlaze) {
                    ((EntityBlaze) inner).setOnFire(false);
                }
            }
        });
    }
}

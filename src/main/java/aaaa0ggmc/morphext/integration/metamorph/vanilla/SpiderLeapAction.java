package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 蜘蛛/洞穴蜘蛛主动技能：灵蛛跃扑 (Spider Leap)。
 * 还原原版蜘蛛核心 AI: EntityAILeapAtTarget。
 * 向准星朝向爆发前跃扑击，滞空飞跃并对落点/沿途敌人造成冲击伤害与击退。
 */
public class SpiderLeapAction implements IAction, ICooldownAction {
    public static final String ID = "spider_leap";
    public static final int COOLDOWN = 60; // 3 秒

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
        Vec3d look = entity.getLookVec();

        entity.swingArm(EnumHand.MAIN_HAND);

        // 强力跃起扑击
        entity.motionX = look.x * 0.90D;
        entity.motionY = 0.45D;
        entity.motionZ = look.z * 0.90D;
        entity.velocityChanged = true;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 1.4F, 0.9F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.2F, 1.1F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.CRIT, entity.posX, entity.posY + 0.2D, entity.posZ, 15, 0.3D, 0.1D, 0.3D, 0.1D);
        }

        // 跃击判定：使用 ServerActionScheduler 在扑跃中途与落地进行抓捕打击
        ServerActionScheduler.schedule(6, () -> {
            if (entity.isEntityAlive()) {
                double radius = 2.5D;
                AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 1.5D, radius);
                List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                        e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

                DamageSource source = entity instanceof EntityPlayer
                        ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                        : DamageSource.causeMobDamage(entity);

                boolean hit = false;
                for (EntityLivingBase e : list) {
                    Vec3d toTarget = new Vec3d(e.posX - entity.posX, e.posY - entity.posY, e.posZ - entity.posZ).normalize();
                    if (entity.getLookVec().dotProduct(toTarget) > 0.2D || entity.getDistanceSq(e) < 4.0D) {
                        e.attackEntityFrom(source, 7.5F);
                        e.motionX += look.x * 0.4D;
                        e.motionY += 0.25D;
                        e.motionZ += look.z * 0.4D;
                        e.velocityChanged = true;
                        hit = true;
                    }
                }

                if (hit) {
                    world.playSound(null, entity.posX, entity.posY, entity.posZ,
                            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 1.0F);
                    if (world instanceof WorldServer) {
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.SWEEP_ATTACK,
                                entity.posX + look.x * 1.5D, entity.posY + 0.5D, entity.posZ + look.z * 1.5D,
                                2, 0.1D, 0.1D, 0.1D, 0.0D);
                    }
                }
            }
        });
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 守卫者主动技能：棱晶激光束 (Prismarine Laser) - 高能重构版。
 * 顺着视线发射一道高能量的海晶石激光，贯穿路径并对命中的实体造成直接魔法伤害。
 * 带有击发后坐力、主手施法动作与真实高能水爆散裂。
 */
public class PrismarineLaserAction implements IAction, ICooldownAction {
    public static final String ID = "prismarine_laser";
    public static final int COOLDOWN = 100; // 5 秒
    private static final double MAX_REACH = 24.0D;

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
        Vec3d start = VanillaActionHelper.getSafeSpawnPos(entity, 0.8D);
        Vec3d end = start.add(look.x * MAX_REACH, look.y * MAX_REACH, look.z * MAX_REACH);

        // 主手动作与微后坐力
        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX -= look.x * 0.08D;
        entity.motionZ -= look.z * 0.08D;
        entity.velocityChanged = true;

        // 检查方块阻挡
        RayTraceResult blockHit = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d impactPoint = blockHit != null ? blockHit.hitVec : end;

        // 检查光束沿途命中的存活生物
        AxisAlignedBB beamBox = new AxisAlignedBB(start.x, start.y, start.z, impactPoint.x, impactPoint.y, impactPoint.z).grow(1.2D);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, beamBox,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        EntityLivingBase hitEntity = null;
        double closestDistSq = Double.MAX_VALUE;

        for (EntityLivingBase candidate : list) {
            AxisAlignedBB aabb = candidate.getEntityBoundingBox().grow(0.3D);
            RayTraceResult intercept = aabb.calculateIntercept(start, impactPoint);
            if (intercept != null) {
                double distSq = start.squareDistanceTo(intercept.hitVec);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    hitEntity = candidate;
                    impactPoint = intercept.hitVec;
                }
            }
        }

        // 播放守卫者蓄力激发激光声
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.4F, 1.25F);

        // 服务端绘制激光光束粒子线与终点魔法爆裂
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            VanillaActionHelper.drawParticleLine(ws, start, impactPoint, EnumParticleTypes.CRIT_MAGIC, 0.35D, 0.05D);
            VanillaActionHelper.drawParticleLine(ws, start, impactPoint, EnumParticleTypes.WATER_BUBBLE, 0.5D, 0.02D);
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, impactPoint.x, impactPoint.y, impactPoint.z, 15, 0.25, 0.25, 0.25, 0.1);
            ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, impactPoint.x, impactPoint.y, impactPoint.z, 20, 0.3, 0.3, 0.3, 0.15);
        }

        // 结算伤害
        if (hitEntity != null) {
            boolean inWater = hitEntity.isInWater() || hitEntity.isWet();
            float damage = inWater ? 10.0F : 7.0F;
            DamageSource src = DamageSource.causeIndirectMagicDamage(entity, entity);
            hitEntity.attackEntityFrom(src, damage);

            world.playSound(null, hitEntity.posX, hitEntity.posY, hitEntity.posZ,
                    SoundEvents.ENTITY_GUARDIAN_FLOP, SoundCategory.PLAYERS, 1.0F, 1.5F);
        }
    }
}

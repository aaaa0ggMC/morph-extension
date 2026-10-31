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
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 守卫者/远古守卫者主动技能：棱晶蓄能激光束 (Prismarine Laser)。
 * 深度还原原版 EntityGuardian.getAttackAnimationScale 激光蓄能音画曲线：
 * 1. 0.4 秒 (8 ticks) 高速高能充能，守卫者攻击音效音调由低至高极速爬升；
 * 2. 蓄能微粒向施法者眼部汇聚成高密度海晶石能量球；
 * 3. 充能完毕瞬间朝准星爆发贯穿性海晶激光射线，潮湿或水中目标受到巨额加成伤害；
 * 4. 自由瞄准，绝不强制锁定，手感丝滑流畅。
 */
public class PrismarineLaserAction implements IAction, ICooldownAction {
    public static final String ID = "prismarine_laser";
    public static final int COOLDOWN = 80; // 4 秒
    private static final int CHARGE_TICKS = 8;
    private static final double MAX_REACH = 28.0D;

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
        entity.swingArm(EnumHand.MAIN_HAND);

        // 阶段一：启动激光蓄能音画曲线 (getAttackAnimationScale 模拟)
        ServerActionScheduler.scheduleTicker(CHARGE_TICKS, remaining -> {
            if (!entity.isEntityAlive()) return;

            int elapsed = CHARGE_TICKS - remaining;
            float scale = (float) elapsed / (float) CHARGE_TICKS;
            float pitch = 0.8F + scale * 0.7F; // 音调从 0.8 攀升到 1.5

            // 每隔 2 ticks 播放一次攀升蓄力音
            if (elapsed % 2 == 0) {
                world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.0F, pitch);
            }

            // 眼部汇聚充能粒子环
            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                Vec3d look = entity.getLookVec();
                Vec3d eye = entity.getPositionEyes(1.0F).add(look.scale(0.8D));
                ws.spawnParticle(EnumParticleTypes.WATER_BUBBLE, eye.x, eye.y, eye.z, 3, 0.15D, 0.15D, 0.15D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, eye.x, eye.y, eye.z, 2, 0.1D, 0.1D, 0.1D, 0.02D);
            }
        }, () -> {
            // 阶段二：充能完毕，释放高能激光爆裂
            if (!entity.isEntityAlive()) return;

            Vec3d look = entity.getLookVec();
            Vec3d start = VanillaActionHelper.getSafeSpawnPos(entity, 1.0D);
            Vec3d end = start.add(look.x * MAX_REACH, look.y * MAX_REACH, look.z * MAX_REACH);

            // 击发微后坐力
            entity.motionX -= look.x * 0.10D;
            entity.motionZ -= look.z * 0.10D;
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
                AxisAlignedBB aabb = candidate.getEntityBoundingBox().grow(0.35D);
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

            // 激发声与击发炸裂声
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.8F, 1.6F);

            // 服务端绘制高密双色海晶光束粒子线与终点爆裂
            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                VanillaActionHelper.drawParticleLine(ws, start, impactPoint, EnumParticleTypes.CRIT_MAGIC, 0.30D, 0.05D);
                VanillaActionHelper.drawParticleLine(ws, start, impactPoint, EnumParticleTypes.WATER_BUBBLE, 0.40D, 0.02D);
                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, impactPoint.x, impactPoint.y, impactPoint.z, 20, 0.25, 0.25, 0.25, 0.12);
                ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, impactPoint.x, impactPoint.y, impactPoint.z, 25, 0.35, 0.35, 0.35, 0.18);
            }

            // 伤害结算（还原原版：魔法伤害 + 物理伤害，在水中大幅加成）
            if (hitEntity != null) {
                boolean inWater = hitEntity.isInWater() || hitEntity.isWet();
                float damage = inWater ? 13.0F : 9.0F;

                DamageSource magicSrc = DamageSource.causeIndirectMagicDamage(entity, entity);
                hitEntity.attackEntityFrom(magicSrc, damage);

                world.playSound(null, hitEntity.posX, hitEntity.posY, hitEntity.posZ,
                        SoundEvents.ENTITY_GUARDIAN_FLOP, SoundCategory.PLAYERS, 1.2F, 1.5F);
                world.playSound(null, hitEntity.posX, hitEntity.posY, hitEntity.posZ,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.2F);
            }
        });
    }
}

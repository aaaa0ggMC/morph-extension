package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.EntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 原版生物动作通用辅助类。
 */
public final class VanillaActionHelper {
    private VanillaActionHelper() {
    }

    /**
     * 计算施法者面向方向上的安全发射起点，带有前向偏移量，彻底防止碰撞到施法者自身。
     */
    public static Vec3d getSafeSpawnPos(EntityLivingBase entity, double forwardOffset) {
        Vec3d look = entity.getLookVec();
        double eyeY = entity.posY + (double) entity.getEyeHeight() - 0.1;
        return new Vec3d(
                entity.posX + look.x * forwardOffset,
                eyeY + look.y * forwardOffset,
                entity.posZ + look.z * forwardOffset
        );
    }

    /**
     * 智能索敌：
     * 1. 优先获取准星直接瞄准的实体；
     * 2. 若无准星目标，在视线锥形角内搜索距离最近的存活生物（严格排除施法者自身）。
     */
    public static EntityLivingBase findTarget(EntityLivingBase entity, double reach, double minDotProduct) {
        Entity direct = EntityUtils.getTargetEntity(entity, reach);
        if (direct instanceof EntityLivingBase && direct != entity && direct.isEntityAlive()) {
            return (EntityLivingBase) direct;
        }

        World world = entity.world;
        AxisAlignedBB searchBox = entity.getEntityBoundingBox().grow(reach, reach * 0.5, reach);
        List<EntityLivingBase> candidates = world.getEntitiesWithinAABB(EntityLivingBase.class, searchBox,
                target -> target != null && target != entity && target.isEntityAlive());

        Vec3d look = entity.getLookVec();
        Vec3d eyePos = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);

        EntityLivingBase best = null;
        double closestDistSq = Double.MAX_VALUE;

        for (EntityLivingBase candidate : candidates) {
            Vec3d targetCenter = new Vec3d(candidate.posX, candidate.posY + candidate.height * 0.5, candidate.posZ);
            Vec3d toTarget = targetCenter.subtract(eyePos);
            double distSq = toTarget.lengthSquared();
            if (distSq > reach * reach) {
                continue;
            }
            double dot = look.dotProduct(toTarget.normalize());
            if (dot >= minDotProduct && distSq < closestDistSq) {
                closestDistSq = distSq;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 在起点与终点之间发射粒子束（仅在服务端 WorldServer 上广播）。
     */
    public static void drawParticleLine(WorldServer ws, Vec3d start, Vec3d end, EnumParticleTypes type, double stepSize, double speed, int... args) {
        double dist = start.distanceTo(end);
        int steps = Math.max(1, (int) (dist / Math.max(0.1, stepSize)));
        Vec3d dir = end.subtract(start).normalize();

        for (int i = 0; i <= steps; i++) {
            double currentDist = i * (dist / steps);
            Vec3d point = start.add(dir.scale(currentDist));
            ws.spawnParticle(type, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, speed, args);
        }
    }
}

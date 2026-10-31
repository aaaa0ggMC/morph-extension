package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
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

    /**
     * 驱动灾厄施法者（唤魔者、幻术师等）施法动作姿态、施法长吟音效与对应法术颜色的魔纹粒子。
     */
    public static void triggerSpellEffects(World world, EntityLivingBase caster, AbstractMorph morph,
                                          EntitySpellcasterIllager.SpellType spellType,
                                          SoundEvent prepareSound, SoundEvent castSound) {
        if (caster == null || world == null) {
            return;
        }

        // 1. 设置底层实体姿态为施法 (SPELLCASTING)
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity();
            if (dummy instanceof EntitySpellcasterIllager) {
                EntitySpellcasterIllager illager = (EntitySpellcasterIllager) dummy;
                illager.setSpellType(spellType);
                ServerActionScheduler.schedule(40, () -> {
                    if (illager.isSpellcasting()) {
                        illager.setSpellType(EntitySpellcasterIllager.SpellType.NONE);
                    }
                });
            }
        }

        // 2. 音效长吟与施法驱动
        if (prepareSound != null) {
            world.playSound(null, caster.posX, caster.posY, caster.posZ,
                    prepareSound, SoundCategory.PLAYERS, 1.5F, 1.0F);
        }
        if (castSound != null) {
            world.playSound(null, caster.posX, caster.posY, caster.posZ,
                    castSound, SoundCategory.PLAYERS, 1.3F, 1.0F);
        }

        // 3. 粒子驱动：按照源码对应颜色在头顶摇曳双手及身周飘散魔纹粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double[] rgb = new double[] { 0.4, 0.3, 0.35 }; // FANGS 紫黑
            if (spellType == EntitySpellcasterIllager.SpellType.SUMMON_VEX) {
                rgb = new double[] { 0.7, 0.7, 0.8 }; // VEX 白烟/银色
            } else if (spellType == EntitySpellcasterIllager.SpellType.WOLOLO) {
                rgb = new double[] { 0.7, 0.5, 0.2 }; // WOLOLO 橙黄
            } else if (spellType == EntitySpellcasterIllager.SpellType.DISAPPEAR) {
                rgb = new double[] { 0.3, 0.3, 0.8 }; // DISAPPEAR 幻象蓝
            } else if (spellType == EntitySpellcasterIllager.SpellType.BLINDNESS) {
                rgb = new double[] { 0.1, 0.1, 0.2 }; // BLINDNESS 暗夜幽黑
            }

            float yawOffset = (float) Math.toRadians(caster.renderYawOffset);
            float f1 = MathHelper.cos(yawOffset);
            float f2 = MathHelper.sin(yawOffset);
            double headY = caster.posY + caster.getEyeHeight() + 0.35D;

            // 双手摇曳位置生成专属魔纹粒子（SPELL_MOB 当 count=0 时 x,y,zOffset 对应 RGB 通道）
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, caster.posX + f1 * 0.6D, headY, caster.posZ + f2 * 0.6D, 0, rgb[0], rgb[1], rgb[2], 1.0D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, caster.posX - f1 * 0.6D, headY, caster.posZ - f2 * 0.6D, 0, rgb[0], rgb[1], rgb[2], 1.0D);

            // 周身飘散魔纹粒子
            for (int i = 0; i < 16; i++) {
                double px = caster.posX + (ws.rand.nextDouble() - 0.5D) * 1.3D;
                double py = caster.posY + ws.rand.nextDouble() * (caster.height + 0.4D);
                double pz = caster.posZ + (ws.rand.nextDouble() - 0.5D) * 1.3D;
                ws.spawnParticle(EnumParticleTypes.SPELL_MOB, px, py, pz, 0, rgb[0], rgb[1], rgb[2], 1.0D);
            }
        }
    }
}

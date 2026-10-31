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
 * 恼鬼主动技能：红光穿刺疾冲 (Vex Phase)。
 * 浑身泛起嗜血红光进入穿刺状态，朝准星方向高速突刺 12 格并穿透沿途生物，造成穿刺伤害与击退。
 */
public class VexPhaseAction implements IAction, ICooldownAction {
    public static final String ID = "vex_phase";
    public static final int COOLDOWN = 160; // 8 秒

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

        // 高速突进推进力
        entity.motionX = look.x * 2.2D;
        entity.motionY = look.y * 1.5D + 0.1D;
        entity.motionZ = look.z * 2.2D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        // 突刺起始与终点
        Vec3d start = new Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        Vec3d end = start.add(look.scale(12.0D));

        // 沿途生成红光充能粒子束
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.REDSTONE, 0.4D, 0.0D);
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.CRIT, 0.6D, 0.1D);
        }

        // 穿刺盒检测
        AxisAlignedBB pathBox = new AxisAlignedBB(
                Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        ).grow(1.5D);

        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, pathBox,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        for (EntityLivingBase target : targets) {
            // 计算到突刺线段的垂直距离
            Vec3d toTarget = new Vec3d(target.posX - start.x, target.posY + target.height * 0.5D - start.y, target.posZ - start.z);
            double proj = toTarget.dotProduct(look);
            if (proj >= 0.0D && proj <= 12.0D) {
                Vec3d closestPoint = start.add(look.scale(proj));
                if (closestPoint.distanceTo(new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ)) <= 1.8D) {
                    target.attackEntityFrom(source, 8.0F);

                    // 穿透重推击退
                    target.motionX += look.x * 0.75D;
                    target.motionY += 0.30D;
                    target.motionZ += look.z * 0.75D;
                    target.velocityChanged = true;

                    if (world instanceof WorldServer) {
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                                target.posX, target.posY + target.height * 0.5D, target.posZ, 6, 0.2, 0.2, 0.2, 0.1);
                    }
                }
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 1.8F, 1.1F);
    }
}

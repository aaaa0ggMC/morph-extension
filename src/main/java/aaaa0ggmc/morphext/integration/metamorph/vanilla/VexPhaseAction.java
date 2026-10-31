package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 恼鬼主动技能：血怒蓄力穿墙突刺 (Vex Phase Charge)。
 * <p>
 * 严谨复刻原版 {@link net.minecraft.entity.monster.EntityVex#setCharging(boolean)} 与穿墙物理机制：
 * 1. 狂暴血怒红光：激活 {@link EntityVex#setCharging(boolean)}，恼鬼周身泛起血色充能纹理；
 * 2. 破空冲刺推力：朝视线方向爆发高速强力位移，伴随红石光芒与暴击轨迹拖尾；
 * 3. 虚化穿墙透体 (NoClip Phase)：若冲刺路径前方遭遇厚度 ≤ 3 格的坚固障碍物墙体，直接虚化透体穿墙而过；
 * 4. 贯穿真实破甲：对冲刺轨迹 12 格线段两侧 1.8 格内的所有敌人造成 8 点无视护甲穿刺伤害与穿透重击；
 * 5. 冲刺完成后（20 Tick）血怒退去平稳复位。
 */
public class VexPhaseAction implements IAction, ICooldownAction {
    public static final String ID = "vex_phase";
    public static final int COOLDOWN = 140; // 7 秒冷却

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

        // 1. 开启恼鬼血色红光充能状态
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityVex) {
                ((EntityVex) inner).setCharging(true);
            }
        }

        // 2. 高速突进强推力
        entity.motionX = look.x * 2.2D;
        entity.motionY = look.y * 1.5D + 0.1D;
        entity.motionZ = look.z * 2.2D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        // 突刺起始与终点（12 格长程贯穿）
        Vec3d start = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        Vec3d end = start.add(look.scale(12.0D));

        // 3. 沿途生成红石光带与暴击火花拖尾
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.REDSTONE, 0.35D, 0.0D);
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.CRIT, 0.5D, 0.1D);
        }

        // 4. 虚化穿墙透体检测 (Phase Through Solid Obstacles)
        RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            // 向前探查最多 3 格厚度的方块群背后是否存在可通过的空气空间
            for (int depth = 1; depth <= 4; depth++) {
                BlockPos targetPos = hitPos.offset(hit.sideHit.getOpposite(), depth);
                IBlockState state1 = world.getBlockState(targetPos);
                IBlockState state2 = world.getBlockState(targetPos.up());

                boolean passable = (!state1.getMaterial().isSolid() || !state1.isFullCube())
                        && (!state2.getMaterial().isSolid() || !state2.isFullCube());

                if (passable) {
                    double destX = targetPos.getX() + 0.5D;
                    double destY = targetPos.getY();
                    double destZ = targetPos.getZ() + 0.5D;

                    entity.setPositionAndUpdate(destX, destY, destZ);
                    entity.motionX = look.x * 1.2D;
                    entity.motionY = look.y * 0.8D;
                    entity.motionZ = look.z * 1.2D;
                    entity.velocityChanged = true;

                    if (world instanceof WorldServer) {
                        WorldServer ws = (WorldServer) world;
                        ws.spawnParticle(EnumParticleTypes.PORTAL, destX, destY + 0.5D, destZ, 25, 0.3, 0.5, 0.3, 0.2);
                        ws.spawnParticle(EnumParticleTypes.REDSTONE, destX, destY + 0.5D, destZ, 20, 0.4, 0.4, 0.4, 0.0);
                    }
                    break;
                }
            }
        }

        // 5. 穿刺盒检测与真实破甲伤害
        AxisAlignedBB pathBox = new AxisAlignedBB(
                Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        ).grow(1.8D);

        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, pathBox,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - start.x, target.posY + target.height * 0.5D - start.y, target.posZ - start.z);
            double proj = toTarget.dotProduct(look);
            if (proj >= 0.0D && proj <= 12.0D) {
                Vec3d closestPoint = start.add(look.scale(proj));
                if (closestPoint.distanceTo(new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ)) <= 2.0D) {
                    target.attackEntityFrom(source, 8.0F);

                    // 穿透重推击退
                    target.motionX += look.x * 0.85D;
                    target.motionY += 0.35D;
                    target.motionZ += look.z * 0.85D;
                    target.velocityChanged = true;

                    if (world instanceof WorldServer) {
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                                target.posX, target.posY + target.height * 0.5D, target.posZ, 8, 0.2, 0.2, 0.2, 0.1);
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT,
                                target.posX, target.posY + target.height * 0.5D, target.posZ, 12, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 2.0F, 1.1F);

        // 6. 20 Tick (1.0秒) 突进结束后复位充能状态
        ServerActionScheduler.schedule(20, () -> {
            if (morph instanceof EntityMorph) {
                EntityLivingBase inner = ((EntityMorph) morph).getEntity();
                if (inner instanceof EntityVex) {
                    ((EntityVex) inner).setCharging(false);
                }
            }
        });
    }
}

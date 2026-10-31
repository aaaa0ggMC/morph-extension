package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.vanilla_pack.morphs.ShulkerMorph;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Random;

/**
 * 潜影贝标志性折跃瞬移技能 (Shulker Teleport Action)。
 * 1. 优先瞬移至玩家准星视线方向的目标方块表面（最远 32 格）；
 * 2. 若前方无遮挡或落点不安全，自动在周围 16-32 格内寻找安全坚实地面；
 * 3. 伴随潜影贝专属折跃音效与紫珀碎屑/传送门粒子；
 * 4. 彻底消除 ShulkerMorph 原生对变身玩家的 blockPos 坐标定死锁；
 * 5. 冷却适中 (6秒 = 120 ticks)，极大增强变身机动性。
 */
public class ShulkerTeleportAction implements IAction, ICooldownAction {
    public static final String ID = "shulker_teleport";
    public static final String ALIAS_ID = "morphext:shulker_teleport";
    public static final int COOLDOWN = 120; // 6 秒

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
        Random rand = entity.getRNG();

        // 确保清除原生定死锁定
        if (morph instanceof ShulkerMorph) {
            ((ShulkerMorph) morph).blockPos = null;
        }

        double startX = entity.posX;
        double startY = entity.posY;
        double startZ = entity.posZ;

        Vec3d eyePos = new Vec3d(startX, startY + (double) entity.getEyeHeight(), startZ);
        Vec3d look = entity.getLookVec();
        double maxDist = 32.0D;
        Vec3d traceEnd = eyePos.add(look.scale(maxDist));

        RayTraceResult hit = world.rayTraceBlocks(eyePos, traceEnd, false, true, false);
        BlockPos safeDest = null;

        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos targetPos = hit.getBlockPos().offset(hit.sideHit);
            safeDest = findSafeLandingPos(world, targetPos);
        } else {
            BlockPos targetPos = new BlockPos(traceEnd.x, traceEnd.y, traceEnd.z);
            safeDest = findSafeLandingPos(world, targetPos);
        }

        // 若视线落点不安全，在周围 16-32 格随机寻找安全立足点
        if (safeDest == null) {
            for (int i = 0; i < 24; i++) {
                double dist = 16.0D + rand.nextDouble() * 16.0D;
                double angle = rand.nextDouble() * Math.PI * 2.0D;
                double rx = startX + Math.cos(angle) * dist;
                double rz = startZ + Math.sin(angle) * dist;
                double ry = Math.max(1, startY + (rand.nextInt(11) - 5));

                BlockPos candidate = new BlockPos(rx, ry, rz);
                BlockPos found = findSafeLandingPos(world, candidate);
                if (found != null) {
                    safeDest = found;
                    break;
                }
            }
        }

        if (safeDest == null) {
            return;
        }

        double destX = safeDest.getX() + 0.5D;
        double destY = safeDest.getY();
        double destZ = safeDest.getZ() + 0.5D;

        // 起点微粒与音效
        world.playSound(null, startX, startY, startZ,
                SoundEvents.ENTITY_SHULKER_TELEPORT, SoundCategory.PLAYERS, 1.4F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int purpurState = Block.getStateId(Blocks.PURPUR_BLOCK.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, startX, startY + 0.5D, startZ,
                    30, 0.4D, 0.4D, 0.4D, 0.05D, purpurState);
            ws.spawnParticle(EnumParticleTypes.PORTAL, startX, startY + 0.5D, startZ,
                    35, 0.3D, 0.5D, 0.3D, 0.2D);
        }

        // 执行传送与跌落重置
        if (entity instanceof EntityPlayerMP) {
            ((EntityPlayerMP) entity).connection.setPlayerLocation(destX, destY, destZ, entity.rotationYaw, entity.rotationPitch);
        } else {
            entity.setPositionAndUpdate(destX, destY, destZ);
        }
        entity.fallDistance = 0.0F;

        if (morph instanceof ShulkerMorph) {
            ((ShulkerMorph) morph).blockPos = null;
        }

        // 终点微粒与音效
        world.playSound(null, destX, destY, destZ,
                SoundEvents.ENTITY_SHULKER_TELEPORT, SoundCategory.PLAYERS, 1.4F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int purpurState = Block.getStateId(Blocks.PURPUR_BLOCK.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, destX, destY + 0.5D, destZ,
                    30, 0.4D, 0.4D, 0.4D, 0.05D, purpurState);
            ws.spawnParticle(EnumParticleTypes.PORTAL, destX, destY + 0.5D, destZ,
                    35, 0.3D, 0.5D, 0.3D, 0.2D);
        }

        entity.swingArm(EnumHand.MAIN_HAND);
    }

    private static BlockPos findSafeLandingPos(World world, BlockPos start) {
        BlockPos current = start;
        for (int i = 0; i < 6; i++) {
            if (isStandable(world, current)) {
                return current;
            }
            if (world.getBlockState(current.down()).getMaterial().isSolid()) {
                break;
            }
            current = current.down();
        }

        current = start;
        for (int i = 0; i < 4; i++) {
            if (isStandable(world, current)) {
                return current;
            }
            current = current.up();
        }

        return null;
    }

    private static boolean isStandable(World world, BlockPos pos) {
        IBlockState foot = world.getBlockState(pos);
        IBlockState head = world.getBlockState(pos.up());
        IBlockState ground = world.getBlockState(pos.down());

        boolean spaceClear = (!foot.getMaterial().blocksMovement() || !foot.causesSuffocation())
                && (!head.getMaterial().blocksMovement() || !head.causesSuffocation());
        boolean groundSolid = ground.getMaterial().isSolid() && !ground.getMaterial().isLiquid();

        return spaceClear && groundSolid;
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 末影人主动技能：视线瞬移 (Enderman Teleport)。
 * 朝准星视线方向瞬移至目标表面或最远 32 格安全空间，彻底进行地面与空间防卡墙校验，
 * 伴随起点与落点的双重传送门微粒与折跃音效。
 */
public class EndermanTeleportAction implements IAction, ICooldownAction {
    public static final String ID = "enderman_teleport";
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
        Vec3d eyePos = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        Vec3d look = entity.getLookVec();
        double maxDist = 32.0D;
        Vec3d traceEnd = eyePos.add(look.scale(maxDist));

        RayTraceResult hit = world.rayTraceBlocks(eyePos, traceEnd, false, true, false);
        BlockPos targetPos;

        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            targetPos = hit.getBlockPos().offset(hit.sideHit);
        } else {
            targetPos = new BlockPos(traceEnd.x, traceEnd.y, traceEnd.z);
        }

        // 寻找安全落脚点：校验脚底方块与头顶两格空气
        BlockPos safePos = findSafeLandingPos(world, targetPos);
        if (safePos == null) {
            return;
        }

        double destX = safePos.getX() + 0.5D;
        double destY = safePos.getY();
        double destZ = safePos.getZ() + 0.5D;

        // 起点微粒与音效
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, entity.posX, entity.posY + 1.0D, entity.posZ,
                    45, 0.4D, 0.8D, 0.4D, 0.5D);
        }
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.6F, 1.0F);

        // 执行瞬移与重置跌落高度
        if (entity instanceof EntityPlayerMP) {
            ((EntityPlayerMP) entity).connection.setPlayerLocation(destX, destY, destZ, entity.rotationYaw, entity.rotationPitch);
        } else {
            entity.setPositionAndUpdate(destX, destY, destZ);
        }
        entity.fallDistance = 0.0F;

        // 落点微粒与音效
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, destX, destY + 1.0D, destZ,
                    45, 0.4D, 0.8D, 0.4D, 0.5D);
        }
        world.playSound(null, destX, destY, destZ,
                SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.6F, 1.0F);
    }

    private static BlockPos findSafeLandingPos(World world, BlockPos start) {
        // 先向下寻找坚实支撑面（最多 5 格）
        BlockPos current = start;
        for (int i = 0; i < 5; i++) {
            if (isStandable(world, current)) {
                return current;
            }
            if (world.getBlockState(current.down()).getMaterial().isSolid()) {
                break;
            }
            current = current.down();
        }

        // 若原点或脚下受阻，向上寻找开阔空间（最多 4 格）
        current = start;
        for (int i = 0; i < 4; i++) {
            if (isStandable(world, current)) {
                return current;
            }
            current = current.up();
        }

        return isStandable(world, start) ? start : null;
    }

    private static boolean isStandable(World world, BlockPos pos) {
        IBlockState foot = world.getBlockState(pos);
        IBlockState head = world.getBlockState(pos.up());
        IBlockState ground = world.getBlockState(pos.down());

        boolean footPassable = !foot.getMaterial().blocksMovement();
        boolean headPassable = !head.getMaterial().blocksMovement();
        boolean groundSolid = ground.getMaterial().isSolid();

        return footPassable && headPassable && groundSolid;
    }
}

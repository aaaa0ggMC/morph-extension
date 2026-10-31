package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityEvokerFangs;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 唤魔者主动技能：唤魔尖牙 (Evoker Fangs)。
 * 顺应灾厄魔法：沿视线方向破土召唤一排（12 只）咬合尖牙；
 * 若潜行释放，则在身周召唤双层同心尖牙防御环，彻底豁免施法者自身。
 */
public class EvokerFangsAction implements IAction, ICooldownAction {
    public static final String ID = "evoker_fangs";
    public static final int COOLDOWN = 300; // 15 秒

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

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 1.5F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK, SoundCategory.PLAYERS, 1.2F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                    20, 0.5, 0.5, 0.5, 1.0);
        }

        double minY = entity.posY - 3.0D;
        double maxY = entity.posY + 3.0D;

        if (entity.isSneaking()) {
            // 潜行释放：身周双层同心尖牙环
            float baseAngle = (float) Math.toRadians(entity.rotationYaw);

            // 内环：5 只尖牙，半径 1.75D，延迟 0
            for (int i = 0; i < 5; i++) {
                float angle = baseAngle + i * (float) Math.PI * 0.4F;
                double fx = entity.posX + MathHelper.cos(angle) * 1.75D;
                double fz = entity.posZ + MathHelper.sin(angle) * 1.75D;
                spawnFangAt(world, entity, fx, fz, minY, maxY, angle, 0);
            }

            // 外环：8 只尖牙，半径 2.85D，延迟 3 ticks
            for (int i = 0; i < 8; i++) {
                float angle = baseAngle + (float) (i * Math.PI * 2.0 / 8.0) + 0.2F;
                double fx = entity.posX + MathHelper.cos(angle) * 2.85D;
                double fz = entity.posZ + MathHelper.sin(angle) * 2.85D;
                spawnFangAt(world, entity, fx, fz, minY, maxY, angle, 3);
            }
        } else {
            // 正常释放：前方直线 12 只尖牙
            Vec3d look = entity.getLookVec();
            float yaw = (float) MathHelper.atan2(look.z, look.x);

            for (int i = 0; i < 12; i++) {
                double dist = 1.25D * (i + 1);
                double fx = entity.posX + look.x * dist;
                double fz = entity.posZ + look.z * dist;
                spawnFangAt(world, entity, fx, fz, minY, maxY, yaw, i);
            }
        }
    }

    private static void spawnFangAt(World world, EntityLivingBase caster, double x, double z, double minY, double maxY, float yaw, int delayTicks) {
        BlockPos pos = new BlockPos(x, maxY, z);
        boolean foundGround = false;
        double groundY = 0.0D;

        while (pos.getY() >= minY) {
            BlockPos down = pos.down();
            IBlockState downState = world.getBlockState(down);
            if (downState.isSideSolid(world, down, net.minecraft.util.EnumFacing.UP)) {
                if (!world.isAirBlock(pos)) {
                    IBlockState state = world.getBlockState(pos);
                    net.minecraft.util.math.AxisAlignedBB aabb = state.getCollisionBoundingBox(world, pos);
                    if (aabb != null) {
                        groundY = pos.getY() + aabb.maxY;
                    } else {
                        groundY = pos.getY();
                    }
                } else {
                    groundY = pos.getY();
                }
                foundGround = true;
                break;
            }
            pos = down;
        }

        if (foundGround) {
            EntityEvokerFangs fangs = new EntityEvokerFangs(world, x, groundY, z, yaw, delayTicks, caster);
            world.spawnEntity(fangs);
        }
    }
}

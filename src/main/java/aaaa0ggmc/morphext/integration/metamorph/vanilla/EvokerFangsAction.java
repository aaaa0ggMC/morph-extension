package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
import net.minecraft.entity.projectile.EntityEvokerFangs;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 唤魔者主动技能：唤魔尖牙 (Evoker Fangs)。
 * 顺应灾厄魔法源码体系：
 * 1. 施法咒文音画驱动：高举双手摇曳并飘散深紫魔纹粒子 (SpellType.FANGS) 与长吟准备音效；
 * 2. 直线攻击模式：沿准星直线排布召唤 16 只从地面上涌咬合的唤魔尖牙；
 * 3. 潜行防御模式：在身周召唤双层同心防御尖牙环（内环 6 只，外环 10 只）；
 * 4. 尖牙构造严格传入 caster，在伤害判定中彻底豁免宿主自身伤害。
 */
public class EvokerFangsAction implements IAction, ICooldownAction {
    public static final String ID = "evoker_fangs";
    public static final int COOLDOWN = 200; // 10 秒

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

        // 施法咒文音画驱动 (SpellType.FANGS + 紫黑魔纹粒子 + 施法准备长吟)
        VanillaActionHelper.triggerSpellEffects(world, entity, morph,
                EntitySpellcasterIllager.SpellType.FANGS,
                SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK,
                SoundEvents.EVOCATION_ILLAGER_CAST_SPELL);

        double minY = entity.posY - 3.0D;
        double maxY = entity.posY + 3.0D;

        if (entity.isSneaking()) {
            // 潜行释放：身周双层同心尖牙防御环 (共 16 只)
            float baseAngle = (float) Math.toRadians(entity.rotationYaw);

            // 内环：6 只尖牙，半径 1.75D，延迟 0 刻
            for (int i = 0; i < 6; i++) {
                float angle = baseAngle + i * (float) (Math.PI * 2.0 / 6.0);
                double fx = entity.posX + MathHelper.cos(angle) * 1.75D;
                double fz = entity.posZ + MathHelper.sin(angle) * 1.75D;
                spawnFangAt(world, entity, fx, fz, minY, maxY, angle, 0);
            }

            // 外环：10 只尖牙，半径 3.0D，延迟 3 刻
            for (int i = 0; i < 10; i++) {
                float angle = baseAngle + (float) (i * Math.PI * 2.0 / 10.0) + 0.15F;
                double fx = entity.posX + MathHelper.cos(angle) * 3.0D;
                double fz = entity.posZ + MathHelper.sin(angle) * 3.0D;
                spawnFangAt(world, entity, fx, fz, minY, maxY, angle, 3);
            }
        } else {
            // 正常释放：前方直线 16 只唤魔尖牙依次涌出
            Vec3d look = entity.getLookVec();
            float yaw = (float) MathHelper.atan2(look.z, look.x);

            for (int i = 0; i < 16; i++) {
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
            // 严格传入 caster，底层 EntityEvokerFangs 在伤害判定时会主动比对并豁免施法者
            EntityEvokerFangs fangs = new EntityEvokerFangs(world, x, groundY, z, yaw, delayTicks, caster);
            world.spawnEntity(fangs);
        }
    }
}

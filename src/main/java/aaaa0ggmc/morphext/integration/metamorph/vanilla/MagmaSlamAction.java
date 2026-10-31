package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 岩浆怪主动技能：熔岩重踏 (Magma Slam)。
 * 蓄力高跃弹射，落地时在地面炸开熔岩火环，重创并引燃周围敌人。
 */
public class MagmaSlamAction implements IAction, ICooldownAction {
    public static final String ID = "magma_slam";
    public static final int COOLDOWN = 240; // 12 秒

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

        // 1. 熔岩高跳起飞
        entity.motionX += look.x * 0.80D;
        entity.motionY = 0.90D;
        entity.motionZ += look.z * 0.80D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_MAGMACUBE_JUMP, SoundCategory.PLAYERS, 1.8F, 0.9F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.LAVA, entity.posX, entity.posY + 0.2D, entity.posZ,
                    15, 0.4, 0.2, 0.4, 0.0);
            ws.spawnParticle(EnumParticleTypes.FLAME, entity.posX, entity.posY + 0.2D, entity.posZ,
                    20, 0.3, 0.2, 0.3, 0.05);
        }

        // 2. 着陆检测与熔岩火环爆发 (最多跟踪 30 ticks)
        AtomicBoolean landed = new AtomicBoolean(false);
        ServerActionScheduler.scheduleTicker(30, remaining -> {
            if (landed.get() || !entity.isEntityAlive()) {
                return;
            }

            if (remaining <= 25 && (entity.onGround || remaining == 1)) {
                landed.set(true);
                triggerMagmaShockwave(world, entity);
            }
        }, () -> {
            if (!landed.get() && entity.isEntityAlive()) {
                triggerMagmaShockwave(world, entity);
            }
        });
    }

    private static void triggerMagmaShockwave(World world, EntityLivingBase entity) {
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        world.playSound(null, x, y, z, SoundEvents.ENTITY_MAGMACUBE_SQUISH, SoundCategory.PLAYERS, 2.0F, 0.7F);
        world.playSound(null, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.6F, 0.8F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int magmaState = Block.getStateId(Blocks.MAGMA.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y + 0.1D, z, 50, 2.0, 0.2, 2.0, 0.1, magmaState);
            ws.spawnParticle(EnumParticleTypes.LAVA, x, y + 0.2D, z, 25, 1.8, 0.2, 1.8, 0.0);
            ws.spawnParticle(EnumParticleTypes.FLAME, x, y + 0.3D, z, 60, 2.2, 0.3, 2.2, 0.08);
        }

        double radius = 6.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.5D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setFireDamage()
                : DamageSource.causeMobDamage(entity).setFireDamage();

        for (EntityLivingBase target : targets) {
            target.attackEntityFrom(source, 8.0F);
            // 引燃 5 秒
            target.setFire(5);

            double dx = target.posX - x;
            double dz = target.posZ - z;
            double norm = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += (dx / norm) * 0.7D;
            target.motionY += 0.40D;
            target.motionZ += (dz / norm) * 0.7D;
            target.velocityChanged = true;
        }
    }
}

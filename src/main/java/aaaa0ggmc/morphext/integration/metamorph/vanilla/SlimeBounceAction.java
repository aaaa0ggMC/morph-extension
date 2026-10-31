package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
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
 * 史莱姆主动技能：蓄力黏液弹跳重踏 (Slime Bounce Slam)。
 * 蓄力朝视线前方高空弹射跳跃，落地时引发强劲黏液震地冲击波，对周围敌人造成震荡伤害与强效减速。
 */
public class SlimeBounceAction implements IAction, ICooldownAction {
    public static final String ID = "slime_bounce_slam";
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
        Vec3d look = entity.getLookVec();

        // 1. 高空弹跳起飞
        entity.motionX += look.x * 0.75D;
        entity.motionY = 0.85D;
        entity.motionZ += look.z * 0.75D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SLIME_JUMP, SoundCategory.PLAYERS, 1.8F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.5F, 1.1F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SLIME, entity.posX, entity.posY + 0.2D, entity.posZ,
                    30, 0.4, 0.2, 0.4, 0.1);
        }

        // 2. 着陆检测与黏液震地冲击波 (最多跟踪 30 ticks)
        AtomicBoolean landed = new AtomicBoolean(false);
        ServerActionScheduler.scheduleTicker(30, remaining -> {
            if (landed.get() || !entity.isEntityAlive()) {
                return;
            }

            // 起飞 5 ticks 之后检测落地
            if (remaining <= 25 && (entity.onGround || remaining == 1)) {
                landed.set(true);
                triggerSlimeShockwave(world, entity);
            }
        }, () -> {
            if (!landed.get() && entity.isEntityAlive()) {
                triggerSlimeShockwave(world, entity);
            }
        });
    }

    private static void triggerSlimeShockwave(World world, EntityLivingBase entity) {
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        world.playSound(null, x, y, z, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 2.0F, 0.7F);
        world.playSound(null, x, y, z, SoundEvents.BLOCK_SLIME_FALL, SoundCategory.PLAYERS, 1.6F, 0.9F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int slimeState = Block.getStateId(Blocks.SLIME_BLOCK.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y + 0.1D, z, 45, 1.8, 0.2, 1.8, 0.1, slimeState);
            ws.spawnParticle(EnumParticleTypes.SLIME, x, y + 0.2D, z, 40, 2.0, 0.3, 2.0, 0.1);
        }

        double radius = 5.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            target.attackEntityFrom(source, 7.0F);
            // 附加 5 秒 (100 ticks) 缓慢 II
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1, false, true));

            double dx = target.posX - x;
            double dz = target.posZ - z;
            double norm = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += (dx / norm) * 0.6D;
            target.motionY += 0.35D;
            target.motionZ += (dz / norm) * 0.6D;
            target.velocityChanged = true;
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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

/**
 * 苦力怕主动技能：引信蓄力自爆 (Creeper Fuse)。
 * 蓄力 1.5 秒（播放嘶嘶声与膨胀烟雾粒子），随后产生威力 3.0 的安全冲击波大爆炸，
 * 对周围敌人造成大量爆炸伤害与强力击退，彻底豁免宿主玩家自身不自残。
 */
public class CreeperFuseAction implements IAction, ICooldownAction {
    public static final String ID = "creeper_fuse";
    public static final int COOLDOWN = 400; // 20 秒
    public static final int FUSE_TICKS = 30; // 1.5 秒

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

        // 初始引信嘶嘶声与短暂缓速蓄力感
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.PLAYERS, 1.8F, 1.0F);
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, FUSE_TICKS, 1, false, false));

        // 调度 1.5 秒蓄力粒子追踪与引爆
        ServerActionScheduler.scheduleTicker(FUSE_TICKS, remaining -> {
            if (!entity.isEntityAlive()) {
                return;
            }
            if (world instanceof WorldServer && remaining % 3 == 0) {
                WorldServer ws = (WorldServer) world;
                double spread = (FUSE_TICKS - remaining) * 0.02D;
                ws.spawnParticle(EnumParticleTypes.CLOUD, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                        8, spread, entity.height * 0.3D, spread, 0.03D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                        6, spread, entity.height * 0.3D, spread, 0.02D);
            }
        }, () -> {
            if (!entity.isEntityAlive()) {
                return;
            }

            double x = entity.posX;
            double y = entity.posY + entity.height * 0.5D;
            double z = entity.posZ;

            // 1. 震撼爆炸音效与全屏级视觉特效
            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.5F, 0.9F);
            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, x, y, z, 2, 0.0, 0.0, 0.0, 0.0);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 35, 1.5, 1.0, 1.5, 0.15);
                ws.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 25, 1.2, 0.8, 1.2, 0.2);
            }

            // 2. 威力 3.0 安全冲击波（半径 6.0 格），严格豁免宿主自身
            double radius = 6.0D;
            AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 3.0D, radius);
            List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                    e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

            DamageSource source = entity instanceof EntityPlayer
                    ? DamageSource.causePlayerDamage((EntityPlayer) entity).setExplosion()
                    : DamageSource.causeMobDamage(entity).setExplosion();

            for (EntityLivingBase target : targets) {
                double dist = entity.getDistance(target);
                if (dist > radius) {
                    continue;
                }

                // 距离递减伤害公式：中心高额、边缘递减，威力等同 3.0 爆炸冲击
                double factor = 1.0D - (dist / radius);
                float damage = (float) (factor * 16.0D + 4.0D);
                target.attackEntityFrom(source, damage);

                // 强力水平与垂直击退
                double dx = target.posX - entity.posX;
                double dz = target.posZ - entity.posZ;
                double norm = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
                double push = factor * 1.5D + 0.3D;

                target.motionX += (dx / norm) * push;
                target.motionY += 0.45D * factor + 0.15D;
                target.motionZ += (dz / norm) * push;
                target.velocityChanged = true;
            }
        });
    }
}

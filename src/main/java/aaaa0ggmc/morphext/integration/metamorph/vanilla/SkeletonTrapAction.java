package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 骷髅马/僵尸马主动技能：雷霆四骑士践踏 (Skeleton Horse Trap Thunder Slam)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.passive.EntitySkeletonHorse} 与 {@link net.minecraft.entity.ai.EntityAISkeletonRiders} 天罚雷击机制：
 * 1. 【高扬前蹄天雷召来】：原地扬起前蹄引发震撼苍穹的雷鸣音效与天雷电光特效 (EntityLightningBolt effectOnly)；
 * 2. 【8 格雷霆冲击波】：以落蹄为中心爆裂环形雷光与气爆冲击波，对周围 8 格所有存活敌对生物造成巨额范围伤害与深度击退；
 * 3. 【强效压制】：命中附带雷霆电击短暂迟缓麻痹。
 */
public class SkeletonTrapAction implements IAction, ICooldownAction {
    public static final String ID = "skeleton_trap_slam";
    public static final int COOLDOWN = 400; // 20 秒冷却 (400 ticks)

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

        // 1. 前蹄高扬与天怒雷鸣音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_HORSE_ANGRY, SoundCategory.PLAYERS, 1.4F, 0.75F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.PLAYERS, 2.0F, 0.9F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.1F, 1.2F);

        // 2. 召唤纯视觉与音效的天雷电光（effectOnly = true，避免灼伤自身方块）
        EntityLightningBolt bolt = new EntityLightningBolt(world, entity.posX, entity.posY, entity.posZ, true);
        world.addWeatherEffect(bolt);

        // 3. 环形雷霆冲击波粒子广播
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, entity.posX, entity.posY + 0.5D, entity.posZ, 2, 0.5D, 0.2D, 0.5D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, entity.posX, entity.posY + 0.5D, entity.posZ, 80, 2.8D, 0.6D, 2.8D, 0.25D);
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, entity.posX, entity.posY + 0.3D, entity.posZ, 12, 1.8D, 0.2D, 1.8D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, entity.posX, entity.posY + 0.2D, entity.posZ, 25, 2.0D, 0.3D, 2.0D, 0.05D);
        }

        // 4. 周围 8 格敌人判定：重创并向外击退
        double radius = 8.0D;
        AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(radius, 3.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                target -> target != entity && target.isEntityAlive() && !target.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            double dist = target.getDistance(entity);
            float damage = (float) Math.max(5.0D, 10.0D - dist * 0.6D);

            // 范围物理/雷霆冲击伤害
            target.attackEntityFrom(source, damage);

            // 附带电击麻痹迟缓 II (60 ticks = 3 秒)
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));

            // 向外强力击退
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001D) {
                dx /= len;
                dz /= len;
            } else {
                dx = 0.0D;
                dz = 0.0D;
            }

            target.motionX += dx * 1.5D;
            target.motionY += 0.45D;
            target.motionZ += dz * 1.5D;
            target.velocityChanged = true;
        }
    }
}

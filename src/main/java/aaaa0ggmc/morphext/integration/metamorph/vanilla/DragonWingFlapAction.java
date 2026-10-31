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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 末影龙主动技能：巨力展翅扇风击退 (Dragon Wing Flap Gust)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.EntityDragon#collideWithEntities} 原版巨翼风压机理：
 * 1. 【狂暴风压击退】：扇动巨龙双翼，强力击退周围 10 格内所有敌对生物并将其暴虐挑飞抛向高空；
 * 2. 【气流撕裂伤害】：对范围内的目标造成 8 点物理风压重击并附带迟缓效果；
 * 3. 【震撼视听呈现】：生成暴风气旋微粒（龙息+云雾+暴风扩散），伴随真实振翅与霸气龙吼。
 */
public class DragonWingFlapAction implements IAction, ICooldownAction {
    public static final String ID = "dragon_wing_flap";
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
        double radius = 10.0D;

        // 1. 播放震撼双翼振翅与恶龙咆哮音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 2.5F, 0.8F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 2. 激发环形暴风粒子气压圈
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            for (int i = 0; i < 36; i++) {
                double rad = Math.toRadians(i * 10);
                double px = entity.posX + Math.cos(rad) * 4.5D;
                double pz = entity.posZ + Math.sin(rad) * 4.5D;
                ws.spawnParticle(EnumParticleTypes.CLOUD, px, entity.posY + 0.6D, pz,
                        2, Math.cos(rad) * 0.4D, 0.15D, Math.sin(rad) * 0.4D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, px, entity.posY + 0.8D, pz,
                        1, 0.1D, 0.1D, 0.1D, 0.02D);
            }
            ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, entity.posX, entity.posY + 1.0D, entity.posZ,
                    3, 1.5D, 0.5D, 1.5D, 0.0D);
        }

        // 3. 检索周围 10 格内非友方存活生物，执行原版巨翼抛空与击退
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 4.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double distSq = dx * dx + dz * dz;
            double dist = Math.max(0.25D, Math.sqrt(distSq));

            // 原版 EntityDragon 巨力强力吹飞 + 抛向半空
            target.motionX += (dx / dist) * 2.5D;
            target.motionY += 0.85D;
            target.motionZ += (dz / dist) * 2.5D;
            target.velocityChanged = true;

            // 承受风刃破空重击与短暂迟缓
            target.attackEntityFrom(source, 8.0F);
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1, false, true));

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                        target.posX, target.posY + target.height * 0.5D, target.posZ, 8, 0.2D, 0.2D, 0.2D, 0.1D);
            }
        }
    }
}

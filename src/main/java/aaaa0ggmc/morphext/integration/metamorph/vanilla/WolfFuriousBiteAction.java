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
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 狼主动技能：利齿撕咬扑杀 (Wolf Furious Bite)。
 * 源码级还原 EntityWolf 凶猛扑咬本能：
 * 向前小段位移飞扑咬向目标，造成 8 点物理伤害并附加 3 秒流血虚弱与迟缓。
 */
public class WolfFuriousBiteAction implements IAction, ICooldownAction {
    public static final String ID = "wolf_bite";
    public static final String ALIAS_ID = "furious_bite";
    public static final int COOLDOWN = 80; // 4 秒

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

        // 1. 向前小段位移飞扑
        entity.motionX += look.x * 0.48D;
        entity.motionY += 0.16D;
        entity.motionZ += look.z * 0.48D;
        entity.velocityChanged = true;

        // 2. 眼前獠牙撕扯与暴击光效
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.3D;
            double fy = entity.posY + entity.getEyeHeight() * 0.7D;
            double fz = entity.posZ + look.z * 1.3D;
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 12, 0.25D, 0.25D, 0.25D, 0.1D);
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }

        // 3. 索敌与扇形判定
        double reach = 3.2D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.4D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hit = false;
        for (EntityLivingBase e : list) {
            Vec3d toTarget = new Vec3d(e.posX - entity.posX, e.posY - entity.posY, e.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                // 造成 8 点物理伤害
                e.attackEntityFrom(source, 8.0F);
                // 附加 3 秒 (60 ticks) 流血虚弱与迟缓
                e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, true));
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true));
                hit = true;

                if (world instanceof WorldServer) {
                    WorldServer ws = (WorldServer) world;
                    // 流血飞溅血雾微粒 (REDSTONE)
                    ws.spawnParticle(EnumParticleTypes.REDSTONE,
                            e.posX, e.posY + e.height * 0.6D, e.posZ,
                            10, 0.2D, 0.2D, 0.2D, 0.0D);
                }
            }
        }

        // 4. 恶狼低吼与撕裂音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.PLAYERS, 1.4F, 1.2F);
        if (hit) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.3F, 1.4F);
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_WOLF_HURT, SoundCategory.PLAYERS, 1.0F, 1.3F);
        }
    }
}

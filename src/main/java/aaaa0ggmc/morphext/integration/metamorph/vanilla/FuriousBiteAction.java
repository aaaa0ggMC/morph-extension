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
 * 狼/掠食者主动技能：狂暴撕咬 (Furious Bite) - 极致手感版。
 * 扑跃突进，眼前爆发獠牙暴击粒子与撕扯声，扇形撕裂流血减速，未命中也有明显攻击反馈。
 */
public class FuriousBiteAction implements IAction, ICooldownAction {
    public static final String ID = "furious_bite";
    public static final int COOLDOWN = 50; // 2.5 秒

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

        // 迅猛前冲
        entity.motionX += look.x * 0.55D;
        entity.motionY += 0.15D;
        entity.motionZ += look.z * 0.55D;
        entity.velocityChanged = true;

        // 眼前獠牙撕扯光效
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.2D;
            double fy = entity.posY + entity.getEyeHeight() * 0.7D;
            double fz = entity.posZ + look.z * 1.2D;
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 10, 0.25D, 0.25D, 0.25D, 0.1D);
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }

        double reach = 3.2D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.2D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hit = false;
        for (EntityLivingBase e : list) {
            Vec3d toTarget = new Vec3d(e.posX - entity.posX, e.posY - entity.posY, e.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                e.attackEntityFrom(source, 8.5F);
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 2));
                e.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 80, 1));
                hit = true;
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.PLAYERS, 1.4F, 1.2F);
        if (hit) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.3F, 1.4F);
        }
    }
}

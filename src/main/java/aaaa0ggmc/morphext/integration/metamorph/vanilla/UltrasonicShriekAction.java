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
 * 蝙蝠主动技能：超声尖啸 (Ultrasonic Shriek)。
 * 释放刺耳的高频超声波冲击，使周围敌人神经紊乱，造成反胃、虚弱与微量破防伤害。
 */
public class UltrasonicShriekAction implements IAction, ICooldownAction {
    public static final String ID = "ultrasonic_shriek";
    public static final int COOLDOWN = 100; // 5 秒

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
        double radius = 7.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 3.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase e : list) {
            e.attackEntityFrom(source, 3.0F);
            e.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 80, 0));
            e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 0));
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0));
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.NOTE,
                    entity.posX, entity.posY + 0.3D, entity.posZ,
                    35, 1.5D, 0.5D, 1.5D, 0.1D);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_BAT_HURT, SoundCategory.PLAYERS, 1.8F, 1.8F);
    }
}

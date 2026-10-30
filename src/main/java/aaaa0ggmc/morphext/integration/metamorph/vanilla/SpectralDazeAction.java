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
 * 远古守卫者主动技能：远古幻影震慑 (Spectral Daze)。
 * 激发远古之灵的威严震慑，致盲并大幅削弱大范围内的敌人，使其陷入极度的混乱与恐慌。
 */
public class SpectralDazeAction implements IAction, ICooldownAction {
    public static final String ID = "spectral_daze";
    public static final int COOLDOWN = 140; // 7 秒

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
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 4.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase e : list) {
            e.attackEntityFrom(source, 7.0F);
            e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 80, 0));
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 2));
            e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 120, 1));
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.MOB_APPEARANCE,
                    entity.posX, entity.posY + 1.0D, entity.posZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.8F, 0.9F);
    }
}

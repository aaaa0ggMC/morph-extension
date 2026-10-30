package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 守卫者/远古守卫者主动技能：荆棘尖刺爆发 (Spike Burst)。
 * 瞬间展开全身海晶尖刺，刺穿周围逼近的敌人并造成击退与反刺伤害。
 */
public class SpikeBurstAction implements IAction, ICooldownAction {
    public static final String ID = "spike_burst";
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
        double radius = 4.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase e : list) {
            e.attackEntityFrom(source, 6.0F);
            double dx = e.posX - entity.posX;
            double dz = e.posZ - entity.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.1D) {
                e.motionX += (dx / dist) * 0.5D;
                e.motionZ += (dz / dist) * 0.5D;
                e.velocityChanged = true;
            }
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                    entity.posX, entity.posY + 0.5D, entity.posZ,
                    40, 1.2D, 0.5D, 1.2D, 0.2D);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.4F, 1.5F);
    }
}

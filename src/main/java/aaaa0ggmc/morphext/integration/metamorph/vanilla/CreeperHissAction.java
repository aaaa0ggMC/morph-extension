package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 苦力怕主动技能：嘶嘶威慑 (Creeper Hiss)。
 * 突然发出标志性的苦力怕即将引爆嘶嘶声，震慑周围 10 格内的非宿主生物，
 * 使其陷入惊慌后撤并附加短暂 3 秒迟缓效果。
 */
public class CreeperHissAction implements IAction, ICooldownAction {
    public static final String ID = "creeper_hiss";
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
        double x = entity.posX;
        double y = entity.posY + entity.height * 0.5D;
        double z = entity.posZ;

        // 强力惊悚的苦力怕嘶嘶声
        world.playSound(null, x, y, z, SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.PLAYERS, 2.2F, 1.2F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 20, 0.8, 0.4, 0.8, 0.05);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, x, y + 0.5, z, 5, 0.5, 0.3, 0.5, 0.0);
        }

        double radius = 10.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 3.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        for (EntityLivingBase target : targets) {
            // 短暂 3 秒缓慢 II
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1, false, true));

            // 惊慌后撤反冲
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double norm = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += (dx / norm) * 0.45D;
            target.motionY += 0.15D;
            target.motionZ += (dz / norm) * 0.45D;
            target.velocityChanged = true;
        }
    }
}

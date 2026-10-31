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
 * 末影人主动技能：暴怒尖啸 (Enderman Screech)。
 * 张开深渊巨口发出撕心裂肺的暴怒尖啸，震慑周围 12 格内的所有非友方生物，
 * 使其陷入 3 秒虚弱与失明状态，并产生强力声波斥力。
 */
public class EndermanScreechAction implements IAction, ICooldownAction {
    public static final String ID = "enderman_screech";
    public static final int COOLDOWN = 360; // 18 秒

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

        world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_SCREAM, SoundCategory.PLAYERS, 2.4F, 0.85F);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_STARE, SoundCategory.PLAYERS, 2.0F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, x, y, z, 60, 1.2, 0.8, 1.2, 0.5);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 20, 1.0, 0.5, 1.0, 0.05);
        }

        double radius = 12.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 4.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        for (EntityLivingBase target : targets) {
            // 造成 3 秒 (60 ticks) 虚弱 I 与失明
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0, false, true));

            // 末影声波斥力推开
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double norm = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += (dx / norm) * 0.5D;
            target.motionY += 0.22D;
            target.motionZ += (dz / norm) * 0.5D;
            target.velocityChanged = true;
        }
    }
}

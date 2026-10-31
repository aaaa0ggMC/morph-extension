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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 鹦鹉主动技能：警戒鸣叫 (Parrot Alarm Call)。
 * <p>
 * 依据鸟类"警戒鸣叫"（alarm call）的生态行为与鹦鹉吵闹的社区印象——
 * 尖啸示警，将周围敌人惊退（击退 + 缓慢 I）并让自己轻快逃离（速度 I）。
 * 不造成伤害。
 */
public class ParrotAlarmCallAction implements IAction, ICooldownAction {
    public static final String ID = "parrot_alarm_call";
    public static final String ALIAS_ID = "morphext:parrot_alarm_call";
    public static final int COOLDOWN = 240; // 12 秒

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
        entity.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PARROT_HURT, SoundCategory.PLAYERS, 1.8F, 1.5F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.PLAYERS, 1.4F, 1.2F);

        // 惊退：击退 + 缓慢 I
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));
        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 0, false, true));
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double dist = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += dx / dist * 0.8D;
            target.motionY += 0.25D;
            target.motionZ += dz / dist * 0.8D;
            target.velocityChanged = true;
        }

        // 轻快逃离
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 100, 0, false, true));

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.NOTE, entity.posX, entity.posY + entity.height + 0.3D, entity.posZ, 12, 0.6D, 0.3D, 0.6D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.CLOUD, entity.posX, entity.posY + 0.2D, entity.posZ, 10, 1.0D, 0.3D, 1.0D, 0.02D);
        }
    }
}

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
 * 鱿鱼主动技能：浓墨喷射逃逸 (Ink Jet)。
 * 爆发巨量漆黑浓墨云团致盲并减速周围 5 格内的敌人，同时借助强劲的水流反冲力向前猛烈冲刺推进，
 * 绝佳的脱战与机动技能。
 */
public class InkJetAction implements IAction, ICooldownAction {
    public static final String ID = "ink_jet";
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
        Vec3d look = entity.getLookVec();
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 喷射乌贼墨与破水飞溅音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.8F, 1.2F);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SWIM, SoundCategory.PLAYERS, 1.2F, 1.4F);

        // 强劲冲刺推进（水中或陆地皆可爆发机动）
        entity.motionX += look.x * 1.35D;
        entity.motionY += look.y * 0.85D + 0.22D;
        entity.motionZ += look.z * 1.35D;
        entity.velocityChanged = true;

        // 浓墨大黑雾粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y + 0.5D, z, 45, 1.5, 0.8, 1.5, 0.05);
        }

        // 周围敌对目标致盲（6 秒）与深陷黏腻减速（4 秒）
        double radius = 5.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());

        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 120, 0, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1, false, true));
        }
    }
}

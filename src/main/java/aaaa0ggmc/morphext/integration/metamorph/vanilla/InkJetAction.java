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
 * 鱿鱼主动技能：浓墨喷射与反冲逃逸 (Ink Jet & Recoil Escape)。
 * 深度还原原版 EntitySquid 触手喷墨与逃生机动机制：
 * 1. 喷射巨量漆黑浓墨云团，致盲 (Blindness) 并重度减速周围 6 格内的敌人；
 * 2. 借助强劲的水流反冲推进力，向视线方向猛烈冲刺飞跃，实现绝佳的脱战与战场机动；
 * 3. 伴随鱿鱼受击应激声、水花飞溅声与大范围浓黑烟雾粒子。
 */
public class InkJetAction implements IAction, ICooldownAction {
    public static final String ID = "ink_jet";
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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 喷射乌贼墨应激音效与破水飞溅音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_SQUID_HURT, SoundCategory.PLAYERS, 1.5F, 1.2F);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.8F, 1.2F);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SWIM, SoundCategory.PLAYERS, 1.4F, 1.4F);

        // 强劲水流反冲推进（在水中与陆地皆可爆发机动）
        entity.motionX += look.x * 1.45D;
        entity.motionY += look.y * 0.95D + 0.25D;
        entity.motionZ += look.z * 1.45D;
        entity.velocityChanged = true;

        // 浓黑乌贼墨大雾微粒
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y + 0.5D, z, 55, 1.8D, 1.0D, 1.8D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y + 0.5D, z, 30, 1.2D, 0.8D, 1.2D, 0.15D);
        }

        // 周围敌对目标致盲（6 秒）与深陷浓墨减速 II（4 秒）
        double radius = 6.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.5D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 120, 0, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1, false, true));
        }
    }
}

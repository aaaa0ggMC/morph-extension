package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * 豹猫/猫主动技能：灵猫跃击与威慑 (Feline Pounce)。
 * 展现猫科动物极其灵动的身法向前猛跃，获得疾跑与跳跃提升；
 * 发出凶猛哈气嘶鸣威慑周围 10 格内的苦力怕，强行重置其引信并将其吓退击飞。
 */
public class FelinePounceAction implements IAction, ICooldownAction {
    public static final String ID = "feline_pounce";
    public static final int COOLDOWN = 70; // 3.5 秒

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

        // 猫哈气威慑音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_CAT_HISS, SoundCategory.PLAYERS, 1.6F, 1.1F);

        // 灵动扑击位移
        entity.motionX += look.x * 1.25D;
        entity.motionY += 0.42D;
        entity.motionZ += look.z * 1.25D;
        entity.velocityChanged = true;

        // 自身灵巧敏捷增益（4 秒）
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 80, 1, false, false));
        entity.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 80, 1, false, false));

        // 威慑苦力怕机制：熄灭引信并击退
        double radius = 10.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 3.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e instanceof EntityCreeper && e.isEntityAlive());

        for (EntityLivingBase e : list) {
            EntityCreeper creeper = (EntityCreeper) e;
            // 熄灭引信，阻止引爆
            creeper.setCreeperState(-1);

            double dx = creeper.posX - x;
            double dz = creeper.posZ - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.01) {
                creeper.motionX += (dx / dist) * 0.7D;
                creeper.motionY += 0.25D;
                creeper.motionZ += (dz / dist) * 0.7D;
                creeper.velocityChanged = true;
            }
        }
    }
}

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
 * 蝙蝠主动技能：超声波回声探测/定位 (Echolocation)。
 * 发出超声波脉冲，获得夜视能力，并将周围 24 格内的所有生物高亮显示（发光效果），
 * 同时使近距离的敌对生物因强烈声波而轻微眩晕（缓慢效果）。
 */
public class EcholocationAction implements IAction, ICooldownAction {
    public static final String ID = "echolocation";
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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 蝙蝠振翅与高频回声声效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.2F, 1.8F);
        world.playSound(null, x, y, z, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.PLAYERS, 1.0F, 2.0F);

        // 自身获得夜视效果（15 秒）
        entity.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 300, 0, false, false));

        // 环形声波粒子扩散
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            for (int angle = 0; angle < 360; angle += 20) {
                double rad = Math.toRadians(angle);
                double px = x + Math.cos(rad) * 2.0;
                double pz = z + Math.sin(rad) * 2.0;
                ws.spawnParticle(EnumParticleTypes.SPELL_MOB, px, y + 0.6, pz, 1, 0.2, 0.8, 1.0, 1.0);
            }
        }

        // 探测周围生物：24 格内发光高亮（8 秒）
        double range = 24.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(range, range * 0.5, range);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());

        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 160, 0, false, true));

            // 近距离（5 格内）声波震慑，减速 3 秒
            if (entity.getDistanceSq(target) <= 25.0D) {
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true));
            }
        }
    }
}

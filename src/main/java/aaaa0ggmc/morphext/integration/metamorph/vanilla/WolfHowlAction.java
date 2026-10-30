package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.AbstractSkeleton;
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
 * 狼主动技能：头狼战吼 (Wolf Howl)。
 * 仰天长啸激发群狼战意，为自身及周围 12 格内的友军提供力量、速度与抗性提升（持续 10 秒）；
 * 同时凶猛的嚎叫威慑周围 10 格内的骷髅类亡灵生物，令其胆寒虚弱并将其震退。
 */
public class WolfHowlAction implements IAction, ICooldownAction {
    public static final String ID = "wolf_howl";
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
        double y = entity.posY;
        double z = entity.posZ;

        // 雄浑狼嚎音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 音符与战意高昂粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.NOTE, x, y + 1.2D, z, 15, 0.8, 0.4, 0.8, 0.0);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y + 0.8D, z, 12, 0.6, 0.3, 0.6, 0.0);
        }

        // 施法者自身获得全套狼族战意增益（10 秒）
        entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 200, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 200, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 200, 0, false, true));

        // 作用于周围 12 格
        double radius = 12.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 4.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());

        for (EntityLivingBase target : list) {
            // 骷髅天敌威慑：若是骷髅类亡灵，产生恐惧震退与虚弱
            if (target instanceof AbstractSkeleton) {
                target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 140, 0, false, true));
                double dx = target.posX - x;
                double dz = target.posZ - z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01) {
                    target.motionX += (dx / dist) * 0.6D;
                    target.motionY += 0.25D;
                    target.motionZ += (dz / dist) * 0.6D;
                    target.velocityChanged = true;
                }
            } else if (target.isOnSameTeam(entity) || !target.isEntityUndead()) {
                // 友军或友善伙伴共享狼嚎战意增益
                target.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 200, 0, false, true));
                target.addPotionEffect(new PotionEffect(MobEffects.SPEED, 200, 0, false, true));
            }
        }
    }
}

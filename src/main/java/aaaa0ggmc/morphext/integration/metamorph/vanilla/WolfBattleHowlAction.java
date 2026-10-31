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
 * 狼主动技能：狼群战意长嚎 (Wolf Battle Howl)。
 * 源码级还原 EntityWolf 狼嚎与群居猎手本能：
 * 仰天长嚎（播放原版狼嚎叫声与战意粒子），使自身与周围 16 格队友获得 12 秒力量 I 与速度 I；
 * 同时狼族天威震慑周围骷髅亡灵，令其畏惧虚弱并被声波震退。
 */
public class WolfBattleHowlAction implements IAction, ICooldownAction {
    public static final String ID = "wolf_howl";
    public static final String ALIAS_ID = "wolf_battle_howl";
    public static final int COOLDOWN = 300; // 15 秒

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

        // 1. 雄浑原版狼嚎音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 2. 音符与战意高昂激荡粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.NOTE, x, y + 1.2D, z, 18, 0.8D, 0.4D, 0.8D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y + 0.8D, z, 15, 0.6D, 0.3D, 0.6D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.CRIT, x, y + 0.9D, z, 10, 0.5D, 0.3D, 0.5D, 0.1D);
        }

        // 3. 施法者自身获得 12 秒 (240 ticks) 力量 I、速度 I 与抗性提升 I
        entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 240, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 240, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 240, 0, false, true));

        // 4. 周围 16 格范围同队队友或友善生物共享 12 秒力量 I 与速度 I
        double radius = 16.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 6.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());

        for (EntityLivingBase target : list) {
            // 骷髅天敌震慑：骷髅类生物产生恐惧震退与虚弱
            if (target instanceof AbstractSkeleton) {
                target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 160, 0, false, true));
                double dx = target.posX - x;
                double dz = target.posZ - z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01D) {
                    target.motionX += (dx / dist) * 0.65D;
                    target.motionY += 0.25D;
                    target.motionZ += (dz / dist) * 0.65D;
                    target.velocityChanged = true;
                }
            } else if (target.isOnSameTeam(entity) || !target.isEntityUndead()) {
                // 队友共享群狼战意
                target.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 240, 0, false, true));
                target.addPotionEffect(new PotionEffect(MobEffects.SPEED, 240, 0, false, true));
            }
        }
    }
}

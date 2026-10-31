package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 鹦鹉主动技能：狂欢派对鹦鹉摇摆舞 (Party Parrot Dance)。
 * <p>
 * 严谨复刻原版 {@link net.minecraft.entity.passive.EntityParrot#setPartying} 经典跳舞姿态：
 * 1. 触发魔性摇摆舞姿：向鹦鹉实体注入派对状态，律动摆头、欢快蹦跶；
 * 2. 律动音效与欢呼：奏响原版唱片轻快旋律 {@link SoundEvents#RECORD_CHIRP}；
 * 3. 狂欢派对士气光环：为自身与周围 7 格范围内的队友提供 6 秒迅捷 I 与跳跃提升 I 增益；
 * 4. 环形漫天彩虹音符与爱心粒子；
 * 5. 5 秒（100 Tick）派对结束后平稳复位。
 */
public class ParrotDanceAction implements IAction, ICooldownAction {
    public static final String ID = "parrot_dance";
    public static final int COOLDOWN = 140; // 7 秒冷却

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
        entity.swingArm(EnumHand.MAIN_HAND);

        // 1. 设置鹦鹉跳舞姿态 (partyParrot)
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityParrot) {
                ((EntityParrot) inner).setPartying(entity.getPosition(), true);
            }
        }

        // 2. 欢快轻快的派对旋律
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.RECORD_CHIRP, SoundCategory.RECORDS, 1.8F, 1.1F);

        // 3. 狂欢士气光环：自身与周围友方获得增益
        double buffRadius = 7.0D;
        AxisAlignedBB buffArea = entity.getEntityBoundingBox().grow(buffRadius, 3.0D, buffRadius);
        List<EntityLivingBase> allies = world.getEntitiesWithinAABB(EntityLivingBase.class, buffArea,
                e -> e != null && e.isEntityAlive() && (e == entity || e.isOnSameTeam(entity)));

        for (EntityLivingBase ally : allies) {
            ally.addPotionEffect(new PotionEffect(MobEffects.SPEED, 120, 0, false, true));
            ally.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 120, 0, false, true));
        }

        // 4. 绚丽跳跃音符与爱心粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            for (int i = 0; i < 20; i++) {
                double rad = Math.toRadians(i * 18.0D);
                double px = entity.posX + Math.cos(rad) * 1.5D;
                double pz = entity.posZ + Math.sin(rad) * 1.5D;
                ws.spawnParticle(EnumParticleTypes.NOTE, px, entity.posY + 0.6D, pz, 1, 0.0, 0.0, 0.0, (double) i / 24.0D);
            }
            ws.spawnParticle(EnumParticleTypes.HEART, entity.posX, entity.posY + entity.getEyeHeight() + 0.3D, entity.posZ,
                    5, 0.3, 0.2, 0.3, 0.0);
        }

        // 5. 5 秒（100 Tick）派对尽兴后复位
        ServerActionScheduler.schedule(100, () -> {
            if (morph instanceof EntityMorph) {
                EntityLivingBase inner = ((EntityMorph) morph).getEntity();
                if (inner instanceof EntityParrot) {
                    ((EntityParrot) inner).setPartying(null, false);
                }
            }
        });
    }
}

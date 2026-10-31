package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.*;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.*;

/**
 * 鹦鹉主动技能：原版怪物拟声警报与声学诱导 (Parrot Mimicry)。
 * <p>
 * 严谨调取 {@link net.minecraft.entity.passive.EntityParrot} 源码中注册的全部模仿声效库：
 * 1. 20 格广域敌情声纳侦测：检索周围潜伏的敌对怪物（爬行者、僵尸、骷髅、末影人、烈焰人、恶魂等）；
 * 2. 精准模仿发声：若感知到潜伏怪物，鹦鹉立即惟妙惟肖地发出该怪物的拟声警报（例如模仿苦力怕咝咝声 {@link SoundEvents#E_PARROT_IM_CREEPER}）；
 * 3. 破潜侦察标记：向队友精准标示被拟声怪物的轮廓（赋予被模仿目标 4 秒发光 {@link MobEffects#GLOWING}）；
 * 4. 闲置拟声恶作剧：若周围并无怪物，则随机模仿一种怪叫或欢快啼鸣，伴随音符粒子。
 */
public class ParrotMimicAction implements IAction, ICooldownAction {
    public static final String ID = "parrot_mimic";
    public static final int COOLDOWN = 60; // 3 秒冷却

    private static final Map<Class<? extends EntityLivingBase>, SoundEvent> MIMIC_MAP = new HashMap<>();
    private static final List<SoundEvent> RANDOM_SOUNDS = new ArrayList<>();
    private static final Random RAND = new Random();

    static {
        MIMIC_MAP.put(EntityCreeper.class, SoundEvents.E_PARROT_IM_CREEPER);
        MIMIC_MAP.put(EntityZombie.class, SoundEvents.E_PARROT_IM_ZOMBIE);
        MIMIC_MAP.put(EntityHusk.class, SoundEvents.E_PARROT_IM_HUSK);
        MIMIC_MAP.put(EntitySkeleton.class, SoundEvents.E_PARROT_IM_SKELETON);
        MIMIC_MAP.put(EntityStray.class, SoundEvents.E_PARROT_IM_STRAY);
        MIMIC_MAP.put(EntitySpider.class, SoundEvents.E_PARROT_IM_SPIDER);
        MIMIC_MAP.put(EntityCaveSpider.class, SoundEvents.E_PARROT_IM_SPIDER);
        MIMIC_MAP.put(EntityEnderman.class, SoundEvents.E_PARROT_IM_ENDERMAN);
        MIMIC_MAP.put(EntityBlaze.class, SoundEvents.E_PARROT_IM_BLAZE);
        MIMIC_MAP.put(EntityGhast.class, SoundEvents.E_PARROT_IM_GHAST);
        MIMIC_MAP.put(EntityWitch.class, SoundEvents.E_PARROT_IM_WITCH);
        MIMIC_MAP.put(EntitySlime.class, SoundEvents.E_PARROT_IM_SLIME);
        MIMIC_MAP.put(EntityMagmaCube.class, SoundEvents.E_PARROT_IM_MAGMACUBE);
        MIMIC_MAP.put(EntityVex.class, SoundEvents.E_PARROT_IM_VEX);
        MIMIC_MAP.put(EntityVindicator.class, SoundEvents.E_PARROT_IM_VINDICATION_ILLAGER);
        MIMIC_MAP.put(EntityEvoker.class, SoundEvents.E_PARROT_IM_EVOCATION_ILLAGER);
        MIMIC_MAP.put(EntityIllusionIllager.class, SoundEvents.E_PARROT_IM_ILLUSION_ILLAGER);
        MIMIC_MAP.put(EntityElderGuardian.class, SoundEvents.E_PARROT_IM_ELDER_GUARDIAN);
        MIMIC_MAP.put(EntityWitherSkeleton.class, SoundEvents.E_PARROT_IM_WITHER_SKELETON);

        RANDOM_SOUNDS.addAll(MIMIC_MAP.values());
        RANDOM_SOUNDS.add(SoundEvents.ENTITY_PARROT_AMBIENT);
    }

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

        // 1. 20 格范围侦测周围敌对生物
        double reach = 20.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 10.0D, reach);
        List<EntityLiving> mobs = world.getEntitiesWithinAABB(EntityLiving.class, box,
                m -> m != null && m.isEntityAlive() && !m.isSilent());

        SoundEvent soundToPlay = null;
        EntityLiving spottedTarget = null;

        for (EntityLiving mob : mobs) {
            for (Map.Entry<Class<? extends EntityLivingBase>, SoundEvent> entry : MIMIC_MAP.entrySet()) {
                if (entry.getKey().isInstance(mob)) {
                    soundToPlay = entry.getValue();
                    spottedTarget = mob;
                    break;
                }
            }
            if (soundToPlay != null) {
                break;
            }
        }

        // 2. 若无敌对怪物，则随机拟声恶作剧或啼鸣
        if (soundToPlay == null) {
            soundToPlay = RANDOM_SOUNDS.get(RAND.nextInt(RANDOM_SOUNDS.size()));
        } else {
            // 标示潜伏目标
            spottedTarget.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 80, 0, false, false));
        }

        // 3. 播放拟音声效（音调微调体现小鸟特有的尖细俏皮口音）
        float pitch = 0.95F + RAND.nextFloat() * 0.15F;
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                soundToPlay, SoundCategory.PLAYERS, 1.4F, pitch);

        // 4. 音符粒子四溢
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.NOTE, entity.posX, entity.posY + entity.getEyeHeight() + 0.2D, entity.posZ,
                    8, 0.3, 0.2, 0.3, 0.0);
        }
    }
}

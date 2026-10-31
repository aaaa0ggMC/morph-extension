package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 末影人主动技能：暴怒尖啸与张嘴狂怒 (Enderman Screech & Rage)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.monster.EntityEnderman} 眼神对视激怒与咆哮机理：
 * 1. 【原版张嘴发怒动画】：通过反射同步激活内部 {@code EntityEnderman} 的 {@code SCREAMING} 数据项，
 *    在变身模型上完美呈现撕心裂肺的裂嘴狂怒姿态；
 * 2. 【移速狂怒强化】：赋予自身 10 秒速度 II 与力量 I 增益（对应原版末影人索敌移速加成）；
 * 3. 【深渊声波震慑】：对周围 12 格内的所有敌对目标施加虚弱与失明，并产生末影斥力强力推开。
 */
public class EndermanScreechAction implements IAction, ICooldownAction {
    public static final String ID = "enderman_screech";
    public static final int COOLDOWN = 240; // 12 秒冷却

    private static DataParameter<Boolean> screamingParam = null;
    private static boolean paramLookedUp = false;

    @SuppressWarnings("unchecked")
    private static DataParameter<Boolean> getScreamingParam() {
        if (!paramLookedUp) {
            paramLookedUp = true;
            try {
                Field f = EntityEnderman.class.getDeclaredField("SCREAMING");
                f.setAccessible(true);
                screamingParam = (DataParameter<Boolean>) f.get(null);
            } catch (Throwable ignored) {
            }
        }
        return screamingParam;
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
        double x = entity.posX;
        double y = entity.posY + entity.height * 0.5D;
        double z = entity.posZ;

        // 1. 同步激活客户端末影人张嘴咆哮 (SCREAMING) 姿态
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityEnderman) {
                DataParameter<Boolean> param = getScreamingParam();
                if (param != null) {
                    try {
                        inner.getDataManager().set(param, Boolean.TRUE);
                        ServerActionScheduler.schedule(100, () -> {
                            if (inner != null && inner.isEntityAlive()) {
                                inner.getDataManager().set(param, Boolean.FALSE);
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        // 2. 赋予宿主自身 10 秒速度 II 与力量 I（狂怒移速加成）
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 200, 1, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 200, 0, false, true));

        // 3. 播放撕心裂肺的暴怒尖啸与直视凝视音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_SCREAM, SoundCategory.PLAYERS, 2.4F, 0.85F);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_STARE, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 4. 周身末影微粒与烟雾
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, x, y, z, 60, 1.2D, 0.8D, 1.2D, 0.5D);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 20, 1.0D, 0.5D, 1.0D, 0.05D);
        }

        // 5. 范围声波斥力与虚弱、失明
        double radius = 12.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 4.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 80, 0, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 80, 0, false, true));

            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double norm = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += (dx / norm) * 0.55D;
            target.motionY += 0.24D;
            target.motionZ += (dz / norm) * 0.55D;
            target.velocityChanged = true;
        }
    }
}

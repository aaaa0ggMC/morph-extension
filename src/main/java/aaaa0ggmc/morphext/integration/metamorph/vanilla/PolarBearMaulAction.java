package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 北极熊主动技能：人立暴怒猛击 (Polar Bear Maul)。
 * 源码级还原 EntityPolarBear 人立姿态与重击动作：
 * 人立而起（触发前足抬起站立动画与咆哮音效），向前猛力挥爪扑杀，
 * 造成 14 点纯正破甲重击 (setDamageBypassesArmor) 并附带 5 秒极寒迟缓 II。
 */
public class PolarBearMaulAction implements IAction, ICooldownAction {
    public static final String ID = "polar_bear_maul";
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
        Vec3d look = entity.getLookVec();

        entity.swingArm(EnumHand.MAIN_HAND);

        // 1. 人立姿态触发 (设置 EntityPolarBear.setStanding(true) 并在 25 ticks 后还原)
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity(world);
            if (dummy instanceof EntityPolarBear) {
                EntityPolarBear bear = (EntityPolarBear) dummy;
                bear.setStanding(true);
                ServerActionScheduler.schedule(25, () -> {
                    if (bear.isEntityAlive()) {
                        bear.setStanding(false);
                    }
                });
            }
        }

        // 2. 巨兽扑杀位移
        entity.motionX += look.x * 0.55D;
        entity.motionY += 0.18D;
        entity.motionZ += look.z * 0.55D;
        entity.velocityChanged = true;

        // 3. 人立示警与暴怒撕咬音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.PLAYERS, 1.8F, 0.85F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_POLAR_BEAR_HURT, SoundCategory.PLAYERS, 1.4F, 0.75F);

        // 4. 暴怒利爪横扫与极寒飞雪粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.8D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.8D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2D, 0.1D, 0.2D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.SNOWBALL, fx, fy, fz, 16, 0.4D, 0.3D, 0.4D, 0.1D);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 14, 0.35D, 0.3D, 0.35D, 0.15D);
        }

        // 5. 命中结算：3.8 格范围内扇形判定，14 点纯正破甲重击
        double reach = 3.8D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.6D, reach);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        boolean hitAny = false;
        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.32D) {
                target.attackEntityFrom(source, 14.0F);
                // 极寒迟缓 II 持续 5 秒 (100 ticks)
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1, false, true));

                // 巨力震飞
                target.motionX += look.x * 0.65D;
                target.motionY += 0.35D;
                target.motionZ += look.z * 0.65D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ,
                            10, 0.25D, 0.25D, 0.25D, 0.1D);
                }
            }
        }

        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.5F, 0.85F);
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
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
 * 卫道士主动技能：夺命斧冲 (Vindicator Axe Charge)。
 * 高举致命铁斧向前暴走冲杀，获得 3 秒速度 II，并在冲撞或身前 3.5 格范围劈出致命战斧一击（12 点巨额伤害），
 * 强行击碎并禁用目标的举盾防御。
 */
public class VindicatorAxeChargeAction implements IAction, ICooldownAction {
    public static final String ID = "vindicator_axe_charge";
    public static final int COOLDOWN = 240; // 12 秒

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

        // 暴走冲锋加速 (速度 II 持续 3 秒) 与爆发推力
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 1, false, false));
        entity.motionX += look.x * 0.85D;
        entity.motionY += 0.12D;
        entity.motionZ += look.z * 0.85D;
        entity.velocityChanged = true;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.VINDICATION_ILLAGER_AMBIENT, SoundCategory.PLAYERS, 1.6F, 0.9F);

        // 身前破空剑气与劈砍火花
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.8D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.8D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2, 0.1, 0.2, 0.0);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 15, 0.4, 0.3, 0.4, 0.15);
        }

        // 身前 3.5 格扇形致命劈砍判定
        double reach = 3.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.5D, reach);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hitAny = false;
        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                // 12 点致命劈砍伤害
                target.attackEntityFrom(source, 12.0F);

                // 强行破盾机制
                if (target instanceof EntityPlayer) {
                    EntityPlayer tp = (EntityPlayer) target;
                    if (tp.isActiveItemStackBlocking()) {
                        tp.disableShield(true);
                        world.playSound(null, tp.posX, tp.posY, tp.posZ,
                                SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.6F, 1.0F);
                    }
                }

                // 强劲击退
                target.motionX += look.x * 0.65D;
                target.motionY += 0.30D;
                target.motionZ += look.z * 0.65D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 10, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }

        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.6F, 1.1F);
        }
    }
}

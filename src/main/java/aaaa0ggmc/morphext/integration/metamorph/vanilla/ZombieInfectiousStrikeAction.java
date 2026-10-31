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
 * 僵尸主动技能：腐败挥击 (Zombie Infectious Strike)。
 * 挥舞腐朽双爪向前猛击身前 3.5 格范围内的敌人，造成撕扯伤害，并附带 8 秒饥饿与 3 秒迟缓减速。
 */
public class ZombieInfectiousStrikeAction implements IAction, ICooldownAction {
    public static final String ID = "zombie_infectious_strike";
    public static final int COOLDOWN = 120; // 6 秒

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
        entity.motionX += look.x * 0.30D;
        entity.motionZ += look.z * 0.30D;
        entity.velocityChanged = true;

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.6D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.6D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2, 0.1, 0.2, 0.0);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 10, 0.3, 0.2, 0.3, 0.1);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, fx, fy, fz, 8, 0.1, 0.4, 0.1, 1.0);
        }

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
                target.attackEntityFrom(source, 6.0F);
                // 附加 8 秒饥饿与 3 秒迟缓
                target.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 160, 0, false, true));
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true));

                target.motionX += look.x * 0.35D;
                target.motionY += 0.20D;
                target.motionZ += look.z * 0.35D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 6, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.PLAYERS, 1.4F, 0.9F);
        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.PLAYERS, 1.2F, 1.1F);
        }
    }
}

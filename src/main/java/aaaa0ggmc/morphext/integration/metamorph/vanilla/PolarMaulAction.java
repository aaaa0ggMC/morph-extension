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
 * 北极熊主动技能：极地暴熊扑击 (Polar Maul)。
 * 熊立前扑猛击身前 3.5 格范围内的敌人，造成 12 点巨额物理撕裂伤害与 5 秒极寒迟缓 II。
 */
public class PolarMaulAction implements IAction, ICooldownAction {
    public static final String ID = "polar_maul";
    public static final int COOLDOWN = 160; // 8 秒

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

        // 巨兽扑击位移
        entity.motionX += look.x * 0.50D;
        entity.motionY += 0.15D;
        entity.motionZ += look.z * 0.50D;
        entity.velocityChanged = true;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.PLAYERS, 1.8F, 0.85F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.8D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.8D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2, 0.1, 0.2, 0.0);
            ws.spawnParticle(EnumParticleTypes.SNOWBALL, fx, fy, fz, 15, 0.4, 0.3, 0.4, 0.1);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 12, 0.3, 0.3, 0.3, 0.15);
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
                target.attackEntityFrom(source, 12.0F);
                // 极寒迟缓 II 持续 5 秒 (100 ticks)
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1, false, true));

                // 巨力击飞
                target.motionX += look.x * 0.60D;
                target.motionY += 0.35D;
                target.motionZ += look.z * 0.60D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 8, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }

        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.4F, 0.9F);
        }
    }
}

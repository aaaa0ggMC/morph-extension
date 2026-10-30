package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
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
 * 豹猫/猫主动技能：灵猫跃击 (Feline Pounce)。
 * 强力前扑位移，熄灭苦力怕引信并震退，对前方所有非友军生物造成利爪撕扯伤害与减速。
 */
public class FelinePounceAction implements IAction, ICooldownAction {
    public static final String ID = "feline_pounce";
    public static final int COOLDOWN = 70; // 3.5 秒

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

        // 灵动扑击位移
        entity.motionX += look.x * 1.35D;
        entity.motionY += 0.45D;
        entity.motionZ += look.z * 1.35D;
        entity.velocityChanged = true;

        // 自身灵巧敏捷增益
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 80, 1, false, false));
        entity.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 80, 1, false, false));

        // 利爪破空火花
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.5D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.5D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2D, 0.2D, 0.2D, 0.0D);
        }

        double radius = 4.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : list) {
            // 特殊威慑：苦力怕熄灭引信并击退
            if (target instanceof EntityCreeper) {
                EntityCreeper creeper = (EntityCreeper) target;
                creeper.setCreeperState(-1);
                double dx = creeper.posX - entity.posX;
                double dz = creeper.posZ - entity.posZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01) {
                    creeper.motionX += (dx / dist) * 0.8D;
                    creeper.motionY += 0.35D;
                    creeper.motionZ += (dz / dist) * 0.8D;
                    creeper.velocityChanged = true;
                }
            } else {
                // 普通生物：利爪割裂伤害与硬直
                Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
                if (look.dotProduct(toTarget) > 0.3D) {
                    target.attackEntityFrom(source, 6.0F);
                    target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 1));
                }
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_CAT_HISS, SoundCategory.PLAYERS, 1.6F, 1.1F);
    }
}

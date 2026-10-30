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
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 狼主动技能：狂暴撕咬 (Furious Bite)。
 * 锁定正前方敌人迅猛突击撕咬，造成流血与剧烈残废减速效果。
 */
public class FuriousBiteAction implements IAction, ICooldownAction {
    public static final String ID = "furious_bite";
    public static final int COOLDOWN = 50; // 2.5 秒

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

        // 稍微向前冲刺
        entity.motionX += look.x * 0.45D;
        entity.motionZ += look.z * 0.45D;
        entity.velocityChanged = true;

        double reach = 3.2D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.2D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase e : list) {
            Vec3d toTarget = new Vec3d(e.posX - entity.posX, e.posY - entity.posY, e.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.4D) {
                e.attackEntityFrom(source, 8.0F);
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 2));
                e.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 80, 1));
                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            e.posX, e.posY + 0.5D, e.posZ, 8, 0.2D, 0.2D, 0.2D, 0.1D);
                }
                break;
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.PLAYERS, 1.4F, 1.2F);
    }
}

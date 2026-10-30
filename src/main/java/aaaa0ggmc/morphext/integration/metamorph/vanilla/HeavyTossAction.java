package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 铁傀儡主动技能：铁臂挑飞 (Heavy Toss)。
 * 挥动沉重铁臂对身前敌人进行致命上挑，造成单体高额伤害并将其高高挑飞至空中。
 */
public class HeavyTossAction implements IAction, ICooldownAction {
    public static final String ID = "heavy_toss";
    public static final int COOLDOWN = 60; // 3 秒

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
        double reach = 3.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.5D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        EntityLivingBase target = null;
        double minDot = 0.5D; // 前方扇形
        for (EntityLivingBase e : list) {
            Vec3d toTarget = new Vec3d(e.posX - entity.posX, e.posY - entity.posY, e.posZ - entity.posZ).normalize();
            double dot = look.dotProduct(toTarget);
            if (dot > minDot) {
                target = e;
                break;
            }
        }

        if (target != null) {
            target.attackEntityFrom(source, 10.0F);
            target.motionY += 0.65D;
            target.motionX += look.x * 0.4D;
            target.motionZ += look.z * 0.4D;
            target.velocityChanged = true;

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SWEEP_ATTACK,
                        target.posX, target.posY + 0.5D, target.posZ,
                        5, 0.2D, 0.2D, 0.2D, 0.0D);
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.5F, 1.0F);
    }
}

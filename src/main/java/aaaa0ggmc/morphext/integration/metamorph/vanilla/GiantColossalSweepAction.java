package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
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
 * 远古巨人巨臂横扫 (Giant Colossal Sweep Action):
 * 挥舞擎天巨掌向前横扫半圆，对前方 8 格扇面内全部目标造成 14 点物理重击与剧烈击退。
 */
public class GiantColossalSweepAction implements IAction {

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        entity.swingArm(EnumHand.MAIN_HAND);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 2.0F, 0.5F);

        if (world.isRemote) return;

        Vec3d look = entity.getLookVec();
        double radius = 8.0D;
        AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(radius, 3.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                e -> e != null && e.isEntityAlive() && !e.equals(entity));

        DamageSource src = entity instanceof EntityPlayer ?
                DamageSource.causePlayerDamage((EntityPlayer) entity) : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, 0, target.posZ - entity.posZ).normalize();
            double dot = look.x * toTarget.x + look.z * toTarget.z;
            if (dot > 0.1D) { // 前方视线扇面
                target.attackEntityFrom(src, 14.0F);
                target.knockBack(entity, 2.2F, -toTarget.x, -toTarget.z);
            }
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SWEEP_ATTACK,
                    entity.posX + look.x * 3.0D, entity.posY + 2.0D, entity.posZ + look.z * 3.0D,
                    10, 1.5D, 0.5D, 1.5D, 0.0D);
        }
    }
}

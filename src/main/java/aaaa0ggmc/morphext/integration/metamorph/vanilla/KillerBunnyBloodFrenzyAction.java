package aaaa0ggmc.morphext.integration.metamorph.vanilla;

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
 * 杀手兔血腥飞扑狂袭 (Killer Bunny Blood Frenzy Action):
 * 极速向前暴起前扑 5 格，对落点身前敌人造成 16 点致命真实重创并附带惊吓音效！
 */
public class KillerBunnyBloodFrenzyAction implements IAction {

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        Vec3d look = entity.getLookVec();

        // 强力前扑
        entity.motionX += look.x * 1.4D;
        entity.motionY += 0.35D;
        entity.motionZ += look.z * 1.4D;
        entity.velocityChanged = true;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_RABBIT_ATTACK, SoundCategory.HOSTILE, 1.6F, 0.7F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5F, 1.4F);

        if (world.isRemote) return;

        AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(2.5D, 1.5D, 2.5D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                e -> e != null && e.isEntityAlive() && !e.equals(entity));

        DamageSource src = entity instanceof EntityPlayer ?
                DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor() :
                DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        for (EntityLivingBase target : targets) {
            target.attackEntityFrom(src, 16.0F);
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.REDSTONE,
                    entity.posX + look.x * 0.8D, entity.posY + 0.4D, entity.posZ + look.z * 0.8D,
                    25, 0.4D, 0.3D, 0.4D, 0.05D);
        }
    }
}

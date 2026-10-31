package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
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
 * 绵羊主动技能：公羊冲撞 (Ram Charge)。
 * <p>
 * 依据公羊（ram）头撞顶角的生态行为——低头顶角向前猛冲，对冲撞路径上的敌人
 * 造成 4 点伤害并强力击退。单发伤害 4 点，符合平衡红线。
 */
public class RamChargeAction implements IAction, ICooldownAction {
    public static final String ID = "ram_charge";
    public static final String ALIAS_ID = "morphext:ram_charge";
    public static final int COOLDOWN = 240; // 12 秒
    public static final float DAMAGE = 4.0F;

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

        // 低头猛冲：前向冲量
        entity.motionX += look.x * 0.9D;
        entity.motionY = 0.25D;
        entity.motionZ += look.z * 0.9D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.6F, 0.7F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 0.9F);

        // 冲撞路径判定：前方锥形区域的敌人吃伤害 + 击退
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(3.0D, 1.5D, 3.0D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));
        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, 0, target.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) < 0.5D) {
                continue;
            }
            DamageSource source = entity instanceof EntityPlayer
                    ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                    : DamageSource.causeMobDamage(entity);
            target.attackEntityFrom(source, DAMAGE);
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            double dist = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.motionX += dx / dist * 1.1D;
            target.motionY += 0.35D;
            target.motionZ += dz / dist * 1.1D;
            target.velocityChanged = true;
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            Vec3d start = new Vec3d(entity.posX, entity.posY + 0.3D, entity.posZ);
            Vec3d end = start.add(look.x * 3.0D, 0.1D, look.z * 3.0D);
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.CRIT, 0.4D, 0.08D);
            ws.spawnParticle(EnumParticleTypes.CLOUD, entity.posX, entity.posY + 0.1D, entity.posZ, 10, 0.4D, 0.1D, 0.4D, 0.03D);
        }
    }
}

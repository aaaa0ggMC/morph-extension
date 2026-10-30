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
 * 蜘蛛/节肢生物主动技能：剧毒蛰刺 (Venom Strike) - 极致手感版。
 * 修正受击音效为挥舞与节肢嘶鸣，前扑穿刺，眼前爆发绿芒毒素与剑气，扇形中毒减速。
 */
public class VenomStrikeAction implements IAction, ICooldownAction {
    public static final String ID = "venom_strike";
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

        entity.swingArm(EnumHand.MAIN_HAND);

        // 扑跃突进
        entity.motionX += look.x * 0.60D;
        entity.motionY += 0.28D;
        entity.motionZ += look.z * 0.60D;
        entity.velocityChanged = true;

        // 眼前剧毒绿芒光弧
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.4D;
            double fy = entity.posY + entity.getEyeHeight() * 0.6D;
            double fz = entity.posZ + look.z * 1.4D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, fx, fy, fz, 15, 0.3D, 0.3D, 0.3D, 1.0D);
        }

        double reach = 3.4D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.4D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hit = false;
        for (EntityLivingBase e : list) {
            Vec3d toTarget = new Vec3d(e.posX - entity.posX, e.posY - entity.posY, e.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                e.attackEntityFrom(source, 6.5F);
                e.addPotionEffect(new PotionEffect(MobEffects.POISON, 100, 1));
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
                hit = true;
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.3F, 1.2F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.4F);
        if (hit) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.BLOCK_SLIME_BREAK, SoundCategory.PLAYERS, 1.2F, 1.6F);
        }
    }
}

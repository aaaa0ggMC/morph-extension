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
 * 凋灵骷髅主动技能：凋零斩击 (Wither Strike)。
 * 挥舞黑色重刃横扫身前 3.5 格扇形区域，命中敌人造成 9 点暗黑伤害并附加 10 秒凋零 II 效果，
 * 空挥亦有大范围破空音效与黑色凋零烟雾。
 */
public class WitherStrikeAction implements IAction, ICooldownAction {
    public static final String ID = "wither_strike";
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

        // 1. 挥动手臂与身法前踏
        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX += look.x * 0.35D;
        entity.motionZ += look.z * 0.35D;
        entity.velocityChanged = true;

        // 2. 空挥与前方的黑色凋零粒子弧光
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.8D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.8D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2D, 0.1D, 0.2D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, fx, fy, fz, 15, 0.4D, 0.2D, 0.4D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, fx, fy, fz, 10, 0.0D, 0.0D, 0.0D, 1.0D);
        }

        // 3. 身前 3.5 格 120 度扇形打击判定
        double reach = 3.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.5D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        boolean hitAny = false;
        for (EntityLivingBase target : list) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                target.attackEntityFrom(source, 9.0F);
                // 附加 10 秒 (200 ticks) 凋零 II (amplifier 1)
                target.addPotionEffect(new PotionEffect(MobEffects.WITHER, 200, 1, false, true));

                // 暗黑击退
                target.motionX += look.x * 0.42D;
                target.motionY += 0.20D;
                target.motionZ += look.z * 0.42D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 8, 0.2D, 0.2D, 0.2D, 0.1D);
                }
            }
        }

        // 4. 音效反馈
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 0.7F);
        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 1.4F, 1.2F);
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
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
 * 铁傀儡主动技能：铁臂挑飞 (Heavy Toss) - 极致动作手感版。
 * 沉重铁臂向前怒吼横扫挑飞，自带前踏位移，空挥展示大范围气刃与碎石，命中重创抛飞敌人。
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

        // 1. 挥动手臂
        entity.swingArm(EnumHand.MAIN_HAND);

        // 2. 进攻身法：向前重踏突进（平地滑步，赋予重量感）
        entity.motionX += look.x * 0.40D;
        entity.motionY += 0.10D;
        entity.motionZ += look.z * 0.40D;
        entity.velocityChanged = true;

        // 3. 无论是否命中，身前始终展示大范围扇形破空剑气与重踏灰尘
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.8D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.8D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2D, 0.1D, 0.2D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 12, 0.4D, 0.3D, 0.4D, 0.15D);
        }

        // 4. 前方锥形范围多目标检测
        double reach = 3.6D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.6D, reach);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hitAny = false;
        for (EntityLivingBase target : list) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
            double dot = look.dotProduct(toTarget);
            if (dot > 0.35D) { // 前方约 140 度扇形
                target.attackEntityFrom(source, 10.0F);
                // 标志性垂直重抛 + 前冲推击
                target.motionY += 0.68D;
                target.motionX += look.x * 0.45D;
                target.motionZ += look.z * 0.45D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 8, 0.2D, 0.2D, 0.2D, 0.1D);
                }
            }
        }

        // 5. 音效分层反馈
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 0.8F);
        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.6F, 1.0F);
        }
    }
}

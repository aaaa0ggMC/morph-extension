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
 * 骷髅主动技能：骨刃近身刺击 (Skeleton Melee Jab)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntitySkeleton} 近身搏杀防御机理：
 * 1. 【空挥破空手感】：无论是否命中，必定触发横扫刃风微粒与清脆破空音效，手感凌厉果决；
 * 2. 【身前 2.5 格横扫刺击】：在身前锥形范围内判定目标，造成 6 点坚实物理伤害与强力击退，击退贴脸敌人并拉开安全射击射程。
 */
public class SkeletonMeleeJabAction implements IAction, ICooldownAction {
    public static final String ID = "skeleton_melee_jab";
    public static final int COOLDOWN = 60; // 3 秒冷却 (60 ticks)

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
        entity.swingArm(EnumHand.MAIN_HAND);
        Vec3d look = entity.getLookVec();

        // 1. 无论命中与否，必有的空挥破空手感视听反馈
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 1.35F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.PLAYERS, 0.7F, 1.8F);

        double jabX = entity.posX + look.x * 1.5D;
        double jabY = entity.posY + (double) entity.getEyeHeight() - 0.2D + look.y * 1.5D;
        double jabZ = entity.posZ + look.z * 1.5D;

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, jabX, jabY, jabZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.CRIT, jabX, jabY, jabZ, 8, 0.2D, 0.2D, 0.2D, 0.05D);
        }

        // 2. 身前 2.5 格锥形近战索敌判定
        double reach = 2.5D;
        AxisAlignedBB jabBox = entity.getEntityBoundingBox().grow(reach, 1.2D, reach);
        List<EntityLivingBase> candidates = world.getEntitiesWithinAABB(EntityLivingBase.class, jabBox,
                target -> target != entity && target.isEntityAlive() && !target.isOnSameTeam(entity));

        Vec3d eyePos = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hitAny = false;

        for (EntityLivingBase target : candidates) {
            Vec3d targetCenter = new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ);
            Vec3d toTarget = targetCenter.subtract(eyePos);
            double dist = toTarget.length();

            if (dist <= reach) {
                double dot = look.dotProduct(toTarget.normalize());
                if (dot >= 0.35D) {
                    hitAny = true;
                    // 造成 6 点物理伤害
                    target.attackEntityFrom(source, 6.0F);

                    // 击退贴脸敌人，拉开射手距离
                    target.knockBack(entity, 0.6F, -look.x, -look.z);
                    target.velocityChanged = true;
                }
            }
        }

        // 3. 命中骨裂反馈音效
        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.1F, 1.1F);
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_SKELETON_STEP, SoundCategory.PLAYERS, 1.2F, 0.8F);
        }
    }
}

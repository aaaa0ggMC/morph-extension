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
 * 恼鬼无碰撞短距冲刺穿刺 (Vex Charge)。
 * <p>
 * 依据 {@code EntityVex} 的 AIChargeAttack（穿墙冲锋）与 limitedLifespan（召唤时限自杀机制）：
 * 1. 【无碰撞突刺】朝视线方向无碰撞短距爆冲（约 6 格），命中判定为线段盒检测、可穿墙命中；
 * 2. 【贯穿破甲】对路径两侧敌人造成 6 点无视护甲穿刺伤害与击退；
 * 3. 【自身损耗】每次冲刺对自身造成 1 点 STARVE 伤害——致敬恼鬼 limitedLifespan 的寿命燃烧。
 */
public class VexChargeAction implements IAction, ICooldownAction {
    public static final String ID = "vex_charge";
    public static final String ALIAS_ID = "morphext:vex_charge";
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

        entity.swingArm(EnumHand.MAIN_HAND);

        // 1. 短距无碰撞爆冲（约 6 格语义，命中靠下方线段盒、无视遮挡）
        entity.motionX = look.x * 1.7D;
        entity.motionY = look.y * 1.1D + 0.05D;
        entity.motionZ = look.z * 1.7D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        Vec3d start = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        Vec3d end = start.add(look.scale(6.0D));

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.REDSTONE, 0.35D, 0.0D);
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.CRIT, 0.4D, 0.05D);
        }

        // 2. 线段盒穿刺命中（不依赖视线，可穿墙命中）
        AxisAlignedBB pathBox = new AxisAlignedBB(
                Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        ).grow(1.4D);

        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, pathBox,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - start.x, target.posY + target.height * 0.5D - start.y, target.posZ - start.z);
            double proj = toTarget.dotProduct(look);
            if (proj >= 0.0D && proj <= 6.0D) {
                Vec3d closestPoint = start.add(look.scale(proj));
                if (closestPoint.distanceTo(new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ)) <= 1.6D) {
                    target.attackEntityFrom(source, 6.0F);
                    target.motionX += look.x * 0.55D;
                    target.motionY += 0.18D;
                    target.motionZ += look.z * 0.55D;
                    target.velocityChanged = true;

                    if (world instanceof WorldServer) {
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                                target.posX, target.posY + target.height * 0.5D, target.posZ, 6, 0.2D, 0.2D, 0.2D, 0.1D);
                    }
                }
            }
        }

        // 3. 自身损耗：致敬 EntityVex limitedLifespan 的 STARVE 燃烧（1 点微量自伤）
        entity.attackEntityFrom(DamageSource.STARVE, 1.0F);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 1.6F, 1.15F);
    }
}

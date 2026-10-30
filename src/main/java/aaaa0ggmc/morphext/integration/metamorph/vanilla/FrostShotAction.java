package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 流髑主动技能：冰川凝霜弹 (Frost Shot)。
 * 流髑射出 3 支附带严寒冻结效果的滞缓之箭，命中目标后附加持续 7 秒的缓慢 II 状态，
 * 伴随暴风雪飞雪音画。
 */
public class FrostShotAction implements IAction, ICooldownAction {
    public static final String ID = "frost_shot";
    public static final int COOLDOWN = 80; // 4 秒

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
        entity.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        net.minecraft.util.math.Vec3d look = entity.getLookVec();
        entity.motionX -= look.x * 0.05D;
        entity.motionZ -= look.z * 0.05D;
        entity.velocityChanged = true;

        float baseYaw = entity.rotationYaw;
        float pitch = entity.rotationPitch;
        double eyeY = entity.posY + (double) entity.getEyeHeight() - 0.1D;

        float[] angles = new float[]{-5.0F, 0.0F, 5.0F};

        for (float angleOffset : angles) {
            float yaw = baseYaw + angleOffset;
            double dirX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
            double dirY = -Math.sin(Math.toRadians(pitch));
            double dirZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));

            double sx = entity.posX + dirX * 0.8D;
            double sy = eyeY + dirY * 0.8D;
            double sz = entity.posZ + dirZ * 0.8D;

            EntityTippedArrow arrow = new EntityTippedArrow(world, entity);
            arrow.setPosition(sx, sy, sz);
            arrow.shoot(dirX, dirY, dirZ, 1.9F, 1.0F);
            arrow.setDamage(2.5D);
            // 附加大剂量滞缓药效（缓慢 II，7 秒）
            arrow.addEffect(new PotionEffect(MobEffects.SLOWNESS, 140, 1));
            arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;
            world.spawnEntity(arrow);
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, entity.posX, eyeY, entity.posZ, 25, 0.5, 0.3, 0.5, 0.05);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.2F, 0.85F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6F, 1.8F);
    }
}

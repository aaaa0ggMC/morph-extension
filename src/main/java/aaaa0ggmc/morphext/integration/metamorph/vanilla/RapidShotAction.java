package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 骷髅主动技能：多重齐射 (Rapid Shot)。
 * 骷髅神射手以惊人的手速扇形发射 3 支高精度穿透箭矢，覆盖前方锥形区域，不可被拾取以保证平衡性。
 */
public class RapidShotAction implements IAction, ICooldownAction {
    public static final String ID = "rapid_shot";
    public static final int COOLDOWN = 70; // 3.5 秒

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
        float baseYaw = entity.rotationYaw;
        float pitch = entity.rotationPitch;
        double eyeY = entity.posY + (double) entity.getEyeHeight() - 0.1D;

        float[] angles = new float[]{-5.0F, 0.0F, 5.0F};

        for (float angleOffset : angles) {
            float yaw = baseYaw + angleOffset;
            double dirX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
            double dirY = -Math.sin(Math.toRadians(pitch));
            double dirZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));

            // 安全前向发射点（0.8 格）
            double sx = entity.posX + dirX * 0.8D;
            double sy = eyeY + dirY * 0.8D;
            double sz = entity.posZ + dirZ * 0.8D;

            EntityTippedArrow arrow = new EntityTippedArrow(world, entity);
            arrow.setPosition(sx, sy, sz);
            arrow.shoot(dirX, dirY, dirZ, 2.0F, 1.0F);
            arrow.setDamage(2.5D);
            arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;
            world.spawnEntity(arrow);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.2F, 1.1F);
    }
}

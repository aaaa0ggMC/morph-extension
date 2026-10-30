package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 烈焰人主动技能：烈焰火环爆发 (Inferno Ring)。
 * 核心烈焰骤然向四周 8 个方向喷射出烈焰小火球，形成炽热的环形火焰弹幕，
 * 点燃触碰到的目标并击退靠近的敌人。
 */
public class BlazeFireRingAction implements IAction, ICooldownAction {
    public static final String ID = "blaze_fire_ring";
    public static final int COOLDOWN = 140; // 7 秒

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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;
        double eyeY = y + (double) entity.getEyeHeight() - 0.2D;

        // 向 8 个方向发射环形小火球（半径 1.3 格偏移，防止自身碰撞）
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0D);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);

            double sx = x + dirX * 1.3D;
            double sz = z + dirZ * 1.3D;

            EntitySmallFireball fb = new EntitySmallFireball(world, entity, dirX * 0.4D, 0.0D, dirZ * 0.4D);
            fb.setPosition(sx, eyeY, sz);
            world.spawnEntity(fb);
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.FLAME, x, eyeY, z, 40, 1.0, 0.5, 1.0, 0.1);
            ws.spawnParticle(EnumParticleTypes.LAVA, x, eyeY, z, 15, 0.8, 0.4, 0.8, 0.0);
        }

        world.playSound(null, x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.8F, 0.85F);
        world.playSound(null, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2F, 1.0F);
    }
}

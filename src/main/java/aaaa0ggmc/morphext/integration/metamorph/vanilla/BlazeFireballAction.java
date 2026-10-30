package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 烈焰人主动技能：烈焰飞弹 (Blaze Fireball)。
 * 向视线方向射出一枚小型烈焰火球，引燃目标并造成火焰伤害。
 * 具备主手施法动作、枪口火光与微后坐力反冲。
 */
public class BlazeFireballAction implements IAction, ICooldownAction {
    public static final String ID = "blaze_fireball";
    public static final int COOLDOWN = 50; // 2.5 秒

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
        entity.motionX -= look.x * 0.05D;
        entity.motionZ -= look.z * 0.05D;
        entity.velocityChanged = true;

        double forwardOffset = 1.4D;
        double spawnX = entity.posX + look.x * forwardOffset;
        double spawnY = entity.posY + entity.getEyeHeight() - 0.1D + look.y * forwardOffset;
        double spawnZ = entity.posZ + look.z * forwardOffset;

        EntitySmallFireball fireball = new EntitySmallFireball(world, entity, look.x * 0.15D, look.y * 0.15D, look.z * 0.15D);
        fireball.setPosition(spawnX, spawnY, spawnZ);
        world.spawnEntity(fireball);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.FLAME, spawnX, spawnY, spawnZ, 10, 0.15, 0.15, 0.15, 0.05);
            ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, spawnX, spawnY, spawnZ, 5, 0.1, 0.1, 0.1, 0.02);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.4F, 1.0F);
    }
}

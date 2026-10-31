package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 凋灵骷髅主动技能：投掷凋灵首 (Wither Skull Throw)。
 * 朝准星视线方向射出一枚致命凋灵之首，带有前移 1.5 格安全生成点，彻底规避命中施法者自身。
 */
public class WitherSkullThrowAction implements IAction, ICooldownAction {
    public static final String ID = "wither_skull_throw";
    public static final int COOLDOWN = 240; // 12 秒

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
        entity.motionX -= look.x * 0.08D;
        entity.motionZ -= look.z * 0.08D;
        entity.velocityChanged = true;

        // 安全前移 1.5 格起点
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.5D);

        EntityWitherSkull skull = new EntityWitherSkull(world, entity, look.x * 0.1D, look.y * 0.1D, look.z * 0.1D);
        skull.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        world.spawnEntity(skull);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, spawnPos.x, spawnPos.y, spawnPos.z, 12, 0.2, 0.2, 0.2, 0.05);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, spawnPos.x, spawnPos.y, spawnPos.z, 8, 0.0, 0.0, 0.0, 1.0);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.6F, 1.0F);
    }
}

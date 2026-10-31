package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 恶魂主动技能：巨型恶魂火球 (Ghast Fireball)。
 * 发出标志性恶魂尖啸，从嘴部朝准星射出一枚威力巨大的高爆火球，发射起点安全前移 1.8 格彻底防止自炸。
 */
public class GhastFireballAction implements IAction, ICooldownAction {
    public static final String ID = "ghast_fireball";
    public static final int COOLDOWN = 300; // 15 秒

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

        // 巨炮发射微后坐力
        entity.motionX -= look.x * 0.12D;
        entity.motionZ -= look.z * 0.12D;
        entity.velocityChanged = true;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GHAST_WARN, SoundCategory.PLAYERS, 2.0F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 安全前移 1.8 格，彻底防止与体型庞大的施法者碰撞
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.8D);

        EntityLargeFireball fireball = new EntityLargeFireball(world, entity, look.x * 0.15D, look.y * 0.15D, look.z * 0.15D);
        fireball.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        fireball.explosionPower = 2;
        world.spawnEntity(fireball);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.FLAME, spawnPos.x, spawnPos.y, spawnPos.z, 20, 0.3, 0.3, 0.3, 0.08);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, spawnPos.x, spawnPos.y, spawnPos.z, 12, 0.2, 0.2, 0.2, 0.05);
        }
    }
}

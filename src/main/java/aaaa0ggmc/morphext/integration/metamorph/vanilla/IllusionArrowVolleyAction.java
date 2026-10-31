package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 幻术师主动技能：幻影箭雨齐射 (Illusion Arrow Volley)。
 * 顺应幻术长弓射术体系：
 * 1. 顺应准星朝向扇形散射一波（5 支）附带荧光微粒的发光幻术箭矢 (EntitySpectralArrow)；
 * 2. 命中目标附加高亮发光轮廓与纯正弓箭穿透伤害；
 * 3. 发射起点安全前移 1.2 格，杜绝自身碰撞与误伤。
 */
public class IllusionArrowVolleyAction implements IAction, ICooldownAction {
    public static final String ID = "illusion_arrow_volley";
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
        entity.swingArm(EnumHand.MAIN_HAND);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.5F, 0.9F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 1.1F, 1.4F);

        Vec3d safeSpawn = VanillaActionHelper.getSafeSpawnPos(entity, 1.2D);

        // 散射 5 支发光幻术箭矢（水平偏角 -12° 到 +12°）
        for (int i = 0; i < 5; i++) {
            float yawOffset = (i - 2) * 6.0F;
            float pitchOffset = -1.5F;

            EntitySpectralArrow arrow = new EntitySpectralArrow(world, entity);
            arrow.setPosition(safeSpawn.x, safeSpawn.y, safeSpawn.z);
            arrow.setDamage(3.5D);
            arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;

            arrow.shoot(entity, entity.rotationPitch + pitchOffset, entity.rotationYaw + yawOffset, 0.0F, 2.2F, 1.0F);
            world.spawnEntity(arrow);
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_INSTANT, safeSpawn.x, safeSpawn.y, safeSpawn.z, 15, 0.3, 0.3, 0.3, 0.05);
        }
    }
}

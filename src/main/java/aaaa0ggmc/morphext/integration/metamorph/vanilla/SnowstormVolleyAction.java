package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 雪傀儡主动技能：暴风雪连珠雪球 (Snowstorm Volley)。
 * 极速倾泻连续 5 发高密度雪球形成锥形弹幕风暴，击退并压制前方敌人，
 * 附带暴风雪飞散粒子。
 */
public class SnowstormVolleyAction implements IAction, ICooldownAction {
    public static final String ID = "snowstorm_volley";
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
        double eyeY = entity.posY + (double) entity.getEyeHeight() - 0.1D;

        // 发射 5 发带散布的连珠雪球
        for (int i = 0; i < 5; i++) {
            EntitySnowball snowball = new EntitySnowball(world, entity);
            // 安全前向发射点（0.9 格）
            snowball.setPosition(
                    entity.posX + look.x * 0.9D,
                    eyeY + look.y * 0.9D,
                    entity.posZ + look.z * 0.9D
            );
            snowball.shoot(look.x, look.y, look.z, 1.6F, 9.0F);
            world.spawnEntity(snowball);
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, entity.posX + look.x, eyeY, entity.posZ + look.z,
                    30, 0.4, 0.4, 0.4, 0.1);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SNOWMAN_SHOOT, SoundCategory.PLAYERS, 1.5F, 1.2F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SNOWMAN_SHOOT, SoundCategory.PLAYERS, 1.2F, 0.9F);
    }
}

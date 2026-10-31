package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 兔子主动技能：敏捷飞跃 (Rabbit Hop)。
 * 迅捷向前并高高跃起，可轻松翻越 2 格高障碍与沟壑，落地更加轻灵。
 */
public class RabbitHopAction implements IAction, ICooldownAction {
    public static final String ID = "rabbit_hop";
    public static final int COOLDOWN = 100; // 5 秒

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

        // 强劲高跳冲量 (motionY 0.65D 可越过 2 格高墙) 与向前推力
        entity.motionX += look.x * 0.65D;
        entity.motionY = 0.65D;
        entity.motionZ += look.z * 0.65D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_RABBIT_JUMP, SoundCategory.PLAYERS, 1.6F, 1.2F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.CLOUD, entity.posX, entity.posY + 0.1D, entity.posZ,
                    10, 0.25, 0.1, 0.25, 0.02);
        }
    }
}

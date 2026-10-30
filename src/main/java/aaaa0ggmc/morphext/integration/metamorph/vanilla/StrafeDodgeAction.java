package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 骷髅/流髑主动技能：战术后撤 (Strafe Dodge)。
 * 展现不死射手的战术机动性，迅速向后翻滚拉开距离并获得短暂移速增益。
 */
public class StrafeDodgeAction implements IAction, ICooldownAction {
    public static final String ID = "strafe_dodge";
    public static final int COOLDOWN = 45; // 2.25 秒

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

        // 强力向后跃拉开射击距离
        entity.motionX -= look.x * 0.95D;
        entity.motionY += 0.32D;
        entity.motionZ -= look.z * 0.95D;
        entity.velocityChanged = true;

        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 40, 1, false, false));

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                    entity.posX, entity.posY + 0.2D, entity.posZ,
                    15, 0.4D, 0.2D, 0.4D, 0.05D);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SKELETON_STEP, SoundCategory.PLAYERS, 1.2F, 1.4F);
    }
}

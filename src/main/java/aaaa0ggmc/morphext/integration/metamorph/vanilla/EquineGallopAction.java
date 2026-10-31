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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 马科主动技能：扬蹄疾驰 (Equine Gallop)。
 * <p>
 * 依据 {@code AbstractHorse} 的坐骑疾驰身份（JUMP_STRENGTH + 移速是马科核心属性）——
 * 扬蹄向前猛冲：获得速度 III 与跳跃提升 I 短时爆发，可轻松越障脱战。
 */
public class EquineGallopAction implements IAction, ICooldownAction {
    public static final String ID = "equine_gallop";
    public static final String ALIAS_ID = "morphext:equine_gallop";
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
        entity.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_HORSE_GALLOP, SoundCategory.PLAYERS, 1.4F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_HORSE_ANGRY, SoundCategory.PLAYERS, 1.0F, 1.2F);

        // 扬蹄爆发：速度 III + 跳跃提升 I
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 100, 2, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 100, 0, false, true));

        // 前向小冲量，配合速度爆发形成"起步扬蹄"手感
        net.minecraft.util.math.Vec3d look = entity.getLookVec();
        entity.motionX += look.x * 0.35D;
        entity.motionZ += look.z * 0.35D;
        entity.velocityChanged = true;

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.CLOUD, entity.posX, entity.posY + 0.1D, entity.posZ, 14, 0.4D, 0.15D, 0.4D, 0.03D);
            ws.spawnParticle(EnumParticleTypes.CRIT, entity.posX, entity.posY + 0.4D, entity.posZ, 8, 0.5D, 0.3D, 0.5D, 0.1D);
        }
    }
}

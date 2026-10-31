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
 * 末影螨主动技能：短程钻地跳窜折跃 (Endermite Burrow Leap)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.monster.EntityEndermite} 敏捷窜动与钻缝特性：
 * 1. 【短程贴地跳窜】：朝准星方向进行极速贴地冲刺与敏捷小跃，在复杂的狭窄地形中穿梭自如；
 * 2. 【钻地潜行匿迹】：冲刺爆发时扬起泥石碎屑与异界微粒，使施法者获得 3 秒隐形与速度 II；
 * 3. 【极短冷却体验】：60 Tick (3 秒) 冷却，带来流畅无比的跳窜机动手感。
 */
public class EndermiteBurrowAction implements IAction, ICooldownAction {
    public static final String ID = "endermite_burrow";
    public static final int COOLDOWN = 60; // 3 秒冷却

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

        // 1. 贴地敏捷向前弹射跳窜
        entity.motionX = look.x * 1.65D;
        entity.motionY = Math.max(0.28D, look.y * 1.2D);
        entity.motionZ = look.z * 1.65D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        // 2. 赋予 3 秒隐身与速度 II 潜行突袭增益
        entity.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 60, 0, false, false));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 1, false, false));

        // 3. 步态摩擦声与末影折跃高音调音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERMITE_STEP, SoundCategory.PLAYERS, 1.8F, 1.1F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.2F, 1.8F);

        // 4. 扬起地面泥尘与传送门微粒
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, entity.posX, entity.posY + 0.2D, entity.posZ,
                    20, 0.4D, 0.2D, 0.4D, 0.2D);
            ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, entity.posX, entity.posY + 0.1D, entity.posZ,
                    15, 0.3D, 0.1D, 0.3D, 0.05D);
        }
    }
}

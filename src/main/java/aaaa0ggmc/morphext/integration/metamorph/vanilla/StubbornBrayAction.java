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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 驴/骡主动技能：倔强嘶鸣 (Stubborn Bray)。
 * <p>
 * 依据驴科"倔强护群"的生态位（现实中驴常被用作牲畜护卫，驱赶狼与郊狼）——
 * 一声倔强长嘶震慑周围敌人，令其陷入虚弱 II 与缓慢 I，同时自己稳住阵脚获得
 * 抗性 I；纯威慑不造成伤害。
 */
public class StubbornBrayAction implements IAction, ICooldownAction {
    public static final String ID = "stubborn_bray";
    public static final String ALIAS_ID = "morphext:stubborn_bray";
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
                SoundEvents.ENTITY_DONKEY_ANGRY, SoundCategory.PLAYERS, 1.8F, 0.9F);

        // 震慑圈：虚弱 II + 缓慢 I
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));
        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 1, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 0, false, true));
        }

        // 稳住阵脚：自身抗性 I
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 120, 0, false, true));

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, entity.posX, entity.posY + entity.height + 0.2D, entity.posZ, 8, 0.6D, 0.3D, 0.6D, 0.02D);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, entity.posX, entity.posY + 0.3D, entity.posZ, 12, 1.2D, 0.3D, 1.2D, 0.01D);
        }
    }
}

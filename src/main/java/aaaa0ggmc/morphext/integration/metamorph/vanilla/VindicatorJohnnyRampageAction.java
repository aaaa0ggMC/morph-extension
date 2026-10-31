package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 卫道士"强尼"狂暴 (Vindicator "Johnny" Rampage)。
 * <p>
 * 依据 {@code EntityVindicator} 的 Johnny 彩蛋（命名 Johnny 后 AIJohnnyAttack / JOHNNY_SELECTOR
 * 攻击一切可攻击实体）：
 * 1. 【狂暴宣泄】自身获得力量 II 与急迫 II（10 秒），不分敌我地倾泻怒火；
 * 2. 【红名威慑】周围 10 格内所有生物（不分敌我）被标记发光 10 秒——还原 JOHNNY_SELECTOR
 *    "万物皆目标"的无差别威慑气场。
 */
public class VindicatorJohnnyRampageAction implements IAction, ICooldownAction {
    public static final String ID = "vindicator_johnny_rampage";
    public static final String ALIAS_ID = "morphext:vindicator_johnny_rampage";
    public static final int COOLDOWN = 400; // 20 秒

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
                SoundEvents.VINDICATION_ILLAGER_AMBIENT, SoundCategory.PLAYERS, 1.6F, 0.7F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 1.0F, 0.8F);

        // 1. 狂暴宣泄：力量 II + 急迫 II（10 秒）
        entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 200, 1, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.HASTE, 200, 1, false, true));

        // 2. 无差别红名威慑：周围一切生物标记发光（JOHNNY_SELECTOR 万物皆目标）
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(10.0D, 6.0D, 10.0D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());
        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 200, 0, false, true));
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    entity.posX, entity.posY + entity.height + 0.2D, entity.posZ, 6, 0.3D, 0.1D, 0.3D, 0.0D);
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                ws.spawnParticle(EnumParticleTypes.CRIT,
                        entity.posX + Math.cos(rad) * 1.5D, entity.posY + 1.0D, entity.posZ + Math.sin(rad) * 1.5D,
                        4, 0.1D, 0.3D, 0.1D, 0.15D);
            }
        }
    }
}

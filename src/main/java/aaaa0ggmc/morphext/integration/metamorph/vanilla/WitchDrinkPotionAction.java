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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 女巫主动技能：秘药自愈 (Witch Drink Potion)。
 * 顿饮随身秘制药水，播放饮药音效，回复 6 点生命值并获得 15 秒抗火效果。
 */
public class WitchDrinkPotionAction implements IAction, ICooldownAction {
    public static final String ID = "witch_drink_potion";
    public static final int COOLDOWN = 500; // 25 秒

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

        // 回复 6 点生命值
        entity.heal(6.0F);

        // 获得 15 秒 (300 ticks) 抗火效果
        entity.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 300, 0, false, false));

        // 饮药与回血音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.4F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double x = entity.posX;
            double y = entity.posY + entity.height * 0.5D;
            double z = entity.posZ;
            ws.spawnParticle(EnumParticleTypes.HEART, x, y + 0.4D, z, 7, 0.4, 0.3, 0.4, 0.05);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, 15, 0.4, 0.4, 0.4, 0.05);
        }
    }
}

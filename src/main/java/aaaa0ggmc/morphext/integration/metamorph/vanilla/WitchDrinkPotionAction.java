package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.material.Material;
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
 * 女巫主动技能：动态智能饮药 (Witch Drink Potion)。
 * 顺应女巫原版 AI 源码的高级生存与战斗博弈逻辑：
 * 1. 溺水/水下感知：身处水中且无水下呼吸时，顿饮水肺药水（获得 30 秒水下呼吸与夜视，回满氧气）；
 * 2. 烈焰/熔岩感知：着火或身陷岩浆且无抗火时，顿饮抗火药水（立刻扑灭周身火焰并获得 30 秒抗火）；
 * 3. 濒死/低血自愈：生命值低于 11 点或低于 60% 时，顿饮瞬间治疗药水（回复 8 点生命并附加 5 秒生命恢复）；
 * 4. 追击/疾行机动：潜行或追击较远目标时，顿饮迅捷药水（获得 15 秒速度 II 与跳跃提升）；
 * 5. 健全充能：状态良好时饮用强韧秘药，获得 30 秒伤害吸收 II（额外 4 颗金心）与 10 秒力量 I。
 */
public class WitchDrinkPotionAction implements IAction, ICooldownAction {
    public static final String ID = "witch_drink_potion";
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
        entity.swingArm(EnumHand.MAIN_HAND);

        // 原版女巫智能饮药逻辑判定
        if (entity.isInsideOfMaterial(Material.WATER) && !entity.isPotionActive(MobEffects.WATER_BREATHING)) {
            // 1. 水下环境饮用：水下呼吸 + 夜视 (30 秒)
            entity.setAir(300);
            entity.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING, 600, 0, false, false));
            entity.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 600, 0, false, false));
        } else if ((entity.isBurning() || entity.isInLava()) && !entity.isPotionActive(MobEffects.FIRE_RESISTANCE)) {
            // 2. 燃烧/熔岩中饮用：扑灭火焰 + 抗火 (30 秒)
            entity.extinguish();
            entity.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 600, 0, false, false));
        } else if (entity.getHealth() < 11.0F || entity.getHealth() < entity.getMaxHealth() * 0.6F) {
            // 3. 低生命自愈饮用：回复 8 点生命 + 生命恢复 (5 秒)
            entity.heal(8.0F);
            entity.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 0, false, false));
        } else if (entity.isSneaking() || (VanillaActionHelper.findTarget(entity, 24.0D, 0.4D) != null && !entity.isPotionActive(MobEffects.SPEED))) {
            // 4. 追击/疾行机动饮用：速度 II + 跳跃提升 (15 秒)
            entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 300, 1, false, false));
            entity.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 300, 0, false, false));
        } else {
            // 5. 健全充能增益饮用：伤害吸收 II + 力量 I
            entity.heal(4.0F);
            entity.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 600, 1, false, false));
            entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 200, 0, false, false));
        }

        // 饮药音效与原版女巫咕咚音画
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.4F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.PLAYERS, 1.3F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double x = entity.posX;
            double y = entity.posY + entity.height * 0.5D;
            double z = entity.posZ;
            ws.spawnParticle(EnumParticleTypes.HEART, x, y + 0.4D, z, 8, 0.4, 0.3, 0.4, 0.05);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, 16, 0.4, 0.4, 0.4, 0.05);
        }
    }
}

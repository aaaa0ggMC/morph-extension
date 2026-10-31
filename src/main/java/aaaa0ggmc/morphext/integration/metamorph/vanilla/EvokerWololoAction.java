package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 唤魔者彩蛋法术：转色法咒 (Wololo!)。
 * 顺应灾厄魔法源码体系：
 * 1. 施法咒文音画驱动：高举双手摇曳并飘散橙黄色魔纹粒子 (SpellType.WOLOLO) 与经典的 Wololo 施法长吟；
 * 2. 羊毛瞬变：感应周围 16 格范围内的蓝色绵羊 (EnumDyeColor.BLUE)，瞬变其羊毛为纯正艳红色 (EnumDyeColor.RED)；
 * 3. 自由射击与响应性：即使周围无羊亦可随心念咒施展动作与音画，绝不因缺少目标阻断交互。
 */
public class EvokerWololoAction implements IAction, ICooldownAction {
    public static final String ID = "evoker_wololo";
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
        entity.swingArm(EnumHand.MAIN_HAND);

        // 施法咒文音画驱动 (SpellType.WOLOLO + 橙黄魔纹粒子 + 经典 Wololo 施法准备长吟)
        VanillaActionHelper.triggerSpellEffects(world, entity, morph,
                EntitySpellcasterIllager.SpellType.WOLOLO,
                SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO,
                SoundEvents.EVOCATION_ILLAGER_CAST_SPELL);

        // 检索周围 16 格范围内的所有存活蓝色绵羊
        double range = 16.0D;
        List<EntitySheep> sheepList = world.getEntitiesWithinAABB(EntitySheep.class,
                entity.getEntityBoundingBox().grow(range),
                sheep -> sheep != null && sheep.isEntityAlive() && sheep.getFleeceColor() == EnumDyeColor.BLUE);

        if (!sheepList.isEmpty()) {
            for (EntitySheep sheep : sheepList) {
                // 瞬间转变为红色羊毛
                sheep.setFleeceColor(EnumDyeColor.RED);

                // 专属转色魔纹与欢庆音符粒子
                if (world instanceof WorldServer) {
                    WorldServer ws = (WorldServer) world;
                    double sx = sheep.posX;
                    double sy = sheep.posY + sheep.height * 0.5D;
                    double sz = sheep.posZ;
                    ws.spawnParticle(EnumParticleTypes.SPELL_INSTANT, sx, sy, sz, 20, 0.4, 0.4, 0.4, 0.1);
                    ws.spawnParticle(EnumParticleTypes.NOTE, sx, sy + 0.6D, sz, 6, 0.3, 0.3, 0.3, 0.0);
                }

                world.playSound(null, sheep.posX, sheep.posY, sheep.posZ,
                        SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.NEUTRAL, 1.2F, 1.1F);
            }
        }
    }
}

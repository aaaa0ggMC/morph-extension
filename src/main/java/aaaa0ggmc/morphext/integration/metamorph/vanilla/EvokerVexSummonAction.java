package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * 唤魔者主动技能：召唤恼鬼群 (Evoker Vex Swarm)。
 * 顺应灾厄魔法源码体系：
 * 1. 施法咒文音画驱动：高举双手摇曳并飘散银白烟雾粒子 (SpellType.SUMMON_VEX) 与长吟准备音效；
 * 2. 召唤 2-3 只忠诚恼鬼 (EntityVex)，持续效忠助战 30 秒；
 * 3. 恼鬼手持铁剑，绑定宿主位置并在准星判定的攻击目标上迅速集火。
 */
public class EvokerVexSummonAction implements IAction, ICooldownAction {
    public static final String ID = "evoker_vex_summon";
    public static final int COOLDOWN = 600; // 30 秒

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

        // 施法咒文音画驱动 (SpellType.SUMMON_VEX + 白烟魔纹粒子 + 召唤准备长吟)
        VanillaActionHelper.triggerSpellEffects(world, entity, morph,
                EntitySpellcasterIllager.SpellType.SUMMON_VEX,
                SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON,
                SoundEvents.EVOCATION_ILLAGER_CAST_SPELL);

        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.35D);

        // 召唤 2-3 只忠诚恼鬼助战
        int count = 2 + world.rand.nextInt(2);
        for (int i = 0; i < count; i++) {
            float angle = (float) Math.toRadians(entity.rotationYaw) + (i - (count - 1) * 0.5F) * 0.8F;
            double sx = entity.posX + MathHelper.cos(angle) * 1.35D;
            double sy = entity.posY + 1.2D + world.rand.nextDouble() * 0.4D;
            double sz = entity.posZ + MathHelper.sin(angle) * 1.35D;

            EntityVex vex = new EntityVex(world);
            vex.setLocationAndAngles(sx, sy, sz, entity.rotationYaw, 0.0F);
            vex.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(entity)), null);
            vex.setBoundOrigin(new BlockPos(entity));
            vex.setLimitedLife(20 * 30); // 30 秒寿命限制
            vex.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            vex.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);

            if (entity instanceof EntityLiving) {
                vex.setOwner((EntityLiving) entity);
            }

            if (target != null) {
                vex.setAttackTarget(target);
            }

            world.spawnEntity(vex);
        }
    }
}

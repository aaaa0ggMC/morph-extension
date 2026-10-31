package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 唤魔者主动技能：召唤恼鬼 (Evoker Vex Summon)。
 * 吟唱高阶唤魔咒文，召唤 2 只身披铁剑短暂效忠宿主的幽魂恼鬼 (EntityVex)，持续 30 秒。
 */
public class EvokerVexSummonAction implements IAction, ICooldownAction {
    public static final String ID = "evoker_vex_summon";
    public static final int COOLDOWN = 900; // 45 秒

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
                SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 1.8F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.5F, 1.0F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                    30, 0.6, 0.6, 0.6, 1.0);
        }

        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.3D);

        for (int i = -1; i <= 1; i += 2) {
            EntityVex vex = new EntityVex(world);
            double sx = entity.posX + (double) i * 1.2D;
            double sy = entity.posY + 1.2D;
            double sz = entity.posZ + (double) i * 0.4D;
            vex.setLocationAndAngles(sx, sy, sz, entity.rotationYaw, 0.0F);
            vex.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(entity)), null);
            vex.setBoundOrigin(new BlockPos(entity));
            vex.setLimitedLife(20 * 30); // 30 秒寿命
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

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
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
 * 尸壳沙暴 (Husk Sand Storm)。
 * <p>
 * 依据 {@code EntityHusk} 近战命中附加 HUNGER 的"饥饿之触"（EntityHusk.java:64-72）——
 * 这是它的 AOE 沙暴版：扬起遮天蔽日的风沙，令周围 8 格内的敌人陷入
 * 严重饥饿 II 与迟缓 II，对齐"沙丘暴君"意象。
 */
public class HuskSandStormAction implements IAction, ICooldownAction {
    public static final String ID = "husk_sand_storm";
    public static final String ALIAS_ID = "morphext:husk_sand_storm";
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

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_HUSK_AMBIENT, SoundCategory.PLAYERS, 1.5F, 0.8F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.6F, 0.7F);

        // 沙暴圈：缓慢 II + 严重饥饿 II
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));
        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 240, 1, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 160, 1, false, true));
        }

        // 风沙视觉：沙粒环 + 烟尘
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int sandState = Block.getStateId(Blocks.SAND.getDefaultState());
            for (int angle = 0; angle < 360; angle += 20) {
                double rad = Math.toRadians(angle);
                double px = entity.posX + Math.cos(rad) * 3.0D;
                double pz = entity.posZ + Math.sin(rad) * 3.0D;
                ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, px, entity.posY + 0.4D, pz, 6, 0.4D, 0.5D, 0.4D, 0.05D, sandState);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, px, entity.posY + 0.8D, pz, 2, 0.3D, 0.6D, 0.3D, 0.02D);
            }
        }
    }
}

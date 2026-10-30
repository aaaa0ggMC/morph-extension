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
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 铁傀儡主动技能：大地震击 (Ground Slam)。
 * 凝聚沉重的精铁之力怒击大地，震碎地面方块，对周围 6 格内的敌人造成大量物理伤害并将他们抛至空中击飞，
 * 附带短暂硬直减速。
 */
public class GroundSlamAction implements IAction, ICooldownAction {
    public static final String ID = "ground_slam";
    public static final int COOLDOWN = 160; // 8 秒

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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 巨力铁臂重击与铁砧重坠音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.8F, 0.8F);
        world.playSound(null, x, y, z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.9F, 0.6F);

        // 碎石飞溅与大范围横扫粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int dirtState = Block.getStateId(Blocks.DIRT.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y + 0.1, z, 70, 2.0, 0.2, 2.0, 0.15, dirtState);
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, x, y + 0.3, z, 8, 1.8, 0.2, 1.8, 0.0);
        }

        double radius = 6.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());

        for (EntityLivingBase target : targets) {
            // 铁傀儡巨力击打伤害
            target.attackEntityFrom(DamageSource.causeMobDamage(entity), 11.0F);

            // 铁傀儡标志性的大力抛飞机制（垂直腾空 + 沿中心向外扩散）
            double dx = target.posX - x;
            double dz = target.posZ - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double normX = dist > 0.01 ? (dx / dist) * 0.7D : 0.0D;
            double normZ = dist > 0.01 ? (dz / dist) * 0.7D : 0.0D;

            target.motionX += normX;
            target.motionY += 0.62D;
            target.motionZ += normZ;
            target.velocityChanged = true;

            // 落地震颤缓慢
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1, false, true));
        }
    }
}

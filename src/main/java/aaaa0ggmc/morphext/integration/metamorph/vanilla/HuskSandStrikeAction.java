package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 尸壳主动技能：狂沙撕咬 (Husk Sand Strike)。
 * 挟裹漫天风沙向前狂暴撕咬身前 3.5 格范围内的敌人，附加 12 秒严重饥饿 II 与 4 秒迟缓减速。
 */
public class HuskSandStrikeAction implements IAction, ICooldownAction {
    public static final String ID = "husk_sand_strike";
    public static final int COOLDOWN = 120; // 6 秒

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

        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX += look.x * 0.35D;
        entity.motionZ += look.z * 0.35D;
        entity.velocityChanged = true;

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.6D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.6D;
            int sandState = Block.getStateId(Blocks.SAND.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, fx, fy, fz, 30, 0.4, 0.3, 0.4, 0.1, sandState);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, fx, fy, fz, 10, 0.3, 0.2, 0.3, 0.05);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 12, 0.3, 0.3, 0.3, 0.1);
        }

        double reach = 3.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.5D, reach);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hitAny = false;
        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                target.attackEntityFrom(source, 7.0F);
                // 严重饥饿 II (amplifier 1, 12 秒 = 240 ticks) 与迟缓 I (4 秒 = 80 ticks)
                target.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 240, 1, false, true));
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 0, false, true));

                target.motionX += look.x * 0.40D;
                target.motionY += 0.20D;
                target.motionZ += look.z * 0.40D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 8, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_HUSK_AMBIENT, SoundCategory.PLAYERS, 1.5F, 0.9F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.4F, 0.8F);
        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.3F, 0.9F);
        }
    }
}

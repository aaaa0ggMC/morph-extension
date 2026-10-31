package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 蝙蝠主动技能：天花板倒悬栖息 (Bat Roost / Hanging)。
 * <p>
 * 严谨还原 {@link net.minecraft.entity.passive.EntityBat#setIsBatHanging(boolean)} 姿态：
 * 1. 天花板探测：向上探测 2.5 格内的实体方块；
 * 2. 倒悬姿态切换：若探测到天花板，自动吸附至天花板下方，折叠双翼进入倒悬休眠姿态；
 * 3. 隐蔽栖息休养：倒悬期间消除动量，获得伪装隐形与快速生命恢复 (Regeneration II)；
 * 4. 离地起飞：若已处于倒悬状态或再次施法，振翅脱离天花板腾空起飞，播放 {@link SoundEvents#ENTITY_BAT_TAKEOFF}；
 * 5. 若无天花板：进行一次灵动的扑翼滞空小跃升。
 */
public class BatRoostAction implements IAction, ICooldownAction {
    public static final String ID = "bat_roost";
    public static final int COOLDOWN = 60; // 3 秒冷却

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
        EntityBat batEntity = null;
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityBat) {
                batEntity = (EntityBat) inner;
            }
        }

        // 若当前已经是倒挂状态，则起飞脱离
        if (batEntity != null && batEntity.getIsBatHanging()) {
            releaseHanging(entity, batEntity, world);
            return;
        }

        // 向上探查可供倒挂的天花板
        BlockPos ceilingPos = null;
        for (double dy = entity.height + 0.1D; dy <= 2.5D; dy += 0.4D) {
            BlockPos pos = new BlockPos(entity.posX, entity.posY + dy, entity.posZ);
            IBlockState state = world.getBlockState(pos);
            if (state.getMaterial().isSolid() || state.isFullCube()) {
                ceilingPos = pos;
                break;
            }
        }

        if (ceilingPos != null) {
            // 吸附到天花板方块正下方
            double targetY = ceilingPos.getY() - entity.height;
            entity.setPositionAndUpdate(entity.posX, targetY, entity.posZ);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
            entity.velocityChanged = true;
            entity.fallDistance = 0.0F;

            if (batEntity != null) {
                batEntity.setIsBatHanging(true);
            }

            // 倒悬休养特效：隐匿与快速回血
            entity.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 120, 1, false, false));
            entity.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 120, 0, false, false));

            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_BAT_LOOP, SoundCategory.PLAYERS, 1.2F, 1.6F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_MOB_AMBIENT,
                        entity.posX, entity.posY + 0.3D, entity.posZ, 8, 0.2, 0.2, 0.2, 0.0);
            }

            // 持续检查：若天花板消失或玩家下落，则解除倒挂
            final EntityBat finalBat = batEntity;
            final BlockPos finalCeil = ceilingPos;
            ServerActionScheduler.scheduleTicker(120, remaining -> {
                if (entity.isDead || entity.world == null || finalBat == null) {
                    return;
                }
                if (!finalBat.getIsBatHanging()) {
                    return;
                }
                // 检查方块是否仍然存在
                IBlockState currState = entity.world.getBlockState(finalCeil);
                if (!currState.getMaterial().isSolid() && !currState.isFullCube()) {
                    releaseHanging(entity, finalBat, entity.world);
                }
            }, () -> {
                if (finalBat != null && finalBat.getIsBatHanging()) {
                    releaseHanging(entity, finalBat, entity.world);
                }
            });
        } else {
            // 无天花板时轻盈振翅上跃
            entity.motionY = Math.max(entity.motionY + 0.45D, 0.5D);
            entity.velocityChanged = true;
            entity.fallDistance = 0.0F;

            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.0F, 1.8F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.CLOUD,
                        entity.posX, entity.posY + 0.1D, entity.posZ, 5, 0.1, 0.05, 0.1, 0.01);
            }
        }
    }

    private static void releaseHanging(EntityLivingBase entity, EntityBat bat, World world) {
        if (bat != null) {
            bat.setIsBatHanging(false);
        }
        entity.motionY = 0.2D;
        entity.velocityChanged = true;
        entity.removePotionEffect(MobEffects.INVISIBILITY);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.2F, 1.6F);
    }
}

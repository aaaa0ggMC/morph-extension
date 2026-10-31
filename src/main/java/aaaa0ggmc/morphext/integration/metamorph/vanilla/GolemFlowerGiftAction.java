package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 铁傀儡专属动作：纯洁红花赠礼 (Golem Flower Gift)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntityIronGolem} 经典送花机制：
 * 1. 【原版送花身姿】：调用底层 {@link EntityIronGolem#setHoldingRose(boolean)}，让铁傀儡右臂平举捧出红罂粟花；
 * 2. 【准星焦点祝福】：对视线准星所指村民触发欢悦心动状态（播放赞许音效并激发繁衍求爱意愿）；
 * 3. 【群体守护祝福】：为自身及周围 12 格内所有友方目标（村民、傀儡、驯服宠物与同阵营玩家）赋予 15 秒生命恢复 II 与抗性提升 I；
 * 4. 【爱心粒子飘散】：满溢浪漫守护温情的爱心与村民欢悦微粒。
 */
public class GolemFlowerGiftAction implements IAction, ICooldownAction {
    public static final String ID = "golem_flower_gift";
    public static final String ALIAS_ID = "morphext:golem_flower_gift";
    public static final int COOLDOWN = 600; // 30 秒冷却 (600 ticks)

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        World world = player.world;

        // 1. 摆动手臂
        player.swingArm(EnumHand.MAIN_HAND);

        // 2. 驱动变身实体手持红花动作
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity();
            if (dummy instanceof EntityIronGolem) {
                EntityIronGolem golem = (EntityIronGolem) dummy;
                golem.setHoldingRose(true);
                // 5 秒后收回红花
                ServerActionScheduler.schedule(100, () -> {
                    if (golem.isEntityAlive()) {
                        golem.setHoldingRose(false);
                    }
                });
            }
        }

        // 3. 视线准星检测目标村民
        EntityLivingBase target = VanillaActionHelper.findTarget(player, 16.0D, 0.4D);
        if (target instanceof EntityVillager) {
            EntityVillager villager = (EntityVillager) target;
            villager.setIsWillingToMate(true);
            world.setEntityState(villager, (byte) 12); // 爱心粒子
            world.playSound(null, villager.posX, villager.posY, villager.posZ,
                    SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.2F, 1.0F);
        }

        // 4. 周围 12 格友军群体祝福
        AxisAlignedBB allyBox = player.getEntityBoundingBox().grow(12.0D, 6.0D, 12.0D);
        List<EntityLivingBase> allies = world.getEntitiesWithinAABB(EntityLivingBase.class, allyBox, e -> {
            if (e == null || !e.isEntityAlive()) {
                return false;
            }
            if (e == player) {
                return true;
            }
            if (e instanceof EntityVillager || e instanceof EntityIronGolem || e instanceof EntitySnowman) {
                return true;
            }
            if (e instanceof EntityPlayer) {
                return !e.isOnSameTeam(player) || true; // 友方玩家
            }
            if (e instanceof EntityTameable && ((EntityTameable) e).isTamed()) {
                return true;
            }
            return false;
        });

        int blessedCount = 0;
        for (EntityLivingBase ally : allies) {
            // 赋予 15 秒生命恢复 II (amplifier 1) 与抗性提升 I (amplifier 0)
            ally.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 300, 1, false, true));
            ally.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 300, 0, false, true));

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.HEART,
                        ally.posX, ally.posY + ally.height + 0.3D, ally.posZ,
                        8, 0.4D, 0.3D, 0.4D, 0.05D);
            }
            blessedCount++;
        }

        // 5. 音效与粒子渲染
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9F, 1.4F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.2F, 0.8F);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.HEART,
                    player.posX, player.posY + 1.6D, player.posZ,
                    20, 0.6D, 0.4D, 0.6D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                    player.posX, player.posY + 1.2D, player.posZ,
                    25, 0.8D, 0.5D, 0.8D, 0.08D);
        }

        player.sendStatusMessage(new TextComponentTranslation(
                "morphext.action.golem_flower_gift.success", blessedCount), true);
    }
}

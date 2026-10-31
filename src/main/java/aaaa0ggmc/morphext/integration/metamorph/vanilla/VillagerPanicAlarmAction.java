package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
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
 * 村民专属动作：惊慌呼救与警戒警报 (Villager Panic Alarm)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.passive.EntityVillager} 与村庄警戒生态防御机制：
 * 1. 【惊恐尖叫警报】：发出极高穿透力的惊恐嘶鸣与求救尖啸，触发村民惊怒状态；
 * 2. 【铁卫疾驰护驾】：唤醒周围 24 格内所有铁傀儡 (EntityIronGolem) 以 1.4 倍极速向玩家狂奔集结，并优先锁定威胁玩家的敌对目标；
 * 3. 【敌群受震迟缓】：震慑周围 20 格内所有敌对怪物，使其陷入短暂迟缓 II (5秒) 与虚弱 (5秒)；
 * 4. 【惊慌逃逸身法】：赋予玩家 5 秒极速奔逃 (速度 II 与跳跃提升 I) 逃生脱险。
 */
public class VillagerPanicAlarmAction implements IAction, ICooldownAction {
    public static final String ID = "villager_panic_alarm";
    public static final String ALIAS_ID = "morphext:villager_panic_alarm";
    public static final int COOLDOWN = 400; // 20 秒冷却 (400 ticks)

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

        // 1. 挥动手臂与驱动变身实体姿态
        player.swingArm(EnumHand.MAIN_HAND);
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity();
            if (dummy instanceof EntityVillager) {
                // 状态 13 触发村民气怒/惊慌粒子
                world.setEntityState(dummy, (byte) 13);
            }
        }

        // 2. 惊恐尖啸声与求救警报
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.5F, 1.35F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.PLAYERS, 1.3F, 1.1F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.2F, 0.85F);

        // 3. 惊慌微粒爆发
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    player.posX, player.posY + 1.8D, player.posZ,
                    18, 0.6D, 0.4D, 0.6D, 0.1D);
            ws.spawnParticle(EnumParticleTypes.NOTE,
                    player.posX, player.posY + 1.5D, player.posZ,
                    15, 0.8D, 0.5D, 0.8D, 0.1D);
            ws.spawnParticle(EnumParticleTypes.CRIT,
                    player.posX, player.posY + 1.0D, player.posZ,
                    20, 0.5D, 0.5D, 0.5D, 0.15D);
        }

        // 4. 铁傀儡 24 格极速狂奔驰援护驾
        AxisAlignedBB golemBox = player.getEntityBoundingBox().grow(24.0D, 12.0D, 24.0D);
        List<EntityIronGolem> golems = world.getEntitiesWithinAABB(EntityIronGolem.class, golemBox,
                g -> g != null && g.isEntityAlive());

        // 寻找距离玩家最近的敌对生物作为铁傀儡的优先清除目标
        AxisAlignedBB hostileBox = player.getEntityBoundingBox().grow(16.0D, 8.0D, 16.0D);
        List<EntityLivingBase> hostiles = world.getEntitiesWithinAABB(EntityLivingBase.class, hostileBox,
                m -> m != player && m.isEntityAlive() && (m instanceof IMob || m instanceof EntityMob));

        EntityLivingBase priorityTarget = hostiles.isEmpty() ? null : hostiles.get(0);

        int alertedGolems = 0;
        for (EntityIronGolem golem : golems) {
            golem.getNavigator().tryMoveToEntityLiving(player, 1.45D);

            if (priorityTarget != null) {
                golem.setAttackTarget(priorityTarget);
                golem.setRevengeTarget(priorityTarget);
            }

            world.playSound(null, golem.posX, golem.posY, golem.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.NEUTRAL, 1.0F, 1.2F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                        golem.posX, golem.posY + golem.height + 0.3D, golem.posZ,
                        8, 0.3D, 0.2D, 0.3D, 0.05D);
            }
            alertedGolems++;
        }

        // 5. 周围 20 格敌对生物受到惊叫震慑短暂迟缓与虚弱
        AxisAlignedBB enemyBox = player.getEntityBoundingBox().grow(20.0D, 10.0D, 20.0D);
        List<EntityLivingBase> enemies = world.getEntitiesWithinAABB(EntityLivingBase.class, enemyBox,
                e -> e != player && e.isEntityAlive() && (e instanceof IMob || e instanceof EntityMob));

        for (EntityLivingBase enemy : enemies) {
            enemy.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1, false, true));
            enemy.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 0, false, true));

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                        enemy.posX, enemy.posY + enemy.height * 0.5D, enemy.posZ,
                        8, 0.3D, 0.3D, 0.3D, 0.05D);
            }
        }

        // 6. 赋予玩家惊恐逃逸疾跑 (速度 II + 跳跃提升 I 持续 5 秒)
        player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 100, 1, false, false));
        player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 100, 0, false, false));

        player.sendStatusMessage(new TextComponentTranslation(
                "morphext.action.villager_panic_alarm.triggered", alertedGolems, enemies.size()), true);
    }
}

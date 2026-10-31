package aaaa0ggmc.morphext.ability.morph;

import aaaa0ggmc.morphext.ability.MobAiToggleAbility;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.faction.FactionGraphEngine;
import aaaa0ggmc.morphext.faction.FactionRelation;
import com.google.gson.JsonObject;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 变身实体自主 AI 运行时沙盒引擎 (Morph AI Sandbox Engine)。
 *
 * 核心安全保障：
 * 1. 宿主与同盟绝对豁免 (Host & Ally Immunity)：
 *    彻底拦截 Dummy 实体将宿主玩家或友方同盟设为攻击目标；
 * 2. 危险自杀与自爆任务剥离 (Task Sanitizer)：
 *    剥离 Creeper 自爆膨胀、Stoneling 受惊化云自毁、末影人偷方块、修格斯筑塔与恶魂乱飞 AI；
 * 3. 伴战与协同警戒 (Cooperative Combat Assistance)：
 *    当 AI 开启时，Dummy 实体将优先协同攻击与宿主交战的真正敌对生物 (ENEMY)，实现生动的伴战功能；
 * 4. 彻底防止位置撕裂：位移锁死在宿主坐标，仅保留头部观察与动作触发。
 */
public final class MorphAiEngine {
    private static final Logger LOGGER = LogManager.getLogger("morphext-ai-engine");
    private static final Set<Integer> SANITIZED_ENTITIES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MorphAiEngine() {}

    /**
     * 判断某个实体是否属于指定宿主玩家的变身 Dummy 实体或其衍生发射物。
     * 用于确保调试 AI 状态下生物的任何攻击、弹射物与爆炸伤害绝对不击中玩家自己。
     */
    public static boolean isHostDummyEntity(EntityPlayer player, net.minecraft.entity.Entity entity) {
        if (player == null || entity == null) return false;
        if (player == entity) return false;
        try {
            IMorphing morphing = Morphing.get(player);
            if (morphing != null) {
                AbstractMorph currentMorph = morphing.getCurrentMorph();
                if (currentMorph instanceof EntityMorph) {
                    EntityLivingBase dummy = ((EntityMorph) currentMorph).getEntity(player.world);
                    if (dummy != null) {
                        if (entity == dummy) return true;
                        if (entity instanceof net.minecraft.entity.projectile.EntityArrow) {
                            if (((net.minecraft.entity.projectile.EntityArrow) entity).shootingEntity == dummy) return true;
                        }
                        if (entity instanceof net.minecraft.entity.projectile.EntityFireball) {
                            if (((net.minecraft.entity.projectile.EntityFireball) entity).shootingEntity == dummy) return true;
                        }
                        if (entity instanceof net.minecraft.entity.projectile.EntityThrowable) {
                            if (((net.minecraft.entity.projectile.EntityThrowable) entity).getThrower() == dummy) return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 判断某个玩家当前是否开启了 AI [ON]。
     */
    public static boolean isAiEnabled(EntityPlayer player) {
        if (player == null || player.world.isRemote) return false;
        if (player instanceof EntityPlayerMP) {
            JsonObject state = PlayerAbilityStore.get((EntityPlayerMP) player).getState(MobAiToggleAbility.ID);
            return state.has("enabled") && state.get("enabled").isJsonPrimitive() && state.get("enabled").getAsBoolean();
        }
        return false;
    }

    /**
     * 服务端每 tick 驱动与安全守护。
     */
    public static void tick(EntityPlayer player) {
        if (player == null || player.world.isRemote || player.isDead) return;

        IMorphing morphing = Morphing.get(player);
        if (morphing == null) return;

        AbstractMorph currentMorph = morphing.getCurrentMorph();
        if (!(currentMorph instanceof EntityMorph)) {
            return;
        }

        EntityMorph entityMorph = (EntityMorph) currentMorph;
        EntityLivingBase dummyBase = entityMorph.getEntity(player.world);
        if (!(dummyBase instanceof EntityLiving)) {
            return;
        }

        EntityLiving dummy = (EntityLiving) dummyBase;
        boolean aiEnabled = isAiEnabled(player);

        // 苦力怕变身实例状态监控与自毁处理（支持 AI 自爆与主动自爆，杜绝卡模型）
        if (dummy instanceof EntityCreeper) {
            handleCreeperDummy(player, (EntityCreeper) dummy, aiEnabled);
        }

        if (!aiEnabled) {
            // AI [OFF]: 确保回归完全静默的纯手动状态
            if (!dummy.isAIDisabled()) {
                dummy.setNoAI(true);
            }
            if (dummy.getAttackTarget() != null) {
                dummy.setAttackTarget(null);
            }
            if (dummy.getRevengeTarget() != null) {
                dummy.setRevengeTarget(null);
            }
            return;
        }

        // ==========================================
        // 以下为 AI [ON] 开启状态下的沙盒安全执行逻辑
        // ==========================================

        // 1. 激活 AI 并设置防自然刷怪清除 (Persistence)
        if (dummy.isAIDisabled()) {
            dummy.setNoAI(false);
        }
        dummy.enablePersistence();

        // 保持 Dummy 坐标与朝向同步对齐宿主玩家
        dummy.posX = player.posX;
        dummy.posY = player.posY;
        dummy.posZ = player.posZ;
        dummy.rotationYaw = player.rotationYaw;
        dummy.rotationPitch = player.rotationPitch;

        // 2. 剥离针对玩家的索敌任务（防止 AI 自发把玩家列为攻击目标）
        int entityId = dummy.getEntityId();
        if (!SANITIZED_ENTITIES.contains(entityId)) {
            sanitizeDummyAI(dummy);
            SANITIZED_ENTITIES.add(entityId);
        }

        // 3. 宿主与同盟绝对免疫检查（绝不以宿主玩家为攻击目标）
        EntityLivingBase currentTarget = dummy.getAttackTarget();
        if (currentTarget != null) {
            if (currentTarget == player || currentTarget.isDead) {
                dummy.setAttackTarget(null);
                dummy.setRevengeTarget(null);
            } else {
                // 阵营图引擎判定
                FactionRelation rel = FactionGraphEngine.getInstance().getRelation(dummy, currentTarget);
                if (rel.isPeaceful()) {
                    // 如果被锁定的目标也是盟友/同族，清除仇恨
                    dummy.setAttackTarget(null);
                    dummy.setRevengeTarget(null);
                }
            }
        }
        if (dummy.getRevengeTarget() == player) {
            dummy.setRevengeTarget(null);
        }

        // 5. 协同伴战索敌：若当前无目标，智能协同宿主当前正在交战的敌人
        if (dummy.getAttackTarget() == null && player.ticksExisted % 5 == 0) {
            EntityLivingBase candidate = findCombatCandidate(player, dummy);
            if (candidate != null) {
                dummy.setAttackTarget(candidate);
                dummy.setRevengeTarget(candidate);
            }
        }
    }

    /**
     * 寻找适合让变身生物协助攻击的目标。
     */
    private static EntityLivingBase findCombatCandidate(EntityPlayer player, EntityLiving dummy) {
        // 1. 优先反击正在攻击玩家的敌人
        EntityLivingBase revenge = player.getRevengeTarget();
        if (isValidEnemy(player, dummy, revenge)) {
            return revenge;
        }

        // 2. 其次协助攻击玩家刚命中的目标
        EntityLivingBase lastAttacked = player.getLastAttackedEntity();
        if (isValidEnemy(player, dummy, lastAttacked)) {
            return lastAttacked;
        }

        // 3. 周围 8 格内主动攻击玩家的敌对生物
        List<EntityLiving> nearby = player.world.getEntitiesWithinAABB(EntityLiving.class,
                player.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D));
        for (EntityLiving mob : nearby) {
            if (mob != dummy && mob.getAttackTarget() == player && isValidEnemy(player, dummy, mob)) {
                return mob;
            }
        }

        return null;
    }

    private static boolean isValidEnemy(EntityPlayer player, EntityLiving dummy, EntityLivingBase target) {
        if (target == null || !target.isEntityAlive() || target == player || target == dummy) {
            return false;
        }
        FactionRelation rel = FactionGraphEngine.getInstance().getRelation(dummy, target);
        // 仅对非和平盟友（如 ENEMY 或 MODERATE 激怒）发起协助进攻
        return !rel.isPeaceful();
    }

    /**
     * 清洗 Dummy 实体中的索敌任务：剥离所有原生搜寻玩家的索敌器，防止误把玩家当作敌对目标。
     * （用户指示：地形破坏与其它 AI 行为均保留，只严格禁止锁定与伤害宿主自己）。
     */
    private static void sanitizeDummyAI(EntityLiving dummy) {
        try {
            if (dummy.targetTasks != null) {
                Iterator<EntityAITasks.EntityAITaskEntry> it = dummy.targetTasks.taskEntries.iterator();
                while (it.hasNext()) {
                    EntityAIBase action = it.next().action;
                    if (action instanceof EntityAINearestAttackableTarget) {
                        it.remove();
                        LOGGER.debug("[morphext-ai] Removed player-targeting selector from dummy {}", dummy.getName());
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[morphext-ai] Error sanitizing target tasks for dummy " + dummy.getName(), t);
        }
    }

    private static void handleCreeperDummy(EntityPlayer player, EntityCreeper creeper, boolean aiEnabled) {
        // 1. 如果 dummy 处于 dead/死亡状态，立即复活并重置状态，杜绝卡模型！
        if (creeper.isDead || creeper.getHealth() <= 0.0F) {
            creeper.isDead = false;
            creeper.deathTime = 0;
            creeper.setHealth(creeper.getMaxHealth());
            creeper.setCreeperState(-1);
            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(
                    EntityCreeper.class, creeper, 0, "timeSinceIgnited", "field_70833_d");
            aaaa0ggmc.morphext.network.AbilityNetwork.sendCreeperSwelling(player, false);
        }

        int state = creeper.getCreeperState();
        int timeSinceIgnited = net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                EntityCreeper.class, creeper, "timeSinceIgnited", "field_70833_d");

        if (aiEnabled && state > 0) {
            // AI [ON] 状态下苦力怕自主蓄力膨胀：同步给客户端
            if (player.ticksExisted % 10 == 0) {
                aaaa0ggmc.morphext.network.AbilityNetwork.sendCreeperSwelling(player, true);
            }

            // 当引信到达上限 (29~30 ticks)：触发真正的自爆大爆炸！
            int fuseTime = 30;
            try {
                fuseTime = net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                        EntityCreeper.class, creeper, "fuseTime", "field_82225_h");
            } catch (Throwable ignored) {}
            if (timeSinceIgnited >= fuseTime - 1) {
                boolean isPowered = creeper.getPowered();
                float power = isPowered ? 6.0F : 3.0F;
                boolean mobGriefing = player.world.getGameRules().getBoolean("mobGriefing");

                // 立即重置 dummy 状态，防止它因原生 explode() 调 setDead() 卡死
                creeper.setCreeperState(-1);
                net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(
                        EntityCreeper.class, creeper, 0, "timeSinceIgnited", "field_70833_d");
                creeper.isDead = false;
                creeper.deathTime = 0;
                aaaa0ggmc.morphext.network.AbilityNetwork.sendCreeperSwelling(player, false);

                // 产生真实环境大爆炸
                net.minecraft.world.Explosion explosion = player.world.createExplosion(
                        player, player.posX, player.posY + player.height * 0.3D, player.posZ, power, mobGriefing);

                // 苦力怕 AI 自爆副作用：生存模式下宿主死亡
                if (!player.isCreative()) {
                    net.minecraft.util.DamageSource deathSource = net.minecraft.util.DamageSource.causeExplosionDamage(explosion);
                    player.attackEntityFrom(deathSource, Float.MAX_VALUE);
                    if (player.isEntityAlive()) {
                        player.attackEntityFrom(net.minecraft.util.DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
                    }
                }
            }
        } else if (!aaaa0ggmc.morphext.integration.metamorph.vanilla.CreeperFuseAction.isPrimed(player)) {
            // 未在蓄力且未在主动技能蓄力：如果留在膨胀状态，予以平息
            if (state > 0 || timeSinceIgnited > 0) {
                creeper.setCreeperState(-1);
                net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(
                        EntityCreeper.class, creeper, 0, "timeSinceIgnited", "field_70833_d");
                aaaa0ggmc.morphext.network.AbilityNetwork.sendCreeperSwelling(player, false);
            }
        }
    }
}

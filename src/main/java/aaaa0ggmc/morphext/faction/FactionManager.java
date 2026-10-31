package aaaa0ggmc.morphext.faction;

import aaaa0ggmc.morphext.ability.IPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphAbilityManager;
import aaaa0ggmc.morphext.profiler.ProfilerBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 细分阵营与拓扑关系调度中枢 (Faction & Relation Manager)：
 *  - 挂载 FactionGraphEngine 关系图引擎；
 *  - 拦截目标索敌与敌对判定，彻底阻断同族与同盟误击；
 *  - 维护 ENEMY / MODERATE / MEMBER / RELATIVE / LORD 全层级外交规则；
 *  - 驱动 Lord 领主协同集火机制。
 */
public class FactionManager {
    private static final Logger LOGGER = LogManager.getLogger("morphext-faction");
    private static final FactionManager INSTANCE = new FactionManager();
    private static final Map<String, Long> RALLY_COOLDOWNS = new HashMap<>();

    private static Field FIELD_AI_TARGET;
    private static Field FIELD_AI_NEAREST_TARGET;

    static {
        try {
            FIELD_AI_TARGET = ReflectionHelper.findField(EntityAITarget.class, "target", "field_188509_g");
            FIELD_AI_TARGET.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.warn("[morphext-faction] Failed to reflect EntityAITarget.target", t);
        }
        try {
            FIELD_AI_NEAREST_TARGET = ReflectionHelper.findField(EntityAINearestAttackableTarget.class, "targetEntity", "field_75309_a");
            FIELD_AI_NEAREST_TARGET.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.warn("[morphext-faction] Failed to reflect EntityAINearestAttackableTarget.targetEntity", t);
        }
    }

    private FactionManager() {}

    public static FactionManager getInstance() {
        return INSTANCE;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        LOGGER.info("[morphext] FactionManager registered to Forge event bus.");
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote) {
            FactionJsonConfig.onWorldLoad(event.getWorld());
        }
    }

    /**
     * 获取玩家当前生效的所有阵营及其最高关系层级（兼容旧版接口）。
     */
    public static Map<Faction, FactionRank> getActiveFactions(EntityPlayer player) {
        if (player == null) {
            return Collections.emptyMap();
        }
        Map<Faction, FactionRank> map = new LinkedHashMap<>();
        List<IPassiveAbility> passives = MorphAbilityManager.getInstance().getActivePassives(player.getUniqueID());
        if (passives != null) {
            for (IPassiveAbility p : passives) {
                if (p instanceof FactionPassive) {
                    FactionPassive fp = (FactionPassive) p;
                    Faction f = fp.getFaction();
                    if (f != null) {
                        FactionRank existing = map.get(f);
                        if (existing == null || fp.getRank().isHigherThan(existing)) {
                            map.put(f, fp.getRank());
                        }
                    }
                }
            }
        }
        return map;
    }

    /**
     * 统一判定：当前关系下，这只生物是否允许把玩家当作攻击目标/造成伤害。
     *
     * <p>关键点：1.12.2 的 {@code getRevengeTarget()} 没有时间衰减，
     * 而 {@code getAttackingEntity()} 更是会长期保留历史攻击者。此前索敌放行使用
     * {@code getAttackingEntity()}、伤害放行使用 {@code getRevengeTarget()}，
     * 两者不同步就会出现“一直跟着玩家但打不出伤害”的怪异行为。这里统一成
     * “是否被该玩家伤害过”的单一判据，并在失去目标时立即清空寻路。</p>
     */
    private static boolean isAttackPermitted(EntityLiving mob, EntityPlayer player, FactionRelation relation) {
        boolean provoked = mob.getRevengeTarget() == player;
        switch (relation) {
            case LORD:
                return false;
            case RELATIVE:
                // 亲友：半血以上宽容误伤，只有半血以下才被迫自卫
                return provoked && mob.getHealth() <= mob.getMaxHealth() * 0.5F;
            case MEMBER:
                // 普通成员：不主动攻击，被该玩家攻击后才反击
                return provoked;
            default:
                return true;
        }
    }

    /**
     * 找到某个实体所属的变身宿主玩家（若该实体是 Morph dummy）。
     * 用于把“怪物攻击变身 dummy”也纳入阵营判定，避免怪物贴着玩家攻击一个不可见的替身。
     */
    private static EntityPlayer findMorphOwner(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity.world == null || entity.world.isRemote) {
            return null;
        }
        for (EntityPlayer candidate : entity.world.playerEntities) {
            if (candidate == null) {
                continue;
            }
            try {
                mchorse.metamorph.capabilities.morphing.IMorphing morphing =
                        mchorse.metamorph.capabilities.morphing.Morphing.get(candidate);
                mchorse.metamorph.api.morphs.AbstractMorph morph =
                        morphing != null ? morphing.getCurrentMorph() : null;
                if (morph instanceof mchorse.metamorph.api.morphs.EntityMorph) {
                    // 使用无参 getEntity()，避免为了识别 dummy 而提前创建实体。
                    net.minecraft.entity.EntityLivingBase dummy =
                            ((mchorse.metamorph.api.morphs.EntityMorph) morph).getEntity();
                    if (dummy != null && dummy == entity) {
                        return candidate;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 调试用：按当前关系、玩家身份等级与挑衅状态，判断该生物是否被允许攻击玩家。 */
    public static boolean canAttackPlayer(EntityLiving mob, EntityPlayer player) {
        if (mob == null || player == null) {
            return false;
        }
        FactionRelation relation = FactionGraphEngine.getInstance().getRelation(mob, player);
        return isAttackPermitted(mob, player, relation);
    }

    /**
     * 1. 核心索敌判定：通过 FactionGraphEngine 判定关系，重定向或清空攻击目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingSetAttackTarget(LivingSetAttackTargetEvent event) {
        Entity target = event.getTarget();
        EntityPlayer player = target instanceof EntityPlayer
                ? (EntityPlayer) target
                : findMorphOwner(target);
        if (player == null) {
            return;
        }

        long start = ProfilerBridge.begin();

        EntityLivingBase attacker = event.getEntityLiving();
        if (attacker != null && aaaa0ggmc.morphext.ability.morph.MorphAiEngine.isHostDummyEntity(player, attacker)) {
            if (attacker instanceof EntityLiving) {
                suppressMobTarget((EntityLiving) attacker);
            }
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }
        if (!(attacker instanceof EntityLiving)) {
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }
        EntityLiving mob = (EntityLiving) attacker;

        // 使用图引擎计算生物对待玩家的外交关系
        FactionRelation relation = FactionGraphEngine.getInstance().getRelation(mob, player);
        if (!isAttackPermitted(mob, player, relation)) {
            suppressMobTarget(mob);
        }

        ProfilerBridge.end("morphext:faction_target", start);
    }

    /**
     * 每 tick 兜底：原版 {@code EntityAITarget.shouldContinueExecuting()} 会在
     * {@code getAttackTarget() == null} 时回退到任务内部缓存的 target，并再次调用
     * {@code setAttackTarget}。若只依赖 set 事件，某些 AI 会保留路径继续贴近玩家。
     * 这里仅对“当前目标正好是玩家”的生物做一次轻量校验，失去资格时立即清空目标、
     * 仇恨、寻路与移动输入，保证不会再出现“凑过来但打不到”的状态。
     */
    @SubscribeEvent
    public void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase base = event.getEntityLiving();
        if (base == null || base.world.isRemote || !(base instanceof EntityLiving)) {
            return;
        }
        EntityLiving mob = (EntityLiving) base;
        EntityLivingBase target = mob.getAttackTarget();
        if (!(target instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) target;
        if (aaaa0ggmc.morphext.ability.morph.MorphAiEngine.isHostDummyEntity(player, mob)) {
            suppressMobTarget(mob);
            return;
        }
        FactionRelation relation = FactionGraphEngine.getInstance().getRelation(mob, player);
        if (!isAttackPermitted(mob, player, relation)) {
            suppressMobTarget(mob);
        }
    }

    /**
     * 彻底重置并抑制怪物的索敌任务，防止残留的内部 target、寻路路径或移动输入
     * 让实体继续贴近玩家。
     */
    public static void suppressMobTarget(EntityLiving mob) {
        if (mob == null) return;
        mob.setAttackTarget(null);
        mob.setRevengeTarget(null);
        mob.setMoveForward(0.0F);
        mob.moveStrafing = 0.0F;
        try {
            if (mob.getNavigator() != null) {
                mob.getNavigator().clearPath();
            }
        } catch (Throwable ignored) {
        }

        // 清空 targetTasks 中的内部状态
        try {
            if (mob.targetTasks != null) {
                for (EntityAITasks.EntityAITaskEntry entry : mob.targetTasks.taskEntries) {
                    EntityAIBase action = entry.action;
                    if (action instanceof EntityAITarget && FIELD_AI_TARGET != null) {
                        FIELD_AI_TARGET.set(action, null);
                    }
                    if (action instanceof EntityAINearestAttackableTarget && FIELD_AI_NEAREST_TARGET != null) {
                        FIELD_AI_NEAREST_TARGET.set(action, null);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 主动形态动作（morph action）的伤害归属兜底。
     *
     * <p>第三方动作常用没有 owner 的静态 DamageSource，原版受害者不会记录 revenge target。
     * 动作执行期间 {@link aaaa0ggmc.morphext.integration.metamorph.MorphActionContext} 持有
     * 服务端玩家；如果此时目标生物受到的非玩家伤害命中，就把玩家记录为挑衅者，
     * 让 MEMBER/RELATIVE 的阵营规则能正常触发反击。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMobAttacked(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim instanceof EntityPlayer || !(victim instanceof EntityLiving)) {
            return;
        }
        if (victim.world == null || victim.world.isRemote) {
            return;
        }
        net.minecraft.entity.player.EntityPlayerMP actor =
                aaaa0ggmc.morphext.integration.metamorph.MorphActionContext.get();
        if (actor == null) {
            return;
        }
        if (aaaa0ggmc.morphext.ability.morph.MorphAiEngine.isHostDummyEntity(actor, victim)) {
            return;
        }
        // 普通玩家造成的伤害已经由原版写入 revenge target，这里只补“无主/非玩家”的 morph action 伤害。
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            return;
        }
        EntityLiving mob = (EntityLiving) victim;
        FactionRelation relation = FactionGraphEngine.getInstance().getRelation(mob, actor);
        if (relation == FactionRelation.MEMBER || relation == FactionRelation.RELATIVE) {
            mob.setRevengeTarget(actor);
            mob.setAttackTarget(actor);
            LOGGER.info("[morphext-faction] Morph action by {} provoked {} (relation={})",
                    actor.getName(), mob.getName(), relation.name());
        }
    }

    /**
     * 2. 核心受创防护：防止弹射物滞留、近战横扫对领主、亲友与同族成员造成误伤
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        EntityPlayer player = event.getEntityLiving() instanceof EntityPlayer
                ? (EntityPlayer) event.getEntityLiving()
                : findMorphOwner(event.getEntityLiving());
        if (player == null) {
            return;
        }

        long start = ProfilerBridge.begin();

        Entity trueSource = event.getSource().getTrueSource();
        Entity immediateSource = event.getSource().getImmediateSource();

        // 宿主自伤绝对豁免：调试 AI 自身或其投掷物/爆炸绝不伤害宿主玩家
        if (aaaa0ggmc.morphext.ability.morph.MorphAiEngine.isHostDummyEntity(player, trueSource)
                || aaaa0ggmc.morphext.ability.morph.MorphAiEngine.isHostDummyEntity(player, immediateSource)) {
            event.setCanceled(true);
            ProfilerBridge.end("morphext:faction_attack", start);
            return;
        }

        if (!(trueSource instanceof EntityLivingBase)) {
            ProfilerBridge.end("morphext:faction_attack", start);
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) trueSource;

        FactionRelation relation = FactionGraphEngine.getInstance().getRelation(attacker, player);
        if (attacker instanceof EntityLiving
                && !isAttackPermitted((EntityLiving) attacker, player, relation)) {
            // 统一按“关系 + 是否被该玩家攻击过”的判据处理：
            // LORD 永不伤害；RELATIVE 半血以上宽容；MEMBER 只有被该玩家攻击后才反击。
            event.setCanceled(true);
            suppressMobTarget((EntityLiving) attacker);
            ProfilerBridge.end("morphext:faction_attack", start);
            return;
        }

        ProfilerBridge.end("morphext:faction_attack", start);
    }

    /**
     * 3. 领主协同集火机制：当玩家攻击异族目标时，号令周围同盟与臣属一同发起攻击！
     */
    public static void rallyAllies(EntityPlayer player, Faction faction, EntityLivingBase target) {
        if (player == null || target == null || !target.isEntityAlive() || player.world.isRemote) {
            return;
        }
        long start = ProfilerBridge.begin();

        // 冷却限制：每个玩家每 5 秒最多触发一次群体协同集火号令
        String coolKey = player.getUniqueID().toString();
        long now = player.world.getTotalWorldTime();
        Long last = RALLY_COOLDOWNS.get(coolKey);
        if (last != null && now - last < 100L) {
            ProfilerBridge.end("morphext:faction_rally", start);
            return;
        }
        RALLY_COOLDOWNS.put(coolKey, now);

        double radius = 24.0D;
        List<EntityLiving> nearby = player.world.getEntitiesWithinAABB(EntityLiving.class,
                player.getEntityBoundingBox().grow(radius, 8.0D, radius));

        FactionGraphEngine engine = FactionGraphEngine.getInstance();
        int count = 0;
        for (EntityLiving mob : nearby) {
            if (mob == target || !mob.isEntityAlive()) continue;

            // 检查生物对玩家的关系：若为臣服(LORD)或亲友同盟(RELATIVE)，响应集火号召
            FactionRelation mobToPlayer = engine.getRelation(mob, player);
            if (mobToPlayer == FactionRelation.LORD || mobToPlayer == FactionRelation.RELATIVE) {
                // 检查生物对受击目标的关系：若受击目标也是亲友/领主，避免同盟相残
                FactionRelation mobToTarget = engine.getRelation(mob, target);
                if (!mobToTarget.isPeaceful()) {
                    mob.setAttackTarget(target);
                    mob.setRevengeTarget(target);
                    count++;
                }
            }
        }

        if (count > 0 && player.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) player.world;
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    player.posX, player.posY + player.getEyeHeight() + 0.5D, player.posZ,
                    12, 0.4D, 0.3D, 0.4D, 0.05D);
            LOGGER.debug("[morphext-faction] Rallied {} allies for player {} against target {}", count, player.getName(), target.getName());
        }

        ProfilerBridge.end("morphext:faction_rally", start);
    }
}

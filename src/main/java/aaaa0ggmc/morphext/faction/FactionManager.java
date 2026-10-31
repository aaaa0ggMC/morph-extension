package aaaa0ggmc.morphext.faction;

import aaaa0ggmc.morphext.ability.IPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphAbilityManager;
import aaaa0ggmc.morphext.profiler.ProfilerBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 细分阵营核心调度管理器：
 *  - 拦截目标索敌与敌对判定；
 *  - 维护 Member / Relative / Lord 层级规则；
 *  - 驱动 Lord 领主协同集火机制。
 */
public class FactionManager {
    private static final Logger LOGGER = LogManager.getLogger("morphext-faction");
    private static final FactionManager INSTANCE = new FactionManager();
    private static final Map<String, Long> RALLY_COOLDOWNS = new HashMap<>();

    private FactionManager() {}

    public static FactionManager getInstance() {
        return INSTANCE;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        LOGGER.info("[morphext] FactionManager registered to Forge event bus.");
    }

    /**
     * 获取玩家当前生效的所有阵营及其最高关系层级。
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
     * 1. 核心索敌判定：根据 Member / Relative / Lord 规则重定向或清空攻击目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingSetAttackTarget(LivingSetAttackTargetEvent event) {
        Entity target = event.getTarget();
        if (!(target instanceof EntityPlayer)) {
            return;
        }

        long start = ProfilerBridge.begin();

        EntityPlayer player = (EntityPlayer) target;
        EntityLivingBase attacker = event.getEntityLiving();
        if (!(attacker instanceof EntityLiving)) {
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }
        EntityLiving mob = (EntityLiving) attacker;

        Map<Faction, FactionRank> factions = getActiveFactions(player);
        if (factions.isEmpty()) {
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }

        FactionRank highestRank = null;
        for (Map.Entry<Faction, FactionRank> entry : factions.entrySet()) {
            if (entry.getKey().contains(mob)) {
                if (highestRank == null || entry.getValue().isHigherThan(highestRank)) {
                    highestRank = entry.getValue();
                }
            }
        }

        if (highestRank == null) {
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }

        // 判定 1：LORD 领主 —— 无论何种情况永不攻击！
        if (highestRank == FactionRank.LORD) {
            mob.setAttackTarget(null);
            mob.setRevengeTarget(null);
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }

        boolean playerProvoked = (mob.getRevengeTarget() == player || mob.getAttackingEntity() == player);

        // 判定 2：RELATIVE 亲友 —— 不主动攻击；若被攻击，半血以上宽容不还手，半血以下被迫自卫
        if (highestRank == FactionRank.RELATIVE) {
            if (!playerProvoked) {
                mob.setAttackTarget(null);
            } else {
                // 检查生物生命值
                if (mob.getHealth() > mob.getMaxHealth() * 0.5F) {
                    // 半血以上：失误宽容，清除仇恨
                    mob.setAttackTarget(null);
                    mob.setRevengeTarget(null);
                }
                // 半血以下：正常允许反击
            }
            ProfilerBridge.end("morphext:faction_target", start);
            return;
        }

        // 判定 3：MEMBER 普通成员 —— 不主动攻击；被攻击则正常反击
        if (highestRank == FactionRank.MEMBER) {
            if (!playerProvoked) {
                mob.setAttackTarget(null);
            }
        }
        ProfilerBridge.end("morphext:faction_target", start);
    }

    /**
     * 2. 核心受创防护：防止弹射物未结清或近战挥舞对领主与宽容亲友造成伤害
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }

        long start = ProfilerBridge.begin();

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityLivingBase)) {
            ProfilerBridge.end("morphext:faction_attack", start);
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) trueSource;

        Map<Faction, FactionRank> factions = getActiveFactions(player);
        if (factions.isEmpty()) {
            ProfilerBridge.end("morphext:faction_attack", start);
            return;
        }

        for (Map.Entry<Faction, FactionRank> entry : factions.entrySet()) {
            if (entry.getKey().contains(attacker)) {
                if (entry.getValue() == FactionRank.LORD) {
                    event.setCanceled(true);
                    ProfilerBridge.end("morphext:faction_attack", start);
                    return;
                }
                if (entry.getValue() == FactionRank.RELATIVE && attacker.getHealth() > attacker.getMaxHealth() * 0.5F) {
                    event.setCanceled(true);
                    ProfilerBridge.end("morphext:faction_attack", start);
                    return;
                }
            }
        }
        ProfilerBridge.end("morphext:faction_attack", start);
    }

    /**
     * 3. 领主协同集火机制：当玩家攻击异族目标时，号令周围同族生物一同攻击！
     */
    public static void rallyAllies(EntityPlayer player, Faction faction, EntityLivingBase target) {
        if (player == null || faction == null || target == null || !target.isEntityAlive() || player.world.isRemote) {
            return;
        }
        // 如果受击目标也是本阵营生物，不触发内讧集火
        if (faction.contains(target)) {
            return;
        }

        long start = ProfilerBridge.begin();

        String cooldownKey = player.getUniqueID().toString() + ":" + faction.getId();
        long now = System.currentTimeMillis();
        Long last = RALLY_COOLDOWNS.get(cooldownKey);
        if (last != null && now - last < 1200L) {
            ProfilerBridge.end("morphext:faction_rally", start);
            return;
        }
        RALLY_COOLDOWNS.put(cooldownKey, now);

        double radius = 20.0D;
        List<EntityLiving> allies = player.world.getEntitiesWithinAABB(EntityLiving.class,
                player.getEntityBoundingBox().grow(radius),
                e -> e != null && e.isEntityAlive() && !e.equals(player) && e != target && faction.contains(e));

        for (EntityLiving ally : allies) {
            ally.setAttackTarget(target);
            ally.setRevengeTarget(target);
            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                        ally.posX, ally.posY + ally.getEyeHeight() + 0.35D, ally.posZ, 2, 0.15D, 0.15D, 0.15D, 0.02D);
            }
        }
        ProfilerBridge.end("morphext:faction_rally", start);
    }
}

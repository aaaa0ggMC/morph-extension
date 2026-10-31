package aaaa0ggmc.morphext.faction;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高性能阵营拓扑关系图引擎 (Faction Relationship Graph Engine)。
 *
 * 核心机制：
 * 1. 节点与继承树 (Nodes & Inheritance Tree)：
 *    支持阵营标签树形继承（如 zombie -> undead, zombie_villager -> zombie -> undead），
 *    子阵营自动继承父阵营的外交与敌对关系。
 * 2. 关系边矩阵 (Relationship Edge Matrix)：
 *    支持定向 (Directed) 与双向 (Symmetric) 关系定义：
 *    ENEMY, MODERATE, MEMBER, RELATIVE, LORD。
 * 3. 极速推导与记忆缓存 (O(1) Resolution & Memoized Cache)：
 *    在战时高频判断中，自动根据实体类型、变身形态与关系图推导终极外交关系，结果缓存至快速哈希表。
 */
public final class FactionGraphEngine {
    private static final Logger LOGGER = LogManager.getLogger("morphext-faction-graph");
    private static final FactionGraphEngine INSTANCE = new FactionGraphEngine();

    // 阵营父级继承表：child -> parents
    private final Map<String, Set<String>> parentHierarchy = new ConcurrentHashMap<>();
    // 实体 ID 到阵营标签的映射：e.g. "minecraft:zombie_villager" -> ["zombie", "undead"]
    private final Map<String, Set<String>> entityMappings = new ConcurrentHashMap<>();
    // 阵营间关系规则表："factionA->factionB" -> Relation
    private final Map<String, FactionRelation> relations = new ConcurrentHashMap<>();
    // 快速计算缓存："fromEntityKey#toEntityKey" -> Relation
    private final Map<String, FactionRelation> resolutionCache = new ConcurrentHashMap<>();

    private FactionGraphEngine() {}

    public static FactionGraphEngine getInstance() {
        return INSTANCE;
    }

    /**
     * 清空并重置图引擎状态（用于重新加载数据包/配置文件时）。
     */
    public synchronized void clear() {
        parentHierarchy.clear();
        entityMappings.clear();
        relations.clear();
        resolutionCache.clear();
        LOGGER.info("[morphext-graph] Faction graph engine state cleared.");
    }

    /**
     * 注册阵营继承关系：child 继承 parent 的所有外部关系。
     */
    public void registerParent(String childFaction, String parentFaction) {
        if (childFaction == null || parentFaction == null) return;
        String c = childFaction.trim().toLowerCase(Locale.ROOT);
        String p = parentFaction.trim().toLowerCase(Locale.ROOT);
        if (c.equals(p)) return;

        parentHierarchy.computeIfAbsent(c, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(p);
        resolutionCache.clear();
    }

    /**
     * 注册实体类或注册名与阵营的映射。
     */
    public void registerEntityMapping(String entityId, String faction) {
        if (entityId == null || faction == null) return;
        String e = entityId.trim().toLowerCase(Locale.ROOT);
        String f = faction.trim().toLowerCase(Locale.ROOT);

        entityMappings.computeIfAbsent(e, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(f);
        resolutionCache.clear();
    }

    /**
     * 注册两个阵营之间的单向或双向关系。
     */
    public void registerRelation(String fromFaction, String toFaction, FactionRelation relation, boolean symmetric) {
        if (fromFaction == null || toFaction == null || relation == null) return;
        String from = fromFaction.trim().toLowerCase(Locale.ROOT);
        String to = toFaction.trim().toLowerCase(Locale.ROOT);

        relations.put(makeKey(from, to), relation);
        if (symmetric) {
            relations.put(makeKey(to, from), relation);
        }
        resolutionCache.clear();
    }

    /**
     * 获取某个阵营及其所有向上递归派生的父级阵营集合。
     */
    public Set<String> getFactionWithAncestors(String faction) {
        if (faction == null) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        collectAncestors(faction.trim().toLowerCase(Locale.ROOT), result);
        return result;
    }

    private void collectAncestors(String current, Set<String> visited) {
        if (!visited.add(current)) return;
        Set<String> parents = parentHierarchy.get(current);
        if (parents != null) {
            for (String p : parents) {
                collectAncestors(p, visited);
            }
        }
    }

    /**
     * 核心推导：获取主体（observer）对待目标（target）的外交层级。
     */
    public FactionRelation getRelation(EntityLivingBase observer, EntityLivingBase target) {
        if (observer == null || target == null) {
            return FactionRelation.MODERATE;
        }
        if (observer == target) {
            return FactionRelation.LORD;
        }

        String obsKey = getEntityCacheKey(observer);
        String tgtKey = getEntityCacheKey(target);
        String cacheKey = obsKey + "#" + tgtKey;

        FactionRelation cached = resolutionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> obsFactions = resolveEntityFactions(observer);
        Set<String> tgtFactions = resolveEntityFactions(target);

        FactionRelation relation = resolveFactionSetsRelation(obsFactions, tgtFactions);

        /*
         * 玩家的“阵营身份等级”必须压过图里的同阵营默认边（例如 dread->dread RELATIVE）。
         * 玩家在 Dreadlands 的身份是 Member 时，同族生物应按照 Member 规则在被攻击后反击；
         * 身份是 Relative/Lord 时才使用宽容/臣服规则。该判断只影响“生物 → 玩家”，
         * 不改变生物与生物、玩家与玩家之间的图关系。
         */
        if (!(observer instanceof EntityPlayer) && target instanceof EntityPlayer) {
            FactionRelation rankRelation = resolvePlayerRankRelation(observer, (EntityPlayer) target, obsFactions);
            if (rankRelation != null) {
                relation = rankRelation;
            }
        }

        resolutionCache.put(cacheKey, relation);
        return relation;
    }

    /**
     * 根据目标玩家在某阵营中的实际 FactionRank（来自当前形态授予的 FactionPassive），
     * 决定同族/同继承生物应使用的外交层级：
     *  - MEMBER   → 不主动攻击，被玩家攻击后正常反击
     *  - RELATIVE → 半血以上宽容
     *  - LORD     → 绝对臣服
     *
     * <p>优先使用最具体的阵营（生物直接所属阵营）；没有直接匹配时再看祖先阵营。
     * 玩家没有对应 FactionPassive（只是 morph 名称映射到阵营）时返回 null，沿用图规则。</p>
     */
    private FactionRelation resolvePlayerRankRelation(EntityLivingBase observer, EntityPlayer player,
                                                      Set<String> observerFactions) {
        Map<Faction, FactionRank> ranks = FactionManager.getActiveFactions(player);
        if (ranks.isEmpty() || observerFactions.isEmpty()) {
            return null;
        }

        FactionRank direct = null;
        FactionRank inherited = null;
        for (Map.Entry<Faction, FactionRank> entry : ranks.entrySet()) {
            Faction faction = entry.getKey();
            if (faction == null || faction.getId() == null || entry.getValue() == null) {
                continue;
            }
            String factionId = faction.getId().toLowerCase(Locale.ROOT);
            if (observerFactions.contains(factionId)) {
                if (direct == null || entry.getValue().isHigherThan(direct)) {
                    direct = entry.getValue();
                }
                continue;
            }
            for (String observerFaction : observerFactions) {
                if (getFactionWithAncestors(observerFaction).contains(factionId)) {
                    if (inherited == null || entry.getValue().isHigherThan(inherited)) {
                        inherited = entry.getValue();
                    }
                    break;
                }
            }
        }

        FactionRank rank = direct != null ? direct : inherited;
        if (rank == null) {
            return null;
        }
        switch (rank) {
            case LORD:
                return FactionRelation.LORD;
            case RELATIVE:
                return FactionRelation.RELATIVE;
            case MEMBER:
                return FactionRelation.MEMBER;
            default:
                return null;
        }
    }

    /**
     * 纯阵营集合推导。
     */
    public FactionRelation resolveFactionSetsRelation(Set<String> obsFactions, Set<String> tgtFactions) {
        if (obsFactions == null || obsFactions.isEmpty() || tgtFactions == null || tgtFactions.isEmpty()) {
            return FactionRelation.MODERATE;
        }

        // 1. 同阵营直接匹配：若双方共享任一有效核心阵营，默认至少为 RELATIVE
        for (String fObs : obsFactions) {
            if (tgtFactions.contains(fObs)) {
                // 如果该阵营自映射有更具体的规则（例如明确声明同阵营为 LORD 或 MEMBER）
                FactionRelation selfRel = relations.get(makeKey(fObs, fObs));
                return selfRel != null ? selfRel : FactionRelation.RELATIVE;
            }
        }

        // 2. 遍历双方所有祖先阵营，寻找显式配置的关系边
        FactionRelation bestRelation = null;
        for (String fObs : obsFactions) {
            Set<String> obsAncestors = getFactionWithAncestors(fObs);
            for (String fTgt : tgtFactions) {
                Set<String> tgtAncestors = getFactionWithAncestors(fTgt);

                for (String aObs : obsAncestors) {
                    for (String aTgt : tgtAncestors) {
                        FactionRelation r = relations.get(makeKey(aObs, aTgt));
                        if (r != null) {
                            if (bestRelation == null || r.getPriority() > bestRelation.getPriority()) {
                                bestRelation = r;
                            }
                        }
                    }
                }
            }
        }

        if (bestRelation != null) {
            return bestRelation;
        }

        // 3. 祖先交集检测：如果双方向上继承了同一个大阵营（如都是 undead），判定为 MEMBER
        for (String fObs : obsFactions) {
            Set<String> obsAncestors = getFactionWithAncestors(fObs);
            for (String fTgt : tgtFactions) {
                Set<String> tgtAncestors = getFactionWithAncestors(fTgt);
                for (String a : obsAncestors) {
                    if (tgtAncestors.contains(a)) {
                        return FactionRelation.MEMBER;
                    }
                }
            }
        }

        return FactionRelation.MODERATE;
    }

    /**
     * 解析任意生物实体或变身实体的所有所属阵营。
     */
    public Set<String> resolveEntityFactions(EntityLivingBase entity) {
        if (entity == null) return Collections.emptySet();
        Set<String> factions = new LinkedHashSet<>();

        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            // 1. 从当前玩家变身形态的声明能力中提取
            Map<Faction, FactionRank> active = FactionManager.getActiveFactions(player);
            for (Faction f : active.keySet()) {
                if (f != null && f.getId() != null) {
                    factions.add(f.getId().toLowerCase(Locale.ROOT));
                }
            }
            // 2. 从当前 Metamorph 变身形态名称中提取
            String morphName = getPlayerMorphName(player);
            if (morphName != null && !morphName.isEmpty()) {
                factions.addAll(getFactionsForEntityId(morphName));
            }
        } else {
            // 普通怪物/生物
            ResourceLocation regName = EntityList.getKey(entity);
            if (regName != null) {
                factions.addAll(getFactionsForEntityId(regName.toString()));
                factions.addAll(getFactionsForEntityId(regName.getPath()));
            }

            // 根据原版生物属性兜底推导
            if (entity.isEntityUndead()) {
                factions.add("undead");
            }
            if (entity.getCreatureAttribute() == EnumCreatureAttribute.ARTHROPOD) {
                factions.add("arthropod");
            }
            if (entity instanceof EntityAnimal) {
                factions.add("animal");
            }
            if (entity instanceof EntityMob && !factions.contains("undead") && !factions.contains("arthropod")) {
                factions.add("monster");
            }

            // 检查已注册的 Faction 动态谓词 (FactionRegistry)
            for (Faction f : FactionRegistry.all()) {
                if (f.contains(entity)) {
                    factions.add(f.getId().toLowerCase(Locale.ROOT));
                }
            }
        }

        return factions;
    }

    public Set<String> getFactionsForEntityId(String id) {
        if (id == null) return Collections.emptySet();
        String clean = id.trim().toLowerCase(Locale.ROOT);
        Set<String> set = entityMappings.get(clean);
        return set != null ? new LinkedHashSet<>(set) : Collections.emptySet();
    }

    /* ---------------- 调试/导出用只读快照 ---------------- */

    /** 当前图中出现过的全部阵营标签（声明、映射、关系边与注册表）。 */
    public Set<String> getKnownFactions() {
        Set<String> out = new TreeSet<>();
        out.addAll(parentHierarchy.keySet());
        for (Set<String> parents : parentHierarchy.values()) {
            out.addAll(parents);
        }
        for (Set<String> mapped : entityMappings.values()) {
            out.addAll(mapped);
        }
        for (String key : relations.keySet()) {
            int split = key.indexOf("->");
            if (split > 0) {
                out.add(key.substring(0, split));
                out.add(key.substring(split + 2));
            }
        }
        for (Faction faction : FactionRegistry.all()) {
            if (faction.getId() != null) {
                out.add(faction.getId().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    public Map<String, Set<String>> snapshotParentHierarchy() {
        Map<String, Set<String>> copy = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : parentHierarchy.entrySet()) {
            copy.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return copy;
    }

    public Map<String, Set<String>> snapshotEntityMappings() {
        Map<String, Set<String>> copy = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : entityMappings.entrySet()) {
            copy.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return copy;
    }

    public Map<String, FactionRelation> snapshotRelations() {
        Map<String, FactionRelation> copy = new TreeMap<>();
        copy.putAll(relations);
        return copy;
    }

    /** 单条显式关系边（不展开祖先），没有则返回 null。 */
    public FactionRelation getDirectRelation(String from, String to) {
        if (from == null || to == null) return null;
        return relations.get(makeKey(from.trim().toLowerCase(Locale.ROOT),
                to.trim().toLowerCase(Locale.ROOT)));
    }

    private static String getPlayerMorphName(EntityPlayer player) {
        try {
            mchorse.metamorph.capabilities.morphing.IMorphing morphing =
                    mchorse.metamorph.capabilities.morphing.Morphing.get(player);
            if (morphing != null) {
                mchorse.metamorph.api.morphs.AbstractMorph morph = morphing.getCurrentMorph();
                if (morph != null && morph.name != null) {
                    return morph.name;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getEntityCacheKey(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            String morph = getPlayerMorphName(player);
            String rankKey = playerRankKey(player);
            return "player:" + entity.getUniqueID().toString()
                    + (morph != null ? ":" + morph : ":human")
                    + ":r" + rankKey;
        }
        ResourceLocation reg = EntityList.getKey(entity);
        return reg != null ? reg.toString() : entity.getClass().getSimpleName();
    }

    /** 把玩家当前的阵营身份等级纳入关系缓存键，避免 passives 挂载前后缓存不刷新。 */
    private static String playerRankKey(EntityPlayer player) {
        Map<Faction, FactionRank> active = FactionManager.getActiveFactions(player);
        if (active.isEmpty()) {
            return "";
        }
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<Faction, FactionRank> entry : active.entrySet()) {
            if (entry.getKey() != null && entry.getKey().getId() != null && entry.getValue() != null) {
                sorted.put(entry.getKey().getId().toLowerCase(Locale.ROOT), entry.getValue().getId());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String makeKey(String a, String b) {
        return a + "->" + b;
    }
}

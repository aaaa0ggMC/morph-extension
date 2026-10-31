package aaaa0ggmc.morphext.faction;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.*;

/**
 * 细分阵营注册中心。
 */
public final class FactionRegistry {
    private static final Map<String, Faction> FACTIONS = new LinkedHashMap<>();
    private static boolean registered = false;

    private FactionRegistry() {}

    public static Faction register(Faction faction) {
        if (faction != null && faction.getId() != null) {
            FACTIONS.put(faction.getId().toLowerCase(Locale.ROOT), faction);
        }
        return faction;
    }

    public static Faction get(String id) {
        if (id == null) return null;
        return FACTIONS.get(id.toLowerCase(Locale.ROOT));
    }

    public static Collection<Faction> all() {
        return Collections.unmodifiableCollection(FACTIONS.values());
    }

    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;

        // 1. 注册核心细分阵营与判定谓词
        registerVanillaFactions();

        // 2. 为每个阵营生成并注册三级被动原型 (MEMBER / RELATIVE / LORD)
        for (Faction faction : FACTIONS.values()) {
            registerRankPassives(faction);
        }
    }

    private static void registerRankPassives(Faction faction) {
        String fId = faction.getId();

        // Member
        FactionPassive pMember = new FactionPassive(fId, FactionRank.MEMBER, faction);
        AbilityRegistry.register(pMember);
        AbilityRegistry.register(new FactionPassive("faction_" + fId, FactionRank.MEMBER, faction));
        AbilityRegistry.register(new FactionPassive("faction_" + fId + "_member", FactionRank.MEMBER, faction));
        AbilityRegistry.register(new FactionPassive("faction_" + fId + ":member", FactionRank.MEMBER, faction));

        // Relative
        FactionPassive pRelative = new FactionPassive("faction_" + fId + "_relative", FactionRank.RELATIVE, faction);
        AbilityRegistry.register(pRelative);
        AbilityRegistry.register(new FactionPassive("faction_" + fId + ":relative", FactionRank.RELATIVE, faction));

        // Lord
        FactionPassive pLord = new FactionPassive("faction_" + fId + "_lord", FactionRank.LORD, faction);
        AbilityRegistry.register(pLord);
        AbilityRegistry.register(new FactionPassive("faction_" + fId + ":lord", FactionRank.LORD, faction));
    }

    private static void registerVanillaFactions() {
        // 亡灵
        register(new Faction("undead", "morphext.faction.undead.name", "morphext.faction.undead.desc",
                new ItemStack(Items.ROTTEN_FLESH)))
                .addPredicate(e -> e != null && (e.isEntityUndead() || e instanceof EntityWither || e instanceof EntityWitherSkeleton));

        // 凋灵/暗黑族
        register(new Faction("wither", "morphext.faction.wither.name", "morphext.faction.wither.desc",
                new ItemStack(Items.SKULL, 1, 1)))
                .addPredicate(e -> e instanceof EntityWither || e instanceof EntityWitherSkeleton);

        // 灾厄
        register(new Faction("illager", "morphext.faction.illager.name", "morphext.faction.illager.desc",
                new ItemStack(Items.EMERALD)))
                .addPredicate(e -> e instanceof EntitySpellcasterIllager || e instanceof EntityVindicator
                        || e instanceof EntityIllusionIllager || e instanceof EntityVex || e instanceof EntityWitch);

        // 女巫系
        register(new Faction("witch", "morphext.faction.witch.name", "morphext.faction.witch.desc",
                new ItemStack(Items.POTIONITEM)))
                .addPredicate(e -> e instanceof EntityWitch);

        // 节肢动物
        register(new Faction("arthropod", "morphext.faction.arthropod.name", "morphext.faction.arthropod.desc",
                new ItemStack(Items.SPIDER_EYE)))
                .addPredicate(e -> e != null && (e.getCreatureAttribute() == EnumCreatureAttribute.ARTHROPOD
                        || e instanceof EntitySpider || e instanceof EntitySilverfish || e instanceof EntityEndermite));

        // 下界族
        register(new Faction("nether", "morphext.faction.nether.name", "morphext.faction.nether.desc",
                new ItemStack(Items.BLAZE_ROD)))
                .addPredicate(e -> e instanceof EntityBlaze || e instanceof EntityGhast || e instanceof EntityMagmaCube
                        || e instanceof EntityPigZombie || e instanceof EntityWitherSkeleton || (e != null && e.dimension == -1));

        // 末影族
        register(new Faction("ender", "morphext.faction.ender.name", "morphext.faction.ender.desc",
                new ItemStack(Items.ENDER_EYE)))
                .addPredicate(e -> e instanceof EntityEnderman || e instanceof EntityDragon || e instanceof EntityShulker || e instanceof EntityEndermite);

        // 村民
        register(new Faction("villager", "morphext.faction.villager.name", "morphext.faction.villager.desc",
                new ItemStack(Items.BREAD)))
                .addPredicate(e -> e instanceof EntityVillager || e instanceof EntityIronGolem || e instanceof EntitySnowman);

        // 傀儡族
        register(new Faction("golem", "morphext.faction.golem.name", "morphext.faction.golem.desc",
                new ItemStack(Blocks.IRON_BLOCK)))
                .addPredicate(e -> e instanceof EntityIronGolem || e instanceof EntitySnowman);

        // 牲畜动物
        register(new Faction("animal", "morphext.faction.animal.name", "morphext.faction.animal.desc",
                new ItemStack(Items.WHEAT)))
                .addPredicate(e -> e instanceof EntityAnimal || e instanceof EntityBat);

        // 掠食野兽
        register(new Faction("beast", "morphext.faction.beast.name", "morphext.faction.beast.desc",
                new ItemStack(Items.BONE)))
                .addPredicate(e -> e instanceof EntityWolf || e instanceof EntityOcelot || e instanceof EntityPolarBear);

        // 史莱姆黏液
        register(new Faction("slime", "morphext.faction.slime.name", "morphext.faction.slime.desc",
                new ItemStack(Items.SLIME_BALL)))
                .addPredicate(e -> e instanceof EntitySlime);

        // 水生海洋
        register(new Faction("aquatic", "morphext.faction.aquatic.name", "morphext.faction.aquatic.desc",
                new ItemStack(Items.FISH)))
                .addPredicate(e -> e instanceof EntitySquid || e instanceof EntityGuardian);

        // 海晶守卫
        register(new Faction("guardian", "morphext.faction.guardian.name", "morphext.faction.guardian.desc",
                new ItemStack(Items.PRISMARINE_SHARD)))
                .addPredicate(e -> e instanceof EntityGuardian);

        // 苦力怕
        register(new Faction("creeper", "morphext.faction.creeper.name", "morphext.faction.creeper.desc",
                new ItemStack(Items.GUNPOWDER)))
                .addPredicate(e -> e instanceof EntityCreeper);

        // 猪人族
        register(new Faction("pigman", "morphext.faction.pigman.name", "morphext.faction.pigman.desc",
                new ItemStack(Items.GOLDEN_SWORD)))
                .addPredicate(e -> e instanceof EntityPigZombie);

        // 远古泰坦巨人族
        register(new Faction("giant", "morphext.faction.giant.name", "morphext.faction.giant.desc",
                new ItemStack(Blocks.STONEBRICK)))
                .addPredicate(e -> e instanceof EntityGiantZombie);

        // 地底隐士与洞穴异象（具体模组谓词由对应 patch 模组注入，核心只保留空阵营外壳）
        register(new Faction("underworld", "morphext.faction.underworld.name", "morphext.faction.underworld.desc",
                new ItemStack(Blocks.COBBLESTONE)));

        // 预定义深渊扩展阵营外壳（具体谓词由 morphext-patch-abyssalcraft 动态注入）
        register(new Faction("abyssal", "morphext.faction.abyssal.name", "morphext.faction.abyssal.desc",
                new ItemStack(Blocks.OBSIDIAN)));
        register(new Faction("dread", "morphext.faction.dread.name", "morphext.faction.dread.desc",
                new ItemStack(Items.REDSTONE)));
        register(new Faction("omothol", "morphext.faction.omothol.name", "morphext.faction.omothol.desc",
                new ItemStack(Items.NETHER_STAR)));
        register(new Faction("shadow", "morphext.faction.shadow.name", "morphext.faction.shadow.desc",
                new ItemStack(Items.COAL)));
        register(new Faction("shoggoth", "morphext.faction.shoggoth.name", "morphext.faction.shoggoth.desc",
                new ItemStack(Items.SLIME_BALL)));
        register(new Faction("antimatter", "morphext.faction.antimatter.name", "morphext.faction.antimatter.desc",
                new ItemStack(Items.NETHERBRICK)));
        register(new Faction("demon", "morphext.faction.demon.name", "morphext.faction.demon.desc",
                new ItemStack(Items.MAGMA_CREAM)));
    }
}

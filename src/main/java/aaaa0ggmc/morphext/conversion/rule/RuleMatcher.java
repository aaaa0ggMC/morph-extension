package aaaa0ggmc.morphext.conversion.rule;

import aaaa0ggmc.morphext.conversion.DeathContext;
import aaaa0ggmc.morphext.conversion.WorldQuery;
import aaaa0ggmc.morphext.form.FormCategories;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * 规则条件判定（纯函数：给定规则 + 死亡上下文 -&gt; 通过几项 / 缺什么）。
 *
 * 判定顺序按代价从低到高：形态类别 -&gt; 死法 -&gt; 自伤否决 -&gt; 击杀者 -&gt;
 * 献祭物 -&gt; 维度/高度 -&gt; 时间天气 -&gt; 药水/情绪 -&gt; 群系 -&gt; 结构 -&gt; 附近方块。
 * 群系/结构/方块会强制加载区块或做世界查询，放在最后。
 */
public final class RuleMatcher {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");

    /**
     * 情绪查询钩子：情绪系统落地时由它赋值（核心不依赖情绪系统）。
     * 入参（玩家, 情绪 id）；未赋值时带 emotions 条件的规则恒不命中，
     * 并在首次遇到该类规则时告警一次。
     */
    public static BiPredicate<EntityPlayerMP, String> EMOTION_CHECK = (player, id) -> false;

    private static boolean emotionWarned;

    private RuleMatcher() {
    }

    /**
     * @return 已通过的条件数（0 = 一项没过）；missing 被清空后填入未通过项
     */
    public static int evaluate(ConversionRule rule, DeathContext ctx, List<String> missing) {
        missing.clear();
        int score = 0;
        EntityPlayerMP player = ctx.player;
        DamageSource source = ctx.source;

        // 形态类别
        if (!rule.state.formCategory.isEmpty()) {
            Set<String> cats = ctx.categories();
            boolean ok = false;
            for (String want : rule.state.formCategory) {
                if (cats.contains(FormCategories.normalize(want))) {
                    ok = true;
                    break;
                }
            }
            if (ok) {
                score++;
            } else {
                missing.add("formCategory" + (ctx.formId() == null ? "(no morph)" : "(" + ctx.formId() + ")"));
            }
        }

        // 死法类型
        if (!rule.death.types.isEmpty()) {
            String type = source == null ? "" : String.valueOf(source.getDamageType());
            if (rule.death.types.contains(type)) {
                score++;
            } else {
                missing.add("death(" + type + ")");
            }
        }

        // 自伤否决（环境死法天然不涉及；玩家自己造成的死亡默认排除）
        if (ctx.isSelfCaused() && !rule.selfCaused) {
            missing.add("selfCaused");
            return score;
        }

        // 击杀者
        if (!rule.death.sourceEntity.isEmpty() || !rule.death.source.isEmpty()) {
            Entity trueSource = source == null ? null : source.getTrueSource();
            Entity immediate = source == null ? null : source.getImmediateSource();
            boolean ok = true;
            if (ok && !rule.death.sourceEntity.isEmpty()) {
                ok = entityIdIn(trueSource, rule.death.sourceEntity);
            }
            if (ok && !rule.death.source.isEmpty()) {
                ok = entityIdIn(immediate, rule.death.source);
            }
            if (ok) {
                score++;
            } else {
                missing.add("sourceEntity");
            }
        }

        // 献祭物
        if (rule.item != null) {
            if (findHeld(player, rule.item) != null) {
                score++;
            } else {
                missing.add("item(" + rule.item.id + ")");
            }
        }

        // 维度
        if (rule.place.dimension != null) {
            if (player.dimension == rule.place.dimension) {
                score++;
            } else {
                missing.add("dimension(" + player.dimension + ")");
            }
        }

        // 高度
        if (rule.place.y != null && rule.place.y.min != null && rule.place.y.max != null) {
            if (player.posY >= rule.place.y.min && player.posY <= rule.place.y.max) {
                score++;
            } else {
                missing.add("y");
            }
        }

        // 时间 / 天气
        if (!"any".equals(rule.state.time)) {
            boolean day = player.world.isDaytime();
            boolean ok = "day".equals(rule.state.time) ? day : "night".equals(rule.state.time) && !day;
            if (ok) {
                score++;
            } else {
                missing.add("time(" + rule.state.time + ")");
            }
        }
        if (!"any".equals(rule.state.weather)) {
            boolean ok = weatherMatches(player.world, rule.state.weather);
            if (ok) {
                score++;
            } else {
                missing.add("weather(" + rule.state.weather + ")");
            }
        }

        // 原版药水效果
        if (!rule.state.effects.isEmpty()) {
            if (hasAnyEffect(player, rule.state.effects)) {
                score++;
            } else {
                missing.add("effects");
            }
        }

        // 情绪（钩子；未接入时恒不满足）
        if (!rule.state.emotions.isEmpty()) {
            if (!emotionWarned) {
                emotionWarned = true;
                LOGGER.warn("[morphext] Rule {} requires emotions but no emotion system is hooked", rule.id);
            }
            boolean ok = false;
            for (String id : rule.state.emotions) {
                if (EMOTION_CHECK.test(player, id)) {
                    ok = true;
                    break;
                }
            }
            if (ok) {
                score++;
            } else {
                missing.add("emotions");
            }
        }

        // 群系（读区块实存）
        if (!rule.place.biomeTypes.isEmpty()) {
            Biome biome = WorldQuery.biomeAt(player.world, new BlockPos(player));
            if (biomeMatches(biome, rule.place.biomeTypes)) {
                score++;
            } else {
                missing.add("biome");
            }
        }

        // 结构
        if (rule.place.structure != null && !rule.place.structure.isEmpty()) {
            StructureDetectors.Detector detector = StructureDetectors.get(rule.place.structure);
            if (detector != null && detector.matches(player.world, new BlockPos(player), rule.place.structureRadius)) {
                score++;
            } else {
                missing.add("structure(" + rule.place.structure + ")");
            }
        }

        // 附近方块
        if (!rule.place.nearBlocks.isEmpty() && rule.place.nearRadius > 0) {
            if (hasNearBlocks(player.world, new BlockPos(player), rule.place.nearBlocks, rule.place.nearRadius)) {
                score++;
            } else {
                missing.add("nearBlocks");
            }
        }

        return score;
    }

    private static boolean weatherMatches(net.minecraft.world.World world, String want) {
        switch (want) {
            case "clear":
                return !world.isRaining();
            case "rain":
                return world.isRaining() && !world.isThundering();
            case "thunder":
                return world.isThundering();
            default:
                return true;
        }
    }

    private static boolean biomeMatches(Biome biome, List<String> types) {
        for (String raw : types) {
            BiomeDictionary.Type type = typeByName(raw);
            if (type != null && BiomeDictionary.hasType(biome, type)) {
                return true;
            }
        }
        return false;
    }

    /** BiomeDictionary.Type 是静态字段类（非枚举），启动时反射建 名字-&gt;Type 表。 */
    private static final Map<String, BiomeDictionary.Type> TYPE_CACHE = new HashMap<>();

    static {
        for (java.lang.reflect.Field field : BiomeDictionary.Type.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || field.getType() != BiomeDictionary.Type.class) {
                continue;
            }
            try {
                BiomeDictionary.Type type = (BiomeDictionary.Type) field.get(null);
                TYPE_CACHE.put(field.getName().toUpperCase(Locale.ROOT), type);
                if (type.getName() != null) {
                    TYPE_CACHE.put(type.getName().toUpperCase(Locale.ROOT), type);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static BiomeDictionary.Type typeByName(String raw) {
        if (raw == null) {
            return null;
        }
        BiomeDictionary.Type type = TYPE_CACHE.get(raw.trim().toUpperCase(Locale.ROOT));
        if (type == null) {
            LOGGER.warn("[morphext] Unknown BiomeDictionary type: {}", raw);
        }
        return type;
    }

    private static boolean hasAnyEffect(EntityPlayerMP player, List<String> effects) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            Potion potion = effect.getPotion();
            if (potion == null) {
                continue;
            }
            String name = String.valueOf(Potion.REGISTRY.getNameForObject(potion));
            for (String want : effects) {
                if (FormCategories.normalize(want).equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNearBlocks(net.minecraft.world.World world, BlockPos pos, List<String> blocks, int radius) {
        BlockPos from = pos.add(-radius, -radius, -radius);
        BlockPos to = pos.add(radius, radius, radius);
        for (BlockPos p : BlockPos.getAllInBox(from, to)) {
            String name = String.valueOf(Block.REGISTRY.getNameForObject(world.getBlockState(p).getBlock()));
            for (String want : blocks) {
                if (FormCategories.normalize(want).equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean entityIdIn(Entity entity, List<String> ids) {
        String key = DeathContext.entityId(entity);
        if (key == null) {
            return false;
        }
        for (String want : ids) {
            if (FormCategories.normalize(want).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找手持献祭物的手；找不到返回 null。
     * hand: main / off / any（含副手都行，按配置收窄）。
     */
    public static EnumHand findHeld(EntityPlayerMP player, ConversionRule.ItemSpec spec) {
        if (player == null || spec == null || spec.id == null) {
            return null;
        }
        String want = FormCategories.normalize(spec.id);
        String hand = spec.hand == null ? "main" : spec.hand.trim().toLowerCase(Locale.ROOT);
        boolean main = !"off".equals(hand);
        boolean off = !"main".equals(hand);
        if (main && matches(player.getHeldItem(EnumHand.MAIN_HAND), spec, want)) {
            return EnumHand.MAIN_HAND;
        }
        if (off && matches(player.getHeldItem(EnumHand.OFF_HAND), spec, want)) {
            return EnumHand.OFF_HAND;
        }
        return null;
    }

    private static boolean matches(ItemStack stack, ConversionRule.ItemSpec spec, String want) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!want.equals(String.valueOf(Item.REGISTRY.getNameForObject(stack.getItem())))) {
            return false;
        }
        if (spec.nbt == null || spec.nbt.isEmpty()) {
            return true;
        }
        if (!stack.hasTagCompound()) {
            return false;
        }
        for (java.util.Map.Entry<String, String> entry : spec.nbt.entrySet()) {
            if (!stack.getTagCompound().hasKey(entry.getKey())
                    || !entry.getValue().equals(stack.getTagCompound().getString(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }
}

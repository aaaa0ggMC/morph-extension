package aaaa0ggmc.morphext.conversion.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条转化规则（数据驱动「仪式」的配置体）。
 *
 * 四要素：献祭物 item × 场地 place × 死法 death × 状态 state -&gt; 目标形态 target。
 * 由 {@code assets/&lt;modid&gt;/morphext/conversion_rules.json} 或
 * {@code config/morphext/conversion_rules.json} 提供；核心不认识任何具体规则。
 */
public final class ConversionRule {
    public String id;
    /** 归属线/章节 id（zombie_tide…），仅作命名空间与调试用。 */
    public String line = "";
    /** 目标形态 id（minecraft:husk）。 */
    public String target;
    /** 目标变种；null = 基础形态。 */
    public String variant = null;
    /** 优先级，大者先判定。 */
    public int priority = 100;
    /** 是否接受「玩家自己造成的死亡」（自爆/自伤）；环境死法不受此限。 */
    public boolean selfCaused = false;
    /** 献祭物；null = 不要求手持物（如沙葬的沙子是场地掩埋物）。 */
    public ItemSpec item = null;
    public PlaceSpec place = new PlaceSpec();
    public DeathSpec death = new DeathSpec();
    public StateSpec state = new StateSpec();
    /** 额外消耗（物品 id 列表，各从背包扣 1）。 */
    public List<String> consume = new ArrayList<>();
    /** 表现：lang 键 / 粒子 / 音效。 */
    public String title = null;
    public String subtitle = null;
    public String particle = null;
    public String sound = null;
    /** 进度/状态键（ul:ritual/&lt;id&gt;）；null = 不记录。 */
    public String advancement = null;
    public boolean debug = false;

    public static final class ItemSpec {
        public String id;
        /** NBT 字符串等值匹配（如 {"Potion": "minecraft:water"}）。 */
        public Map<String, String> nbt = new LinkedHashMap<>();
        /** main / off / any。 */
        public String hand = "main";
        public boolean consume = true;
        /** 消耗后留下的物品 id（水瓶 -&gt; 玻璃瓶）；null = 不留。 */
        public String leave = null;
    }

    public static final class PlaceSpec {
        /** 维度 id；null = 不限。 */
        public Integer dimension = null;
        /** BiomeDictionary 类型名（SANDY/WATER/RIVER/OCEAN/SWAMP…），任一命中。 */
        public List<String> biomeTypes = new ArrayList<>();
        /** 结构检测器 id（village…）；null = 不判结构。 */
        public String structure = null;
        public int structureRadius = 32;
        /** 附近方块（方块 id 列表，半径内任一存在）；配 nearRadius 使用。 */
        public List<String> nearBlocks = new ArrayList<>();
        public int nearRadius = 0;
        /** 高度区间。 */
        public YRange y = new YRange();
    }

    public static final class YRange {
        public Integer min = 0;
        public Integer max = 255;
    }

    public static final class DeathSpec {
        /** DamageSource.getDamageType() 白名单（inWall/drown/onFire/inFire/lava/mob…）。 */
        public List<String> types = new ArrayList<>();
        /** 直接来源实体 id 白名单（getImmediateSource）。 */
        public List<String> source = new ArrayList<>();
        /** 真实来源实体 id 白名单（getTrueSource，被谁打死）。 */
        public List<String> sourceEntity = new ArrayList<>();
    }

    public static final class StateSpec {
        /** 当前形态必须属于的类别（ul:undead…），任一命中。 */
        public List<String> formCategory = new ArrayList<>();
        /** 身上必须有的原版药水效果（注册名）。 */
        public List<String> effects = new ArrayList<>();
        /** 身上必须有的情绪（ul:fury…；情绪系统未落地时恒不满足）。 */
        public List<String> emotions = new ArrayList<>();
        /** any / day / night。 */
        public String time = "any";
        /** any / clear / rain / thunder。 */
        public String weather = "any";
    }

    /** JSON 文件外形：{"rules": [...]}。 */
    public static final class RuleFile {
        public List<ConversionRule> rules = new ArrayList<>();
    }
}

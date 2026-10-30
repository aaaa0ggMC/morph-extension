package aaaa0ggmc.morphext.integration.metamorph;

import java.util.HashMap;
import java.util.Map;

/** Metamorph 能力/攻击/动作的中文详情文案（状态页右键查看）。 */
public final class MetamorphAbilityDescriptions {
    private static final Map<String, String> TEXTS = new HashMap<>();

    static {
        put("amphibian", "水陆两栖：在水中不会耗尽氧气，界面上的氧气条被隐藏，可以一直待在水下。");
        put("armorless", "无法穿甲：身上的盔甲会被脱下并掉落，只剩手中和副手里的物品。");
        put("arrow_loose_packet", "射箭同步：松开弓弦时同步怪物拉弓瞄准的动作，只是视觉效果，不改变伤害。");
        put("arrow_nock_packet", "拉弓同步：开始拉弓时同步怪物拉弓瞄准的动作，只是视觉效果，不改变伤害。");
        put("arrowless", "无需箭矢：拉弓射箭不需要背包里有箭，空手也能把弓拉满射出。");
        put("baby_zombie_stats", "幼年僵尸属性：获得幼年僵尸的移动速度，身形小、跑得快。");
        put("backup", "呼救援军：受伤时让附近的生物转火攻击你的敌人，僵尸猪人也会因此被激怒。");
        put("bat_flight", "蝙蝠飞行：像蝙蝠一样飞行，可以朝任意方向自由移动。");
        put("bear_tactics", "北极熊战术：北极熊形态起身站立时，短时间内力量与抗性提升，但移动变慢。");
        put("become_morph_dragon", "化身 MorphDragon：变身为末影龙时自动转为 MorphDragon，获得飞行等龙的能力。");
        put("blaze_flight", "烈焰人飞行：离地且没有潜行时，按住跳跃键就能持续向上升。");
        put("blaze_update", "烈焰人行为：像烈焰人一样蓄力后连续发射小火球，动作会同步给别人看。");
        put("bow_movement_up", "拉弓不减速：拉弓射击时可以正常移动，像骷髅一样边走边射。");
        put("chicken_egg_lay", "定时下蛋：像鸡一样每隔一段时间在脚下生出一颗蛋，可以捡起来用。");
        put("chicken_riding", "骑乘鸡：右键一只鸡就能骑上去，鸡会获得速度并朝你面对的方向走。");
        put("climb", "攀爬：贴着垂直的墙面按前进键就能向上爬，不需要梯子。");
        put("cow_milk", "产出牛奶：别人用空桶右键你就能获得一桶牛奶，桶会被消耗掉。");
        put("cpae", "苦力怕引信接管：引信快燃尽时爆炸改在你当前位置触发，高压苦力怕范围翻倍；非创造模式会受到致命伤害并短暂隐身，随后引信重置。");
        put("creeper_charge", "闪电充能：被雷电击中时自动变成高压苦力怕，爆炸范围翻倍。");
        put("demorph", "解除变身：变成该形态后会自动退出变身，恢复成玩家的样子。");
        put("dragon_flight", "末影龙飞行：像末影龙一样高速飞行，飞行中撞到生物会造成伤害。");
        put("ender_affinity", "末影亲和：被箭等弹射物击中时有机会瞬间传送躲开，伤害被取消。");
        put("enderman_enemy", "末影人之敌：附近的末影人会主动攻击你，哪怕你没有盯着它们看。");
        put("fire_proof", "火焰免疫：免疫火焰、岩浆和燃烧伤害，像烈焰人一样不怕火。");
        put("flight", "自由飞行：像恶魂一样在空中飞行，用跳跃键控制升降。");
        put("generic_blank_arrow", "无箭射箭：不需要箭矢就能射出不带任何药效的普通箭。");
        put("ghast_update", "恶魂行为：像恶魂一样蓄力后吐出大火球，蓄力和发射都会同步给别人。");
        put("glide", "滑翔：像鸡一样缓慢飘落，下落速度大幅降低，不会摔伤。");
        put("golem_enemy", "村民之敌：附近的铁傀儡和雪傀儡会主动攻击你，哪怕你没招惹它们。");
        put("guardian_general", "守卫者特性：在水中被近战攻击时会向周围生物放出尖刺伤害，激光锁定的目标也会同步。");
        put("guardian_swim", "守卫者游动：像守卫者一样在水中快速游动，转向灵活。");
        put("hands_off", "禁手：打不开物品栏和箱子等容器界面，已打开的界面会被强制关闭。");
        put("harming_to_healing", "伤害药转治疗：被溅射型伤害药水命中不会受伤，反而按伤害量回复生命。");
        put("hide_hotbar", "隐藏快捷栏：屏幕下方的物品快捷栏被隐藏起来。");
        put("hide_hungerbar", "隐藏饥饿条：饥饿值始终保持满格，饥饿条也被隐藏，不会饿肚子。");
        put("hostile", "敌对标记：普通敌对生物不会主动攻击你，但村民型敌人仍会攻击你。");
        put("illager_updater", "灾厄行为：像唤魔者一样自动施放尖牙、召唤恼鬼、致盲和消失等法术。");
        put("itemless", "无物品：背包被清空，盔甲被脱下，快捷栏和饥饿条也一并隐藏。");
        put("jumping", "弹跳强化：跳跃能力大幅提升，跳得又高又远。");
        put("knockback_immune", "击退免疫：完全不受击退影响，被击中时不会被打退。");
        put("knockback_resistant", "击退抗性：大幅减少被击退的距离，更难被打退。");
        put("magic_resistance", "魔法抗性：受到的魔法类和瞬间伤害大幅降低。");
        put("melter", "会融化：身处炎热生物群系时会像雪傀儡一样持续受到火焰伤害。");
        put("mooshroom_interaction", "蘑菇牛互动：别人用碗右键你得蘑菇煲，用剪刀右键你会掉红蘑菇并变回牛。");
        put("natural_armor", "天生护甲：直接获得该怪物原型的护甲值，受到的伤害更少。");
        put("new_sun_allergy", "日光过敏：白天暴露在阳光下会持续燃烧，头上戴的头盔也会加速损坏。");
        put("night_vision", "夜视：持续获得夜视效果，黑暗的地方也看得清楚。");
        put("no_clip", "穿墙：失去碰撞体积，可以直接穿过方块和地形移动。");
        put("pig_lightning_struck", "雷击成猪人：被雷电击中时自动变成僵尸猪人，并获得火焰免疫。");
        put("pig_saddle", "可上鞍：别人手持鞍右键你会给你装上鞍并骑上来，解除变身时自动让他们下来。");
        put("prevent_fall", "免摔伤：不会受到摔落伤害，可以从高处直接跳下去。");
        put("rabbit_hop", "兔子跳：像兔子一样向前蹦跳，落地更轻盈。");
        put("regenerative", "自然再生：持续获得生命恢复效果，生命值会慢慢回升。");
        put("rideable", "可骑乘：其他玩家可以右键骑到你身上，解除变身时会自动把他们放下来。");
        put("rotten", "腐朽之躯：免疫中毒，像亡灵生物一样不怕毒。");
        put("sheep_interactions", "羊互动：别人用染料右键你能改变羊毛颜色，用剪刀右键你会被剪下羊毛。");
        put("silverfish_summon", "召唤蠹虫：被攻击时会破坏附近的虫蚀方块，从中召出蠹虫反击。");
        put("sinker", "下沉：在水中会往下沉，不会像玩家一样浮到水面上来。");
        put("skeleton_arrow", "骷髅箭：射出的箭带原型特效，凋灵骷髅的箭附加凋灵，流髑的箭附加缓慢。");
        put("slime_contact", "史莱姆碰撞：身体撞到其他生物时会像史莱姆一样造成接触伤害。");
        put("slime_jumping", "史莱姆跳：像史莱姆一样原地弹跳，落地时还有弹跳的音效。");
        put("snow_golem_pumpkin", "摘下南瓜：别人用剪刀右键你就能取下头上的南瓜，变成没头的雪傀儡。");
        put("snow_walk", "踏雪而行：走过的地方会自动铺上一层雪，像雪傀儡一样留下雪迹。");
        put("spider_riding", "骑乘蜘蛛：右键蜘蛛或洞穴蜘蛛就能骑上去，蜘蛛会朝你面对的方向移动。");
        put("squid", "鱿鱼行为：像鱿鱼一样在水中游动，还能向后喷出墨汁。");
        put("squid_swim", "鱿鱼游动：像鱿鱼一样在水中自由游动，转向和推进都更灵活。");
        put("step_up", "自动登阶：可以直接走上一格高的方块，不需要起跳。");
        put("sun_allergy", "日光过敏：白天暴露在阳光下会持续燃烧，像僵尸和骷髅一样怕光。");
        put("vex_flight", "恼鬼飞行：像恼鬼一样自由飞行，飞行时还能穿过方块和地形。");
        put("villager_lightning_struck", "雷击成女巫：被雷电击中时自动变成女巫，并获得魔法抗性。");
        put("vindicator_update", "卫道士行为：像卫道士一样挥斧攻击，可以切换是否攻击所有生物。");
        put("water_allergy", "恐水：身体碰到水就会持续受伤，像末影人一样怕水。");
        put("water_breath", "水下呼吸：可以在水下正常呼吸，氧气不会耗尽。");
        put("web_walker", "蛛网行走：在蜘蛛网里不会被缠住减速，可以正常穿行。");
        put("wither_armor", "凋灵甲：生命值高于一半时能完全闪避飞来的箭。");
        put("wither_block_break", "凋灵破块：受到攻击时会像凋灵一样破坏周围的方块，基岩等不可破坏的除外。");
        put("wither_flight", "凋灵飞行：生命值高于一半时可以像凋灵一样在空中飞行。");
        put("witherless", "凋灵免疫：免疫凋灵效果，变身时就有的凋灵效果会被清除。");
        put("wolf_enemy", "狼之敌：附近还没被驯服的狼会主动攻击你。");
        put("wolf_sit", "狼坐下：潜行时切换狼的坐下姿态，坐下后不会移动。");
        put("zombie_reinforcements", "僵尸增援：困难难度下被攻击时会召唤僵尸来攻击打你的敌人。");
        put("mob", "怪物攻击：左键攻击时使用该怪物原型的攻击方式和伤害。");
        put("synced_mob", "同步怪物攻击：左键攻击使用怪物原型的攻击方式，并把攻击动画同步给别人。");
        put("ai_target", "仇恨指挥：让变身后的生物攻击你准星瞄准的实体；潜行时释放则清除它的当前目标。");
        put("mob_act", "通用仇恨指挥：对任何生物形态都有效——释放时让变身后的生物攻击你准星瞄准的实体，潜行时释放则清除目标。适合给没有专属动作的形态补一个主动能力。");
        put("bear_standing", "北极熊起身：释放时切换北极熊的站立姿态，配合战术获得增益。");
        put("blaze_fireball", "烈焰人火球：释放时向前连续发射小火球，有冷却时间。");
        put("block_goblin_throw", "方块哥布林投掷：切换投掷方块的状态，向敌人扔出方块，需要暮色森林。");
        put("carrot_eat", "吃掉胡萝卜：释放时吃掉脚下的胡萝卜作物并让它重新生长，还会掉落胡萝卜。");
        put("creeper_explode", "苦力怕引信：释放时点燃引信并开始膨胀，再按一次可以取消，燃尽就爆炸。");
        put("ender_dragon_fireball", "末影龙火球：释放时向前发射末影龙的火焰弹，命中后留下持续伤害的区域。");
        put("enderman_teleport", "末影人传送：朝准星方向短距离传送，手里拿着方块时会把它一起带走。");
        put("endermite", "末影螨跳窜：释放时随机向附近短距离传送，用来躲开攻击。");
        put("ghast_fireball", "恶魂火球：释放时向前吐出一发巨大的火球，命中后爆炸，有冷却时间。");
        put("horse_jump", "马跃：释放时蓄力并高高跃起，潜行时释放可以减少蓄力。");
        put("illager_spellcast", "灾厄法术：释放时随机施放尖牙、召唤恼鬼、致盲或消失中的一种法术。");
        put("invisibility", "隐身：释放时获得一段时间的隐身效果，别人更难发现你。");
        put("ocelot_forms", "切换猫花色：释放时循环换成不同花色的猫形态。");
        put("sheep_grass_eating", "羊吃草：释放时低头吃脚下的草，吃完后重新长出羊毛并恢复颜色。");
        put("slime_size", "史莱姆体型：释放时放大体型，潜行时释放则缩小，体型会影响生命和跳跃。");
        put("sliverfish", "蠹虫藏匿：看向石头时释放会藏进石头变成方块形态；潜行时释放则把石头变成虫蚀方块。");
        put("sliverfish_reveal", "蠹虫现身：从方块形态里钻出来，重新变回蠹虫。");
        put("snowball", "投掷雪球：释放时向前扔出一颗雪球，可以把敌人击退。");
        put("spider_web", "铺蜘蛛网：释放时在脚下或准星位置放下一张蜘蛛网，挡住敌人的路。");
        put("spit", "羊驼吐口水：释放时向前吐出一团唾液，命中后造成伤害。");
        put("villager_profession", "切换职业：释放时随机变成另一种职业的村民。");
        put("witch_potion", "女巫投药：释放时向前抛出一瓶溅射药水，命中后产生对应的效果。");
        put("wither_skull", "凋灵之首：释放时向前发射凋灵头颅，命中后爆炸并附加凋灵效果。");
        put("wolf_forms", "切换怒态：释放时在普通狼和愤怒狼形态之间切换，愤怒时攻击性更强。");
    }

    private static void put(String key, String text) { TEXTS.put(key, text); }

    public static String get(String key) {
        return TEXTS.getOrDefault(key, "");
    }

    private MetamorphAbilityDescriptions() {
    }
}

package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.ability.Ability;
import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Metamorph 集成：把玩家已获得的 morph 提取成能力，暴露到轮盘。
 *
 * 这是“addon 模式”的第一个实例——Metamorph 有公开 API（无需 patch），
 * 这里把它内部的能力（morph 切换）包装成统一的 IAbility。
 */
public final class MetamorphIntegration {
    public static final String DEMORPH_ID = "metamorph:demorph";
    private static final String MORPH_PREFIX = "metamorph:morph:";
    /** morph 授予能力（被动 / action）的 id 前缀。 */
    public static final String GRANTED_PREFIX = "metamorph:granted:";
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("morph-extension");

    private MetamorphIntegration() {
    }

    public static void init() {
        AbilityRegistry.register(new DemorphAbility());
        MetamorphBridge.markInitialized();
    }

    /** 按玩家当前已获得的 morph 重建能力列表（获得/失去 morph、登录世界、打开 UI 时调用）。 */
    public static void refresh() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        IMorphing morphing = Morphing.get(mc.player);
        if (morphing == null) {
            return;
        }

        java.util.List<aaaa0ggmc.morphext.ability.IAbility> current = new ArrayList<>();
        for (mchorse.metamorph.api.morphs.AbstractMorph morph : morphing.getAcquiredMorphs()) {
            if (morph == null || morph.name == null || morph.name.isEmpty()) {
                continue;
            }
            String id = MORPH_PREFIX + morph.name;
            current.add(new MorphAbility(morph));
        }
        AbilityRegistry.replaceGrants("metamorph:acquired", current, mc.player);
    }

    /**
     * 同步当前 morph 授予的能力：Metamorph 配置（morphs.json）里该 morph 的
     * abilities（含被动）、attack、action 全部转成 morphext 能力。
     *  - abilities/attack -> 只读被动展示（原模组负责实际维持，不重复执行）
     *  - action（mob act）-> 主动能力，可绑轮盘按释放键执行，morph 期间暂时常驻
     * demorph 时全部移除。
     */
    public static void syncGranted() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        java.util.List<aaaa0ggmc.morphext.ability.IAbility> granted = new ArrayList<>();

        IMorphing morphing = Morphing.get(mc.player);
        AbstractMorph morph = morphing == null ? null : morphing.getCurrentMorph();
        if (morph == null) {
            AbilityRegistry.replaceGrants("metamorph:current", granted, mc.player);
            return;
        }
        mchorse.metamorph.api.MorphSettings settings = morph.getSettings();
        if (settings == null) {
            AbilityRegistry.replaceGrants("metamorph:current", granted, mc.player);
            return;
        }
        ItemStack icon = MorphIcons.forMorph(morph.name);
        LOGGER.info("[metamorph] syncGranted: morph={} abilities={} attack={} action={}",
                morph.name,
                settings.abilities == null ? 0 : settings.abilities.size(),
                settings.attack != null, settings.action != null);

        /* 被动能力 */
        if (settings.abilities != null) {
            for (mchorse.metamorph.api.abilities.IAbility ability : settings.abilities) {
                if (ability == null) {
                    continue;
                }
                String name = abilityName(ability);
                String id = GRANTED_PREFIX + morph.name + ":" + name;
                granted.add(new MorphGrantedAbility(morph.name, name, ability, null, icon));
            }
        }
        /* attack：作为被动展示项 */
        if (settings.attack != null) {
            String name = attackName(settings.attack);
            granted.add(new MorphGrantedAbility(morph.name, name, null, null, icon));
        }
        /* action（mob act）：主动能力，morph 期间可释放 */
        if (settings.action != null) {
            String name = actionName(settings.action);
            String id = GRANTED_PREFIX + morph.name + ":" + name;
            granted.add(new MorphGrantedAbility(morph.name, name, null, settings.action, icon));
        }
        /*
         * 通用 mob act：Metamorph Max 的 AITarget 对任何 EntityMorph 都有效
         * （让变身后的生物仇恨玩家准星瞄准的实体，潜行释放=清除目标），
         * 但 morphs.json 只给守卫配了它——这里给所有实体形态补上，
         * 这样任何模组的任何生物变身后都有这个主动能力。
         * 配置里已显式配了 ai_target 的形态不重复注入（避免同一能力两个条目）。
         * 软依赖：没装 Metamorph Max 时反射取不到实例就跳过。
         */
        if (morph instanceof mchorse.metamorph.api.morphs.EntityMorph
                && !isAITarget(settings.action)) {
            mchorse.metamorph.api.abilities.IAction mobAct = createMobActAction();
            if (mobAct != null) {
                String name = "mob_act";
                String id = GRANTED_PREFIX + morph.name + ":" + name;
                granted.add(new MorphGrantedAbility(morph.name, name, null, mobAct, icon));
            }
        }

        /* 外部数据驱动能力注入（由 morph_abilities.json 或其他模组注入） */
        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        if (def != null) {
            if (def.abilities != null) {
                for (String abName : def.abilities) {
                    boolean already = false;
                    for (aaaa0ggmc.morphext.ability.IAbility g : granted) {
                        if (g.getId().endsWith(":" + abName)) {
                            already = true;
                            break;
                        }
                    }
                    if (!already) {
                        mchorse.metamorph.api.abilities.IAbility metaAb = MorphManager.INSTANCE.abilities.get(abName);
                        granted.add(new MorphGrantedAbility(morph.name, abName, metaAb, null, icon));
                    }
                }
            }
            if (def.action != null && !def.action.isEmpty()) {
                boolean actionAlready = false;
                for (aaaa0ggmc.morphext.ability.IAbility g : granted) {
                    if (g.getId().endsWith(":" + def.action)) {
                        actionAlready = true;
                        break;
                    }
                }
                if (!actionAlready) {
                    mchorse.metamorph.api.abilities.IAction metaAct = MorphManager.INSTANCE.actions.get(def.action);
                    if (metaAct != null) {
                        granted.add(new MorphGrantedAbility(morph.name, def.action, null, metaAct, icon));
                    } else {
                        aaaa0ggmc.morphext.ability.IAbility customAbility = AbilityRegistry.get(def.action);
                        if (customAbility != null) {
                            granted.add(customAbility);
                        }
                    }
                }
            }
        }

        AbilityRegistry.replaceGrants("metamorph:current", granted, mc.player);
    }

    /** 判断是否已是 AITarget（避免和配置里显式配置的 ai_target 重复注入）。 */
    private static boolean isAITarget(mchorse.metamorph.api.abilities.IAction action) {
        return action != null
                && "jones.metamorphmax.metamorph.actions.AITarget".equals(action.getClass().getName());
    }

    /** 反射创建 Metamorph Max 的 AITarget（软依赖：没装 Max 时返回 null）。 */
    private static mchorse.metamorph.api.abilities.IAction createMobActAction() {
        try {
            Class<?> clazz = Class.forName("jones.metamorphmax.metamorph.actions.AITarget");
            return (mchorse.metamorph.api.abilities.IAction) clazz.newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从 MorphManager 注册表反查 action 名（如 blaze_fireball）。 */
    private static String actionName(mchorse.metamorph.api.abilities.IAction action) {
        try {
            for (Map.Entry<String, mchorse.metamorph.api.abilities.IAction> entry
                    : MorphManager.INSTANCE.actions.entrySet()) {
                if (entry.getValue() == action) {
                    return entry.getKey();
                }
            }
        } catch (Throwable ignored) {
        }
        return "action";
    }

    /** 从 MorphManager 注册表反查 attack 名（如 synced_mob）。 */
    private static String attackName(mchorse.metamorph.api.abilities.IAttackAbility attack) {
        try {
            for (Map.Entry<String, mchorse.metamorph.api.abilities.IAttackAbility> entry
                    : MorphManager.INSTANCE.attacks.entrySet()) {
                if (entry.getValue() == attack) {
                    return entry.getKey();
                }
            }
        } catch (Throwable ignored) {
        }
        return "attack";
    }

    /** 从 MorphManager 注册表反查能力名（Metamorph 的 IAbility 没有名字方法）。 */
    private static String abilityName(mchorse.metamorph.api.abilities.IAbility ability) {
        try {
            for (Map.Entry<String, mchorse.metamorph.api.abilities.IAbility> entry
                    : MorphManager.INSTANCE.abilities.entrySet()) {
                if (entry.getValue() == ability) {
                    return entry.getKey();
                }
            }
        } catch (Throwable ignored) {
        }
        return ability.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    /* ---------------- 客户端轮询兜底 ---------------- */

    private static String lastMorphName = "";
    private static int lastAcquiredCount = -1;
    private static Object lastPlayer;

    public static void resetClientTracking() {
        lastPlayer = null;
        lastMorphName = "";
        lastAcquiredCount = -1;
    }

    /**
     * 每个 client tick 调用：轮询当前 morph 与已获得 morph 数量，
     * 变化时刷新 + 同步授予能力。
     *
     * 为什么需要轮询：Metamorph 的 MorphEvent/AcquireMorphEvent 可能在集成服务器线程
     * （服务端）触发，客户端 @SubscribeEvent 收不到——不开 UI 时被动能力就永远不同步。
     */
    public static void tick() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        IMorphing morphing = Morphing.get(mc.player);
        if (morphing == null) {
            return;
        }
        mchorse.metamorph.api.morphs.AbstractMorph current = morphing.getCurrentMorph();
        String name = current == null || current.name == null ? "" : current.name;
        int acquired = morphing.getAcquiredMorphs() == null ? 0 : morphing.getAcquiredMorphs().size();
        if (mc.player != lastPlayer || !name.equals(lastMorphName) || acquired != lastAcquiredCount) {
            lastPlayer = mc.player;
            lastMorphName = name;
            lastAcquiredCount = acquired;
            refresh();
            syncGranted();
        }
    }

    /** 变回原形。 */
    public static class DemorphAbility extends Ability {
        public DemorphAbility() {
            super(DEMORPH_ID, "Demorph");
            this.icon(new net.minecraft.item.ItemStack(net.minecraft.init.Items.SLIME_BALL));
        }

        @Override
        public aaaa0ggmc.morphext.ability.ExecutionSide getExecutionSide() {
            return aaaa0ggmc.morphext.ability.ExecutionSide.SERVER;
        }

        @Override
        public void onActivate(EntityPlayer player) {
            if (player != null && player.world.isRemote) {
                aaaa0ggmc.morphext.client.ClientAbilityExecutor.activate(this);
            }
        }

        @Override
        public String getDescription() {
            return "解除变身。立即恢复玩家原形态，同时移除该 morph 授予的全部能力";
        }
    }

    /** 单个 morph 能力：激活时切到该 morph。 */
    public static class MorphAbility extends Ability {
        private final String morphName;
        private final String displayName;
        private final net.minecraft.item.ItemStack icon;

        @Override
        public aaaa0ggmc.morphext.ability.ExecutionSide getExecutionSide() {
            return aaaa0ggmc.morphext.ability.ExecutionSide.SERVER;
        }

        public MorphAbility(mchorse.metamorph.api.morphs.AbstractMorph morph) {
            super(MORPH_PREFIX + morph.name, morph.getDisplayName());
            this.morphName = morph.name;
            this.displayName = "Morph: " + morph.getDisplayName();
            this.icon = MorphIcons.forMorph(morph.name);
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public net.minecraft.item.ItemStack getIcon() {
            return this.icon;
        }

        @Override
        public String getDescription() {
            return "变身为 " + this.morphName + "：需先在 Metamorph 中获得该 morph。"
                    + "变身期间获得该 morph 配置的全部能力（含被动与可释放的 mob act）。";
        }

        @Override
        public void onActivate(EntityPlayer player) {
            if (player != null && player.world.isRemote) {
                aaaa0ggmc.morphext.client.ClientAbilityExecutor.activate(this);
            }
        }
    }
}

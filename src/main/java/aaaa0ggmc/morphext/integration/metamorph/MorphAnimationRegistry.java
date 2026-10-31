package aaaa0ggmc.morphext.integration.metamorph;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用形态动画触发决策中心 (Morph Animation Registry)。
 *
 * 解决由 actions 引发模型/形态转换时是否触发 Metamorph 变身过渡动画的问题。
 * 遵循非硬编码设计原则：
 * 1. 优先级 1：外部数据驱动（morph_abilities.json 中的 actionAnimation / silentActions / animateSameMorph）。
 * 2. 优先级 2：外部模组通过 Java DI 显式注册的 Action 规则或 Transition 规则。
 * 3. 优先级 3：Action 类自身实现的 IMorphAnimationAction 接口。
 * 4. 优先级 4：同生物形态内部状态微调（如羊吃草长毛、羊剪毛等）默认平滑更替模型，不触发 20-tick 翻转与爆炸粒子。
 */
public final class MorphAnimationRegistry {
    private static final Logger LOGGER = LogManager.getLogger("morphext-anim-registry");

    // Action ID -> 是否触发动画 (true = 强制触发动画, false = 静默平滑切换)
    private static final Map<String, Boolean> ACTION_ANIMATION_RULES = new ConcurrentHashMap<>();
    // 静默 Action ID 集合
    private static final Set<String> SILENT_ACTIONS = ConcurrentHashMap.newKeySet();

    // 线程上下文：正在执行的主动动作 ID
    private static final ThreadLocal<String> CURRENT_ACTION_ID = new ThreadLocal<>();
    // 线程上下文：网络发包或局部执行指定静默同步标记
    private static final ThreadLocal<Boolean> SILENT_SYNC_CONTEXT = new ThreadLocal<>();

    // 默认同形态变种/状态微调是否触发动画 (默认 false = 平滑静默更替)
    private static volatile boolean defaultSameMorphAnimation = false;

    static {
        // 内置默认静默动作（羊吃草长毛、羊自主剪毛等状态微调，默认平滑过渡不翻跟头）
        registerSilentAction("sheep_graze");
        registerSilentAction("sheep_shear");
    }

    private MorphAnimationRegistry() {}

    /**
     * DI 注册：指定 Action ID 是否触发变身动画
     */
    public static void registerActionAnimation(String actionId, boolean animate) {
        if (actionId == null || actionId.trim().isEmpty()) return;
        String norm = actionId.trim().toLowerCase();
        ACTION_ANIMATION_RULES.put(norm, animate);
        if (norm.contains(":")) {
            ACTION_ANIMATION_RULES.put(norm.substring(norm.indexOf(':') + 1), animate);
        } else {
            ACTION_ANIMATION_RULES.put("morphext:" + norm, animate);
        }
        if (!animate) {
            SILENT_ACTIONS.add(norm);
        } else {
            SILENT_ACTIONS.remove(norm);
        }
    }

    /**
     * DI 注册：指定 Action ID 为静默动作（不触发翻转爆炸动画）
     */
    public static void registerSilentAction(String actionId) {
        registerActionAnimation(actionId, false);
    }

    /**
     * 设置正在执行的动作上下文
     */
    public static void pushActionContext(String actionId) {
        CURRENT_ACTION_ID.set(actionId);
    }

    public static void popActionContext() {
        CURRENT_ACTION_ID.remove();
    }

    public static String getCurrentActionId() {
        return CURRENT_ACTION_ID.get();
    }

    /**
     * 设置客户端/服务端局部静默更新标记
     */
    public static void setSilentSyncContext(boolean silent) {
        if (silent) {
            SILENT_SYNC_CONTEXT.set(Boolean.TRUE);
        } else {
            SILENT_SYNC_CONTEXT.remove();
        }
    }

    public static boolean isSilentSyncContext() {
        return Boolean.TRUE.equals(SILENT_SYNC_CONTEXT.get());
    }

    /**
     * 核心决策入口：判断由当前上下文、动作及新旧形态引发的模型转换是否应当抑制 (Suppress) 变身动画。
     *
     * @return true 表示应当抑制动画（平滑更替模型，不播放 20-tick 翻转与粒子）；false 表示正常播放动画
     */
    public static boolean shouldSuppressAnimation(AbstractMorph from, AbstractMorph to) {
        // 1. 显式静默同步上下文 (来自网络静默数据包或代码强制标记)
        if (isSilentSyncContext()) {
            return true;
        }

        // 2. 检查当前正在执行的 Action 上下文
        String currentAction = CURRENT_ACTION_ID.get();
        if (currentAction != null) {
            String norm = currentAction.toLowerCase();
            // A. 检查 JSON 中当前形态是否有配置
            if (from != null) {
                MorphAbilityDefinition def = MorphJsonConfig.get(from.name);
                if (def != null) {
                    if (def.actionAnimation.containsKey(norm)) {
                        return !def.actionAnimation.get(norm);
                    }
                    if (def.silentActions.contains(norm)) {
                        return true;
                    }
                }
            }
            if (to != null) {
                MorphAbilityDefinition def = MorphJsonConfig.get(to.name);
                if (def != null) {
                    if (def.actionAnimation.containsKey(norm)) {
                        return !def.actionAnimation.get(norm);
                    }
                    if (def.silentActions.contains(norm)) {
                        return true;
                    }
                }
            }

            // B. 检查 Java DI 显式注册规则
            if (ACTION_ANIMATION_RULES.containsKey(norm)) {
                return !ACTION_ANIMATION_RULES.get(norm);
            }
            if (SILENT_ACTIONS.contains(norm)) {
                return true;
            }
        }

        // 3. 检查同生物形态微调策略 (如 minecraft:sheep -> minecraft:sheep，仅毛色或状态变更)
        if (from != null && to != null && from.name != null && from.name.equalsIgnoreCase(to.name)) {
            MorphAbilityDefinition def = MorphJsonConfig.get(from.name);
            if (def != null && def.animateSameMorph != null) {
                return !def.animateSameMorph;
            }
            return !defaultSameMorphAnimation;
        }

        return false;
    }

    /**
     * 辅助判定：判断由指定 action 触发的转换是否应当播放动画
     */
    public static boolean shouldAnimateForAction(EntityLivingBase entity, String actionId, Object actionObj, AbstractMorph from, AbstractMorph to) {
        if (actionId != null) {
            String norm = actionId.toLowerCase();
            if (from != null) {
                MorphAbilityDefinition def = MorphJsonConfig.get(from.name);
                if (def != null) {
                    if (def.actionAnimation.containsKey(norm)) {
                        return def.actionAnimation.get(norm);
                    }
                    if (def.silentActions.contains(norm)) {
                        return false;
                    }
                }
            }
            if (ACTION_ANIMATION_RULES.containsKey(norm)) {
                return ACTION_ANIMATION_RULES.get(norm);
            }
            if (SILENT_ACTIONS.contains(norm)) {
                return false;
            }
        }

        if (actionObj instanceof IMorphAnimationAction) {
            return ((IMorphAnimationAction) actionObj).shouldAnimateMorphTransition(entity, from, to);
        }

        // 默认如果形态相同则静默
        if (from != null && to != null && from.name != null && from.name.equalsIgnoreCase(to.name)) {
            return false;
        }

        return true;
    }
}

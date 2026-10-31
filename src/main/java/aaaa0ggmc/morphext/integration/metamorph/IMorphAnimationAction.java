package aaaa0ggmc.morphext.integration.metamorph;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;

/**
 * 动作形态动画控制接口 (Morph Animation Action Controller)。
 *
 * 当动作在执行过程中触发了模型/形态转换（例如羊剪毛、羊吃草、潜影贝开闭壳、狼变身等），
 * 实现此接口可声明该动作是否希望触发 Metamorph 的 20-tick 翻转与爆炸粒子动画。
 *
 * 外部 JSON（morph_abilities.json）中配置的 actionAnimation / silentActions 拥有最高优先级覆盖权。
 */
public interface IMorphAnimationAction {

    /**
     * 决定由该动作触发的形态转换是否播放 Metamorph 变身过渡动画。
     *
     * @param entity 执行动作的主体实体
     * @param from   转换前的形态
     * @param to     转换后的形态
     * @return true 播放全套过渡动画；false 平滑静默更替模型
     */
    default boolean shouldAnimateMorphTransition(EntityLivingBase entity, AbstractMorph from, AbstractMorph to) {
        return false;
    }
}

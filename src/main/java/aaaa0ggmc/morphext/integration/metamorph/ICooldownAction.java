package aaaa0ggmc.morphext.integration.metamorph;

import net.minecraft.entity.EntityLivingBase;

/**
 * 具有固有冷却时间的主动动作接口。
 * 当动作实现此接口时，无需依赖外部药水效果，由 morphext 统一执行并同步权威冷却。
 */
public interface ICooldownAction {
    /**
     * 获取施法者的基础冷却刻数（20 ticks = 1 秒）。
     *
     * @param entity 施法实体
     * @return 冷却刻数
     */
    int getCooldownTicks(EntityLivingBase entity);
}

package aaaa0ggmc.morphext.ability;

/**
 * 能力类型枚举：
 * - ACTIVE: 主动技能（可绑定到轮盘/按键释放，进入冷却）
 * - PASSIVE: 被动能力（常驻生效，挂载于变身形态或玩家自身，响应各类事件钩子）
 * - TOGGLE: 开关姿态（可激活切换开启/关闭状态）
 */
public enum AbilityType {
    ACTIVE,
    PASSIVE,
    TOGGLE
}

package aaaa0ggmc.morphext;

/**
 * 代码常量集中处。
 *
 * 只放「数值/间隔/优先级」这类可调参数；行为与数据驱动的规则一律走
 * {@code assets/&lt;modid&gt;/morphext/*.json} 或 {@code config/morphext/*.json}。
 * 每条常量用 {@code [TUNING]} 标记，便于检索与整合包侧微调。
 */
public final class MorphExtTuning {
    /** [TUNING] 技能授予检查间隔（tick）。20 = 1 秒。 */
    public static final int SKILL_GRANT_CHECK_INTERVAL_TICKS = 20;

    /** [TUNING] 随从与主人距离超过该值（格）时开始寻路跟随。 */
    public static final double FOLLOWER_START_DISTANCE = 6.0D;
    /** [TUNING] 距离小于该值（格）时停止寻路，待在主人身旁。 */
    public static final double FOLLOWER_STOP_DISTANCE = 2.0D;
    /** [TUNING] 距离超过该值（格）且无法寻路时直接传送到主人身旁。 */
    public static final double FOLLOWER_TELEPORT_DISTANCE = 16.0D;
    /** [TUNING] 传送落点与主人保持的水平间距（格）。 */
    public static final double FOLLOWER_TELEPORT_OFFSET = 1.5D;
    /** [TUNING] 随从寻路移动速度倍率（原版 1.0 约等于行走速度）。 */
    public static final double FOLLOWER_MOVE_SPEED = 1.2D;
    /** [TUNING] 随从 AI 任务优先级（小者先执行，需低于模组自带的核心 AI）。 */
    public static final int FOLLOWER_AI_PRIORITY = 6;

    private MorphExtTuning() {
    }
}

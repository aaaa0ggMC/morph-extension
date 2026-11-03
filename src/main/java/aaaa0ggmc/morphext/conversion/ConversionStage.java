package aaaa0ggmc.morphext.conversion;

/**
 * 死亡转化管线的一个阶段。
 *
 * 典型阶段：感染（安卡护符）、转化规则（数据驱动仪式）、被动转化（诅咒）。
 * 优先级小的先执行；返回 true 表示「本次死亡已被我处理」，
 * 管线立即停止，后续阶段不再介入。
 *
 * 注册：{@code ConversionPipeline.register(new MyStage())}（模组 preInit/init 期）。
 */
public interface ConversionStage {
    /** 小的先执行。约定：感染 10 &lt; 转化规则 20 &lt; 被动转化 30。 */
    int priority();

    /**
     * @param ctx 死亡上下文
     * @return true = 已处理（死亡应被取消或已被转化接住）
     */
    boolean handle(DeathContext ctx);
}

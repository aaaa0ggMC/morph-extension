package aaaa0ggmc.morphext.integration.metamorph;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 服务端主动形态动作（morph action）的执行上下文。
 *
 * <p>不少主动动作（包括第三方 patch）使用没有 owner 的静态 {@code DamageSource}
 * 造成伤害。原版 {@code attackEntityFrom} 无法从中得到玩家，于是受害者不会把玩家
 * 记录为 revenge target，阵营系统也就无法判断“是玩家先动的手”。</p>
 *
 * <p>{@code MetamorphServerAbilities} 在执行动作期间把服务端玩家放入本线程上下文，
 * {@code FactionManager} 只在“目标生物受伤且伤害来源无法归属到玩家”时才使用它。
 * 这是通用机制，不包含任何具体模组判断。</p>
 */
public final class MorphActionContext {
    private static final ThreadLocal<EntityPlayerMP> CURRENT_ACTOR = new ThreadLocal<>();

    private MorphActionContext() {
    }

    /** 推入当前动作执行者，返回之前的值以便嵌套动作恢复。 */
    public static EntityPlayerMP push(EntityPlayerMP player) {
        EntityPlayerMP previous = CURRENT_ACTOR.get();
        CURRENT_ACTOR.set(player);
        return previous;
    }

    public static void restore(EntityPlayerMP previous) {
        if (previous == null) {
            CURRENT_ACTOR.remove();
        } else {
            CURRENT_ACTOR.set(previous);
        }
    }

    /** 当前线程正在执行主动动作的玩家；不在动作上下文中时返回 null。 */
    public static EntityPlayerMP get() {
        return CURRENT_ACTOR.get();
    }
}

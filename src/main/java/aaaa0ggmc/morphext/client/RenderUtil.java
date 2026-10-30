package aaaa0ggmc.morphext.client;

import org.lwjgl.opengl.GL11;

/**
 * 渲染小工具。
 *
 * 为什么需要 resetBlendFunc：1.12.2 里 {@code renderItemIntoGUI} 等物品渲染路径
 * （OptiFine 尤其明显）会把 GL 混合函数留在加性混合之类的非标准状态，而
 * {@code GlStateManager} 的状态缓存会让 {@code Gui.drawRect} 里的
 * {@code tryBlendFuncSeparate} 误以为已经是标准混合而跳过实际 GL 调用——
 * 结果是之后所有半透明矩形（面板底色、行底色、滚动条）全部失效，看起来“穿了”。
 * 这里用裸 GL 调用强制设回标准 src-alpha 混合，绕过状态缓存。
 */
public final class RenderUtil {
    private RenderUtil() {
    }

    /** 强制把 GL 混合函数设回标准 src-alpha 混合。 */
    public static void resetBlendFunc() {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}

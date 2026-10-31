package aaaa0ggmc.morphext.client.render;

import aaaa0ggmc.morphext.client.RenderUtil;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metamorph 形态缩略图离屏烘焙与 LRU 缓存系统。
 *
 * 核心优化效果：
 * 1. 将界面上满屏（几十到上百个）静止模型的 3D 实时实体渲染，降低为单次 2D Quad 贴图绘制；
 * 2. 交互无缝回退：鼠标悬停 (hover) 或选中 (selected) 格子自动回退为实时 60fps 3D 渲染；
 * 3. 分帧烘焙预算（Budgeting）：每帧最多生成 2 个缩略图，杜绝首次打开 UI 时的尖刺卡顿；
 * 4. 纯 GPU 显存流转 (glCopyTexImage2D)，完全不消耗主内存，显存占用极小；
 * 5. 严格的 OpenGL / Mesa zink 状态隔离保护（保护 Viewport、Scissor、Framebuffer 绑定）。
 */
@SideOnly(Side.CLIENT)
public final class MorphThumbnailCache {
    private static final int THUMBNAIL_SIZE = 128;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final int BUDGET_PER_FRAME = 2;

    private static Framebuffer fbo = null;
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);

    private static final Map<String, ThumbnailEntry> CACHE =
            new LinkedHashMap<String, ThumbnailEntry>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ThumbnailEntry> eldest) {
                    if (size() > MAX_CACHE_ENTRIES) {
                        eldest.getValue().destroy();
                        return true;
                    }
                    return false;
                }
            };

    private static int bakedThisFrame = 0;

    private MorphThumbnailCache() {
    }

    private static class ThumbnailEntry {
        final int textureId;
        long lastAccessed;

        ThumbnailEntry(int textureId) {
            this.textureId = textureId;
            this.lastAccessed = System.currentTimeMillis();
        }

        void destroy() {
            if (this.textureId > 0) {
                GlStateManager.deleteTexture(this.textureId);
            }
        }
    }

    /**
     * 生成形态在缓存中的唯一特征 Key
     */
    public static String getMorphKey(AbstractMorph morph) {
        if (morph == null) return "null";
        return morph.name + "@" + morph.hashCode() + (morph.favorite ? "_fav" : "");
    }

    /**
     * 检查当前帧是否能继续烘焙
     */
    private static boolean canBakeThisFrame() {
        return bakedThisFrame < BUDGET_PER_FRAME;
    }

    public static void beginFrame() {
        bakedThisFrame = 0;
    }

    /**
     * 确保烘焙 FBO 已就绪
     */
    private static void ensureFbo() {
        if (fbo == null) {
            fbo = new Framebuffer(THUMBNAIL_SIZE, THUMBNAIL_SIZE, true);
            fbo.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    /**
     * 离屏烘焙形态为 2D 贴图
     */
    private static ThumbnailEntry bakeThumbnail(AbstractMorph morph, Minecraft mc, int width, int height) {
        if (!OpenGlHelper.isFramebufferEnabled() || morph == null || morph.errorRendering || mc.player == null) {
            return null;
        }

        // 1. 保存当前 OpenGL / zink 渲染上下文状态
        VIEWPORT_BUFFER.rewind();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
        int prevVx = VIEWPORT_BUFFER.get(0);
        int prevVy = VIEWPORT_BUFFER.get(1);
        int prevVw = VIEWPORT_BUFFER.get(2);
        int prevVh = VIEWPORT_BUFFER.get(3);

        int prevFbo = 0;
        if (OpenGlHelper.isFramebufferEnabled()) {
            prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        }
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int textureId = 0;

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        // The preview coordinates are local to this FBO, not the game window.
        GlStateManager.ortho(0, width, height, 0, 1000, 3000);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0, 0, -2000);
        try {
            // Framebuffer construction changes GL bindings: snapshot BEFORE creation.
            ensureFbo();
            // 2. 绑定烘焙 FBO 并设置独立视口，禁用 scissor 干扰
            fbo.bindFramebuffer(true);
            if (scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            GlStateManager.depthMask(true);
            GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);

            // 3. 在 FBO 内部安全渲染形态实体
            if (!SafeMorphRenderer.renderOnScreen(
                    morph,
                    mc.player,
                    width / 2,
                    (int) (height * 0.7F),
                    width * 0.4F,
                    1.0F
            )) {
                return null;
            }

            // 4. 将 FBO 的渲染产物通过纯 GPU 方式拷贝为独立纹理
            textureId = GlStateManager.generateTexture();
            GlStateManager.bindTexture(textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

            // 纯 GPU 显存拷贝，不产生 CPU 内存分配
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0);

        } catch (Throwable t) {
            t.printStackTrace();
            if (textureId > 0) {
                GlStateManager.deleteTexture(textureId);
                textureId = 0;
            }
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(matrixMode);
            GlStateManager.depthMask(depthMask);
            GlStateManager.depthFunc(depthFunc);
            if (depthEnabled) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            GlStateManager.bindTexture(texture);
            GlStateManager.color(1, 1, 1, 1);
            // Restore the caller's framebuffer, including shader render targets.
            if (OpenGlHelper.isFramebufferEnabled()) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
            }
            GlStateManager.viewport(prevVx, prevVy, prevVw, prevVh);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderUtil.resetBlendFunc();
        }

        if (textureId > 0) {
            return new ThumbnailEntry(textureId);
        }
        return null;
    }

    /**
     * 尝试绘制缓存的 2D 缩略图
     * @return 若成功绘制返回 true；若尚未生成返回 false
     */
    public static boolean renderCachedThumbnail(AbstractMorph morph, int x, int y, int w, int h, Minecraft mc) {
        if (morph == null) return false;

        String key = getMorphKey(morph) + "@" + w + "x" + h;
        ThumbnailEntry entry = CACHE.get(key);

        if (entry == null) {
            if (canBakeThisFrame()) {
                bakedThisFrame++;
                entry = bakeThumbnail(morph, mc, w, h);
                if (entry != null) {
                    CACHE.put(key, entry);
                }
            }
        }

        if (entry != null) {
            entry.lastAccessed = System.currentTimeMillis();
            drawTexturedQuad(entry.textureId, x, y, w, h);
            return true;
        }

        return false;
    }

    /**
     * 绘制 2D 纹理 Quad（注意 FBO 贴图上下翻转）
     */
    private static void drawTexturedQuad(int textureId, int x, int y, int w, int h) {
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        RenderUtil.resetBlendFunc();
        GlStateManager.bindTexture(textureId);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        // FBO 纹理 Y 轴在 OpenGL 中原点在左下角，V 坐标从 1 到 0
        buffer.pos(x, y + h, 0).tex(0.0, 0.0).endVertex();
        buffer.pos(x + w, y + h, 0).tex(1.0, 0.0).endVertex();
        buffer.pos(x + w, y, 0).tex(1.0, 1.0).endVertex();
        buffer.pos(x, y, 0).tex(0.0, 1.0).endVertex();

        tessellator.draw();
        if (depthEnabled) GlStateManager.enableDepth();
    }

    /**
     * 综合形态格子绘制代理：
     * 悬停/选中状态执行高精度实时 3D 渲染；普通状态使用 2D 缩略图，大幅消除帧开销。
     */
    public static boolean drawMorphSmart(AbstractMorph morph, Minecraft mc, int x, int y, int w, int h, boolean hover, boolean selected) {
        if (morph == null || morph.errorRendering) {
            return false;
        }

        // 交互态：悬停、选中时保持 60fps 实时 3D 交互
        if (hover || selected) {
            return SafeMorphRenderer.renderOnScreen(
                    morph,
                    mc.player,
                    x + w / 2,
                    y + (int) (h * 0.7F),
                    w * 0.4F,
                    1.0F
            );
        }

        // 静态普通格子：优先走 2D 缩略图贴图
        if (renderCachedThumbnail(morph, x, y, w, h, mc)) {
            return true;
        }

        // Unsupported FBOs and failed/pending cache entries must never hide a morph.
        return SafeMorphRenderer.renderOnScreen(morph, mc.player,
                x + w / 2, y + (int) (h * 0.7F), w * 0.4F, 1.0F);
    }

    /**
     * 清理所有缓存纹理并释放显存
     */
    public static synchronized void clearCache() {
        for (ThumbnailEntry entry : CACHE.values()) {
            entry.destroy();
        }
        CACHE.clear();
        if (fbo != null) {
            fbo.deleteFramebuffer();
            fbo = null;
        }
    }
}

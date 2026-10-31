package aaaa0ggmc.morphext.client.render;

import aaaa0ggmc.morphext.client.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本块离屏贴图缓存 (Cached Text Block)。
 *
 * 核心优化效果：
 * 解决 Minecraft 1.12.2 Unicode 中文字体在跨纹理页时频繁打断 BufferBuilder、
 * 反复 bindTexture 以及阴影二次绘制导致的严重 GPU/驱动层管线开销。
 *
 * 将静态或低频变动的长文本块在内容改变时一次性光栅化烘焙到离屏纹理，
 * 后续每一帧只需单次 2D 贴图贴出，将成百上千次 Draw Call 压缩为 1 次。
 */
@SideOnly(Side.CLIENT)
public final class CachedTextBlock {
    private static final int MAX_BLOCKS = 64;
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);

    private static final Map<String, CachedBlockEntry> CACHE =
            new LinkedHashMap<String, CachedBlockEntry>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedBlockEntry> eldest) {
                    if (size() > MAX_BLOCKS) {
                        eldest.getValue().destroy();
                        return true;
                    }
                    return false;
                }
            };

    private CachedTextBlock() {
    }

    public static class CachedBlockEntry {
        public final int textureId;
        public final int width;
        public final int height;
        public final int lineCount;
        public final List<String> lines;
        public long lastUsed;

        public CachedBlockEntry(int textureId, int width, int height, int lineCount, List<String> lines) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.lineCount = lineCount;
            this.lines = lines;
            this.lastUsed = System.currentTimeMillis();
        }

        public void destroy() {
            if (this.textureId > 0) {
                GlStateManager.deleteTexture(this.textureId);
            }
        }
    }

    private static String getCacheKey(String text, int maxWidth, int color, boolean shadow) {
        return text + "@" + maxWidth + "_" + color + "_" + shadow;
    }

    /**
     * 获取或生成文本块缓存
     */
    public static CachedBlockEntry getOrCreate(FontRenderer font, String text, int maxWidth, int color, boolean shadow) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return null;
        }

        String key = getCacheKey(text, maxWidth, color, shadow);
        CachedBlockEntry entry = CACHE.get(key);
        if (entry != null) {
            entry.lastUsed = System.currentTimeMillis();
            return entry;
        }

        // 切行
        List<String> lines = font.listFormattedStringToWidth(text, maxWidth);
        if (lines == null || lines.isEmpty()) {
            lines = Collections.singletonList(text);
        }

        int lineHeight = font.FONT_HEIGHT + 1;
        int totalHeight = Math.max(16, lines.size() * lineHeight + 4);
        int totalWidth = Math.max(32, maxWidth);

        // 创建临时 Framebuffer 进行文字离屏渲染
        Framebuffer fbo = null;
        int textureId = 0;

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

        try {
            fbo = new Framebuffer(totalWidth, totalHeight, false);
            fbo.bindFramebuffer(true);

            if (scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, totalWidth, totalHeight, 0.0D, 1000.0D, 3000.0D);

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);

            GlStateManager.enableBlend();
            RenderUtil.resetBlendFunc();

            int ty = 2;
            for (String line : lines) {
                if (shadow) {
                    font.drawStringWithShadow(line, 2, ty, color);
                } else {
                    font.drawString(line, 2, ty, color);
                }
                ty += lineHeight;
            }

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            // 拷贝到独立静态纹理
            textureId = GlStateManager.generateTexture();
            GlStateManager.bindTexture(textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 0, 0, totalWidth, totalHeight, 0);

        } catch (Throwable t) {
            t.printStackTrace();
            if (textureId > 0) {
                GlStateManager.deleteTexture(textureId);
                textureId = 0;
            }
        } finally {
            if (fbo != null) {
                fbo.deleteFramebuffer();
            }
            if (OpenGlHelper.isFramebufferEnabled()) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
            }
            GlStateManager.viewport(prevVx, prevVy, prevVw, prevVh);
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            RenderUtil.resetBlendFunc();
        }

        if (textureId > 0) {
            entry = new CachedBlockEntry(textureId, totalWidth, totalHeight, lines.size(), lines);
            CACHE.put(key, entry);
            return entry;
        }

        return null;
    }

    /**
     * 快速将缓存好的文字贴图贴到界面上（仅消耗 1 次 2D Quad 绘制）
     */
    public static boolean draw(FontRenderer font, String text, int x, int y, int maxWidth, int color, boolean shadow) {
        CachedBlockEntry entry = getOrCreate(font, text, maxWidth, color, shadow);
        if (entry != null) {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            RenderUtil.resetBlendFunc();
            GlStateManager.bindTexture(entry.textureId);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

            int w = entry.width;
            int h = entry.height;
            // FBO 纹理 Y 轴翻转
            buffer.pos(x, y + h, 0).tex(0.0, 0.0).endVertex();
            buffer.pos(x + w, y + h, 0).tex(1.0, 0.0).endVertex();
            buffer.pos(x + w, y, 0).tex(1.0, 1.0).endVertex();
            buffer.pos(x, y, 0).tex(0.0, 1.0).endVertex();

            tessellator.draw();
            return true;
        }

        return false;
    }

    public static synchronized void clearCache() {
        for (CachedBlockEntry entry : CACHE.values()) {
            entry.destroy();
        }
        CACHE.clear();
    }
}

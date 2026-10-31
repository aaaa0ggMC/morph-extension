package aaaa0ggmc.morphext.client.render;

import aaaa0ggmc.morphext.client.RenderSafetyHandler;
import aaaa0ggmc.morphext.client.RenderUtil;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * 加固版形态渲染桥接工具。
 *
 * 核心优化：
 * 1. 根治 Metamorph MorphUtils.renderOnScreen/renderDirect 每帧无条件调用
 *    BufferBuilder.finishDrawing() 导致的成千上万次 IllegalStateException 异常风暴。
 * 2. 结合 RenderSafetyHandler 阻断 NaN、除以 0、损坏 dummy 实体的状态机击穿。
 */
@SideOnly(Side.CLIENT)
public final class SafeMorphRenderer {
    private static final MethodHandle IS_DRAWING_GETTER;

    static {
        MethodHandle mh = null;
        try {
            Field f = ReflectionHelper.findField(BufferBuilder.class, "isDrawing", "field_179010_r");
            f.setAccessible(true);
            mh = MethodHandles.lookup().unreflectGetter(f);
        } catch (Throwable ignored) {
        }
        IS_DRAWING_GETTER = mh;
    }

    private SafeMorphRenderer() {
    }

    /**
     * 安全检查 BufferBuilder 是否仍在 drawing，仅在异常未闭合时才执行收尾，
     * 杜绝正常绘制时频繁抛出和捕获 "Not building!" 异常。
     */
    public static void safeFinishDrawing(AbstractMorph morph) {
        try {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            boolean drawing = false;
            if (IS_DRAWING_GETTER != null) {
                try {
                    drawing = (boolean) IS_DRAWING_GETTER.invokeExact(buffer);
                } catch (Throwable ignored) {
                }
            }
            if (drawing) {
                buffer.finishDrawing();
                System.err.println("[morphext] Recovered unfinished buffer from morph: "
                        + (morph == null ? "null" : morph.getClass().getName()));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 安全在屏幕/GUI 上绘制形态
     */
    public static boolean renderOnScreen(AbstractMorph morph, EntityPlayer player, int x, int y, float scale, float alpha) {
        if (morph == null || morph.errorRendering) {
            return false;
        }

        if (!RenderSafetyHandler.sanitizeEntityMorph(morph)) {
            return false;
        }

        try {
            MorphUtils.isRenderingOnScreen = true;
            morph.renderOnScreen(player, x, y, scale, alpha);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            morph.errorRendering = true;
        } finally {
            MorphUtils.isRenderingOnScreen = false;
            safeFinishDrawing(morph);
            RenderUtil.resetBlendFunc();
        }

        return false;
    }

    /**
     * 安全在世界实体上绘制形态
     */
    public static boolean renderDirect(AbstractMorph morph, EntityLivingBase entity, double x, double y, double z, float yaw, float partialTick) {
        if (morph == null || morph.errorRendering) {
            return false;
        }

        if (!RenderSafetyHandler.sanitizeEntityMorph(morph)) {
            return false;
        }

        try {
            morph.render(entity, x, y, z, yaw, partialTick);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            morph.errorRendering = true;
        } finally {
            safeFinishDrawing(morph);
        }

        return false;
    }
}

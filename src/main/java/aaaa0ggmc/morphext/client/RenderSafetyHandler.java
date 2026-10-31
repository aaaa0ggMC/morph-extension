package aaaa0ggmc.morphext.client;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Metamorph 客户端渲染安全加固网 (Render Safety Handler)。
 *
 * 核心防护目标：
 * 1. 规避 EntityMorph 缩放除以 0 导致 scale 变为 Infinity 或 NaN，进而击穿 LWJGL / OpenGL 矩阵状态机；
 * 2. 对 dummy 实体在没有完整客户端数据（如坐标为 NaN、无世界连接）时提供安全保护默认值；
 * 3. 拦截因第三方 Mod 生物模型在 GUI 环境下渲染抛出的异常，防止其直接击垮 Minecraft 客户端主线程。
 */
@SideOnly(Side.CLIENT)
public final class RenderSafetyHandler {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static boolean registered = false;

    private RenderSafetyHandler() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new RenderSafetyHandler());
        LOGGER.info("[morphext] RenderSafetyHandler registered for client rendering protection");
    }

    /**
     * 在渲染 EntityMorph 前执行安全尺寸与状态加固
     * @param morph 待渲染的形态
     * @return 若形态安全可渲染返回 true，若严重损坏返回 false
     */
    public static boolean sanitizeEntityMorph(AbstractMorph morph) {
        if (morph == null || morph.errorRendering) {
            return false;
        }

        if (morph instanceof EntityMorph) {
            EntityMorph em = (EntityMorph) morph;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.world == null) {
                return true;
            }

            try {
                EntityLivingBase dummy = em.getEntity(mc.world);
                if (dummy == null) {
                    return false;
                }

                // 1. 防御除以 0：若实体高度小于等于 0.01F，修正为一个合理的默认值
                if (dummy.height <= 0.01F || Float.isNaN(dummy.height) || Float.isInfinite(dummy.height)) {
                    dummy.height = 0.5F;
                }
                if (dummy.width <= 0.01F || Float.isNaN(dummy.width) || Float.isInfinite(dummy.width)) {
                    dummy.width = 0.5F;
                }

                // 2. 防御坐标 NaN
                if (Double.isNaN(dummy.posX) || Double.isNaN(dummy.posY) || Double.isNaN(dummy.posZ)) {
                    dummy.posX = 0.0D;
                    dummy.posY = 0.0D;
                    dummy.posZ = 0.0D;
                }
            } catch (Throwable t) {
                LOGGER.warn("[morphext] Failed to sanitize entity morph '{}', marking as error rendering to prevent crash: {}",
                        morph.name, t.getMessage());
                morph.errorRendering = true;
                return false;
            }
        }

        return true;
    }
}

package aaaa0ggmc.morphext.variant;

import aaaa0ggmc.morphext.ability.HumanStatureAbility;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 身形规整（Human Stature）客户端模型渲染与视线/碰撞箱拦截控制器。
 *
 * 1. 模式 1 (HUMAN_MODEL)：阻断怪兽模型渲染，放行原版人类玩家模型渲染。
 * 2. 模式 2 (SMART_SCALE)：在 OpenGL 矩阵中智能缩放怪兽模型的视觉体积，使其高度严格等身对齐人类身高（~1.8 格）。
 * 3. 模式 0 (OFF)：保持原生模型大小与渲染比例。
 */
@SideOnly(Side.CLIENT)
public final class HumanStatureRenderHandler {
    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new HumanStatureRenderHandler());
    }

    /**
     * 模式 1 (HUMAN_MODEL) & 模式 2 (SMART_SCALE) 模型渲染拦截：
     * - 模式 1：取消怪兽模型渲染；
     * - 模式 2：在 OpenGL 局部变换矩阵中等比缩放整个模型的视觉体积，使其高度缩放到 1.8 格高的人类等身体积。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderLivingPre(RenderLivingEvent.Pre<EntityLivingBase> event) {
        EntityLivingBase host = EntityMorph.renderEntity;
        if (host instanceof EntityPlayer) {
            HumanStatureAbility.Mode mode = HumanStatureAbility.getClientMode();
            if (mode == HumanStatureAbility.Mode.HUMAN_MODEL) {
                // 取消 Metamorph 的怪兽实体渲染
                event.setCanceled(true);
            } else if (mode == HumanStatureAbility.Mode.SMART_SCALE) {
                EntityLivingBase mob = event.getEntity();
                if (mob != null && mob.height > 0.05F) {
                    float targetHeight = 1.8F;
                    float scaleFactor = targetHeight / mob.height;
                    // 直接在 RenderLivingBase 的 Matrix 中缩放模型体积！
                    GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
                }
            }
        }
    }

    /**
     * 模式 1 (HUMAN_MODEL)：解取消原版 RenderPlayerEvent，放行原版玩家人类模型与皮肤渲染。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null) {
            return;
        }
        IMorphing morphing = Morphing.get(player);
        if (morphing == null || morphing.getCurrentMorph() == null) {
            return;
        }
        HumanStatureAbility.Mode mode = HumanStatureAbility.getClientMode();
        if (mode == HumanStatureAbility.Mode.HUMAN_MODEL && event.isCanceled()) {
            // 将被 Metamorph 取消的玩家模型渲染解取消，让原版 RenderPlayer 正常绘制玩家人类模型与皮肤
            event.setCanceled(false);
        }
    }

    /** Keep camera height consistent with the final tick's hitbox before camera setup.
     * Only the local player's camera is affected; OFF keeps Metamorph's POV policy.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START) return;
        aaaa0ggmc.morphext.client.render.MorphThumbnailCache.beginFrame();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.getRenderViewEntity() != player) return;
        IMorphing cap = Morphing.get(player);
        if (cap == null || cap.getCurrentMorph() == null) return;
        HumanStatureAbility.Mode mode = HumanStatureAbility.getClientMode();
        if (mode != HumanStatureAbility.Mode.OFF) {
            // 渲染帧节奏同步钉住碰撞箱与视线高度（与 PlayerTickEvent.END 的每 tick 写入同值），
            // 防止 Metamorph 每 tick 写入的形态实体尺寸在帧间泄漏进相机/碰撞。
            aaaa0ggmc.morphext.variant.VariantEventListener.pinStatureBox(player);
        } else if (!mchorse.metamorph.Metamorph.disablePov.get()) {
            mchorse.metamorph.api.morphs.AbstractMorph morph = cap.getCurrentMorph();
            float factor = morph.hitbox.enabled ? morph.hitbox.eye : 0.9F;
            float height = Math.max(0.2F, player.height);
            float eye = Math.min(height * factor, height - 0.1F);
            if (Float.isFinite(eye) && eye > 0) player.eyeHeight = eye;
        }
    }

    private HumanStatureRenderHandler() { }
}

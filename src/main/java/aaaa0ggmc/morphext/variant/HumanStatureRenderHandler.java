package aaaa0ggmc.morphext.variant;

import aaaa0ggmc.morphext.ability.HumanStatureAbility;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.Minecraft;
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
     * 模式 1 (HUMAN_MODEL)：阻止 Metamorph 渲染怪兽模型。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderLivingPre(RenderLivingEvent.Pre<EntityLivingBase> event) {
        EntityLivingBase host = EntityMorph.renderEntity;
        if (host instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) host;
            HumanStatureAbility.Mode mode = HumanStatureAbility.getClientMode();
            if (mode == HumanStatureAbility.Mode.HUMAN_MODEL) {
                // 取消 Metamorph 的怪兽实体渲染
                event.setCanceled(true);
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
            // 将被 Metamorph 取消的玩家模型渲染解取消，让原版 RenderPlayer 正常绘制玩家模型
            event.setCanceled(false);
        }
    }

    /**
     * 模式 2 (SMART_SCALE)：在渲染前智能调整当前 EntityMorph 的 scale，使其高度与人类一致 (~1.8 格高)。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMorphRenderSmartScale(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null) {
            return;
        }
        IMorphing morphing = Morphing.get(player);
        if (morphing == null) {
            return;
        }
        AbstractMorph current = morphing.getCurrentMorph();
        if (!(current instanceof EntityMorph)) {
            return;
        }
        EntityMorph em = (EntityMorph) current;
        HumanStatureAbility.Mode mode = HumanStatureAbility.getClientMode();

        if (mode == HumanStatureAbility.Mode.SMART_SCALE) {
            EntityLivingBase mob = em.getEntity(player.world);
            float origH = mob != null ? mob.height : current.getHeight(player);
            if (origH > 0.05F) {
                // 目标高度 1.8 格
                em.scale = 1.8F / origH;
            }
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import mchorse.metamorph.api.events.AcquireMorphEvent;
import mchorse.metamorph.api.events.MorphEvent;
import mchorse.metamorph.api.events.ReloadMorphs;

/**
 * Metamorph 事件订阅：morph 列表变化时刷新能力注册表。
 * 仅当 Metamorph 存在时才会被加载（由 MetamorphIntegration.init 控制注册）。
 */
public final class MetamorphEventHandler {
    private MetamorphEventHandler() {
    }

    public static void register() {
        if (Loader.isModLoaded("metamorph")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MetamorphEventHandler.class);
        }
    }

    @SubscribeEvent
    public static void onAcquire(AcquireMorphEvent.Post event) {
        scheduleRefresh();
    }

    @SubscribeEvent
    public static void onMorph(MorphEvent.Post event) {
        scheduleRefresh();
    }

    @SubscribeEvent
    public static void onReload(ReloadMorphs event) {
        scheduleRefresh();
    }

    private static void scheduleRefresh() {
        // 集成服务器上的事件也可能到这里；所有展示注册表修改必须转到客户端主线程。
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(MetamorphBridge::refresh);
    }
}

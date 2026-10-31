package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.config.ModConfig;
import mchorse.metamorph.api.events.AcquireMorphEvent;
import mchorse.metamorph.api.events.MorphEvent;
import mchorse.metamorph.api.events.SpawnGhostEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 接管并禁用生存模式下的 Metamorph 自主操作：
 * 1. 锁死 souls（禁止掉落与生成灵魂）；
 * 2. 禁止生存模式玩家主动吸收形态；
 * 3. 禁止生存模式玩家主动变形或变回（强制变形除外）；
 * 4. 客户端拦截 Metamorph 生存形态 GUI 打开。
 */
public final class MetamorphOverwriteHandler {
    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new MetamorphOverwriteHandler());
        }
    }

    /**
     * 锁死 souls：完全阻止灵魂生成
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpawnGhost(SpawnGhostEvent.Pre event) {
        if (ModConfig.overwriteMetamorph) {
            event.setCanceled(true);
        }
    }

    /**
     * 生存模式禁止主动吸收形态
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAcquireMorph(AcquireMorphEvent.Pre event) {
        if (ModConfig.overwriteMetamorph) {
            EntityPlayer player = event.player;
            if (player != null && !player.isCreative()) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 生存模式禁止玩家自主变形与解除变身（force 强制机制除外）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMorph(MorphEvent.Pre event) {
        if (ModConfig.overwriteMetamorph) {
            EntityPlayer player = event.player;
            if (player != null && !player.isCreative() && !event.force) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 客户端拦截：禁止在生存模式下打开 Metamorph 的形态选择 GUI
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent event) {
        if (!ModConfig.overwriteMetamorph) {
            return;
        }

        net.minecraft.client.gui.GuiScreen gui = event.getGui();
        if (gui == null) {
            aaaa0ggmc.morphext.client.render.MorphThumbnailCache.clearCache();
            return;
        }

        String className = gui.getClass().getName();
        // 匹配 Metamorph 生存菜单
        if (className.contains("metamorph") && className.contains("survival")) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.player != null && !mc.player.isCreative()) {
                event.setCanceled(true);
            }
        }
    }

    private MetamorphOverwriteHandler() {
    }
}

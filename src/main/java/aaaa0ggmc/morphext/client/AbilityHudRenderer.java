package aaaa0ggmc.morphext.client;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.ability.AbilityCooldowns;
import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import aaaa0ggmc.morphext.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.List;

/**
 * 左下角 HUD：显示当前选中的能力（图标 + 名称 [+ : 状态] + 附加信息）。
 * 状态每帧从能力读取，因此能力状态变化会实时反映（如 Sprint : On）。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = MorphExtension.MODID)
public class AbilityHudRenderer {
    private AbilityHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI) {
            return;
        }
        IAbility ability = AbilityRegistry.get(ModConfig.getActiveAbility());
        if (ability == null) {
            return;
        }
        boolean locked = AbilityUnlocks.isReady() && !AbilityUnlocks.isUnlocked(ability);

        ScaledResolution res = event.getResolution();
        FontRenderer font = mc.fontRenderer;

        boolean cooling = !locked && !AbilityCooldowns.isReady(ability.getId());
        String name = ability.getDisplayName();
        String suffix = "";
        if (locked) {
            suffix = " : " + I18n.translateToLocal("gui.morphext.hud.forgotten");
        } else if (cooling) {
            suffix = " : CD " + AbilityCooldowns.getRemainingText(ability.getId()) + "s";
        } else if (ability instanceof IStatefulAbility && ability.isStateToggleable()) {
            IStatefulAbility stateful = (IStatefulAbility) ability;
            suffix = " : " + stateful.getStateLabel(mc.player);
        }

        int textX = 24;
        int paddingRight = 6;
        int minBoxW = 80;

        // 计算当前锚点下的最大可用宽度，底部时严格防止触碰并遮挡原版快捷栏（Hotbar）
        int maxHudW = getMaxHudWidth(res, ModConfig.hudHorizontal, ModConfig.hudVertical, ModConfig.hudMargin);
        int maxTextW = maxHudW - textX - paddingRight;

        int suffixW = font.getStringWidth(suffix);
        int availableForName = Math.max(16, maxTextW - suffixW);

        String title;
        if (font.getStringWidth(name) > availableForName) {
            // 名字过长：精确截断并追加省略号，适配 UI
            String ellipsis = "…";
            int ellipsisW = font.getStringWidth(ellipsis);
            int targetW = Math.max(8, availableForName - ellipsisW);
            String trimmed = font.trimStringToWidth(name, targetW).trim();
            title = trimmed + ellipsis + suffix;
        } else {
            title = name + suffix;
        }

        int width = Math.min(maxHudW, Math.max(font.getStringWidth(title) + textX + paddingRight, minBoxW));
        int barH = cooling ? 4 : 0;
        int height = 20 + barH;
        int x = anchoredPosition(res.getScaledWidth(), width, ModConfig.hudHorizontal, ModConfig.hudMargin);
        int y = anchoredPosition(res.getScaledHeight(), height, ModConfig.hudVertical, ModConfig.hudMargin);

        Gui.drawRect(x, y, x + width, y + height, 0x66000000);

        ItemStack icon = ability.getIcon();
        if (!icon.isEmpty()) {
            /* 显式绑定方块图集：overlay 事件上下文中此前绑定的是字体图集，不绑会渲染出字形垃圾 */
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemIntoGUI(icon, x + 3, y + 3);
            RenderHelper.disableStandardItemLighting();
            /* 物品渲染后混合函数可能被留在非标准状态，强制复位（HUD 在帧中段渲染，不关深度，避免影响世界渲染） */
            aaaa0ggmc.morphext.client.RenderUtil.resetBlendFunc();
        }
        font.drawStringWithShadow(title, x + textX, y + 5,
                locked ? 0x999999 : cooling ? 0xFFAA66 : 0xFFFFFF);

        /* 冷却背景进度条：底栏，剩余比例填充 */
        if (cooling) {
            int barY = y + height - 3;
            Gui.drawRect(x, barY, x + width, barY + 3, 0xFF1A0505);
            int fillW = (int) (width * AbilityCooldowns.getRatio(ability.getId()));
            if (fillW > 0) {
                Gui.drawRect(x, barY, x + fillW, barY + 3, 0xFFCC4444);
            }
        }
    }

    private static int anchoredPosition(int screen, int size, int anchor, int margin) {
        int available = Math.max(0, screen - size);
        int position = anchor == 0 ? margin : anchor == 1 ? available / 2 : available - margin;
        return Math.max(0, Math.min(available, position));
    }

    /**
     * 计算 HUD 最大允许宽度，避免遮挡原版快捷栏（Hotbar）或超出屏幕
     */
    private static int getMaxHudWidth(ScaledResolution res, int anchorH, int anchorV, int margin) {
        int maxW = 120; // 默认精致紧凑宽度

        // 当位于底部（与快捷栏处于同一水平线）时，严格留出安全呼吸间距：
        if (anchorV == 2) {
            int hotbarLeft = res.getScaledWidth() / 2 - 91;
            int hotbarRight = res.getScaledWidth() / 2 + 91;

            if (anchorH == 0) {
                // 左下角：右边缘绝不触碰快捷栏左边缘（预留 4px 呼吸间距）
                int available = (hotbarLeft - margin) - 4;
                if (available > 40) {
                    maxW = Math.min(maxW, available);
                }
            } else if (anchorH == 2) {
                // 右下角：左边缘绝不触碰快捷栏右边缘
                int available = (res.getScaledWidth() - margin - hotbarRight) - 4;
                if (available > 40) {
                    maxW = Math.min(maxW, available);
                }
            }
        } else {
            maxW = Math.min(maxW, Math.max(80, res.getScaledWidth() / 2 - 32));
        }

        return Math.max(60, maxW);
    }
}

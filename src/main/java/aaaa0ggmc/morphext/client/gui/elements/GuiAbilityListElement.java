package aaaa0ggmc.morphext.client.gui.elements;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import aaaa0ggmc.morphext.client.ClientAbilityExecutor;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** McLib 框架里的可滚动能力列表（带搜索过滤），用于配置 UI 的能力选择器。 */
public class GuiAbilityListElement extends GuiElement {
    private final FontRenderer font;
    private final Consumer<IAbility> callback;
    private final List<IAbility> all;

    private String filter = "";
    private List<IAbility> filtered;
    private int scroll = 0;
    private int hovered = -1;

    private static final int ROW_HEIGHT = 18;

    public GuiAbilityListElement(Minecraft mc, Consumer<IAbility> callback) {
        super(mc);
        this.font = mc.fontRenderer;
        this.callback = callback;
        this.all = AbilityRegistry.search("");
        this.filtered = this.all;
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter;
        this.filtered = AbilityRegistry.search(this.filter);
        this.scroll = 0;
        this.hovered = -1;
    }

    private int visibleRows() {
        return Math.max(1, this.area.h / ROW_HEIGHT);
    }

    @Override
    public void draw(GuiContext context) {
        int rows = this.visibleRows();
        this.hovered = -1;

        for (int i = 0; i < rows; i++) {
            int index = i + this.scroll;
            if (index >= this.filtered.size()) {
                break;
            }
            IAbility ability = this.filtered.get(index);
            int ry = this.area.y + i * ROW_HEIGHT;
            boolean hover = context.mouseX >= this.area.x && context.mouseX < this.area.x + this.area.w
                    && context.mouseY >= ry && context.mouseY < ry + ROW_HEIGHT;
            if (hover) {
                this.hovered = index;
            }
            Gui.drawRect(this.area.x, ry, this.area.x + this.area.w, ry + ROW_HEIGHT,
                    hover ? 0x66FFFFFF : (i % 2 == 0 ? 0x44000000 : 0x22000000));

            ItemStack icon = ability.getIcon();
            if (!icon.isEmpty()) {
                context.mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                RenderHelper.enableGUIStandardItemLighting();
                context.mc.getRenderItem().renderItemIntoGUI(icon, this.area.x + 2, ry + 1);
                RenderHelper.disableStandardItemLighting();
            }

            int btnW = 76;
            int btnH = 14;
            int btnX = this.area.x + this.area.w - btnW - 4;
            int btnY = ry + 2;

            if (ability instanceof IStatefulAbility) {
                IStatefulAbility stateful = (IStatefulAbility) ability;
                boolean active = stateful.isActive(context.mc.player);
                String label = stateful.getStateLabel(context.mc.player);
                if (label == null || label.isEmpty()) {
                    label = active ? "On" : "Off";
                }
                boolean btnHover = context.mouseX >= btnX && context.mouseX <= btnX + btnW
                        && context.mouseY >= btnY && context.mouseY <= btnY + btnH;

                int bgColor = btnHover ? (active ? 0xDD388E3C : 0xAA666666) : (active ? 0xAA2E7D32 : 0x88333333);
                int borderColor = btnHover ? 0xFFFFFFFF : (active ? 0xFF81C784 : 0xFF777777);

                // 绘制按钮背景与1px边框
                Gui.drawRect(btnX, btnY, btnX + btnW, btnY + btnH, bgColor);
                Gui.drawRect(btnX, btnY, btnX + btnW, btnY + 1, borderColor);
                Gui.drawRect(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, borderColor);
                Gui.drawRect(btnX, btnY, btnX + 1, btnY + btnH, borderColor);
                Gui.drawRect(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, borderColor);

                int textW = this.font.getStringWidth(label);
                int textX = btnX + (btnW - textW) / 2;
                int textY = btnY + (btnH - 8) / 2;
                this.font.drawStringWithShadow(label, textX, textY, active ? 0xFFFFA0 : 0xE0E0E0);
            }

            String name = ability.getDisplayName();
            int nameMaxW = (ability instanceof IStatefulAbility) ? (this.area.w - 24 - btnW - 8) : (this.area.w - 30);
            if (this.font.getStringWidth(name) > nameMaxW) {
                name = this.font.trimStringToWidth(name, Math.max(10, nameMaxW - 4)) + "…";
            }
            this.font.drawStringWithShadow(name, this.area.x + 22, ry + 5, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(GuiContext context) {
        if (context.mouseButton == 0 && this.hovered >= 0 && this.hovered < this.filtered.size()) {
            IAbility ability = this.filtered.get(this.hovered);
            if (ability instanceof IStatefulAbility) {
                int i = this.hovered - this.scroll;
                int ry = this.area.y + i * ROW_HEIGHT;
                int btnW = 76;
                int btnH = 14;
                int btnX = this.area.x + this.area.w - btnW - 4;
                int btnY = ry + 2;
                if (context.mouseX >= btnX && context.mouseX <= btnX + btnW
                        && context.mouseY >= btnY && context.mouseY <= btnY + btnH) {
                    // 点击右侧按钮：就地激活/切换状态，不触发 callback 关闭界面选择槽位
                    ClientAbilityExecutor.activate(ability);
                    context.mc.getSoundHandler().playSound(
                            net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                                    net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            this.callback.accept(ability);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContext context) {
        int max = Math.max(0, this.filtered.size() - this.visibleRows());
        this.scroll = Math.max(0, Math.min(max, this.scroll + (context.mouseWheel > 0 ? -1 : 1)));
        return true;
    }
}

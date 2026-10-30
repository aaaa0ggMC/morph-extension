package aaaa0ggmc.morphext.client.gui;

import aaaa0ggmc.morphext.client.gui.elements.GuiLabelElement;
import aaaa0ggmc.morphext.config.ModConfig;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import org.lwjgl.input.Keyboard;

/** McLib HUD 位置设置：九宫格锚点 + 单一边距，修改立即保存。 */
public class GuiHudConfig extends GuiBase {
    private final GuiAbilityConfig parent;
    private static final String[] HORIZONTAL = {"left", "middle", "right"};
    private static final String[] VERTICAL = {"top", "center", "bottom"};

    public GuiHudConfig(GuiAbilityConfig parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        rebuild();
    }

    private void rebuild() {
        this.root.removeAll();
        this.root.add(this.context.keybinds);
        int x = 10;
        int w = this.width - 20;
        GuiLabelElement title = new GuiLabelElement(this.mc, "HUD position");
        title.flex().relative(this.root).x(x).y(10).w(w).h(16);
        this.root.add(title);

        GuiButtonElement horizontal = new GuiButtonElement(this.mc,
                IKey.str("Horizontal: " + HORIZONTAL[ModConfig.hudHorizontal]), b -> {
                    ModConfig.setHudPosition((ModConfig.hudHorizontal + 1) % 3,
                            ModConfig.hudVertical, ModConfig.hudMargin);
                    rebuild();
                });
        horizontal.flex().relative(this.root).x(x).y(36).w(w).h(20);
        this.root.add(horizontal);

        GuiButtonElement vertical = new GuiButtonElement(this.mc,
                IKey.str("Vertical: " + VERTICAL[ModConfig.hudVertical]), b -> {
                    ModConfig.setHudPosition(ModConfig.hudHorizontal,
                            (ModConfig.hudVertical + 1) % 3, ModConfig.hudMargin);
                    rebuild();
                });
        vertical.flex().relative(this.root).x(x).y(62).w(w).h(20);
        this.root.add(vertical);

        GuiButtonElement minus = new GuiButtonElement(this.mc, IKey.str("-"), b -> setMargin(-1));
        minus.flex().relative(this.root).x(x).y(88).w(30).h(20);
        this.root.add(minus);
        GuiLabelElement margin = new GuiLabelElement(this.mc, "Margin: " + ModConfig.hudMargin);
        margin.flex().relative(this.root).x(x + 34).y(90).w(w - 68).h(16);
        this.root.add(margin);
        GuiButtonElement plus = new GuiButtonElement(this.mc, IKey.str("+"), b -> setMargin(1));
        plus.flex().relative(this.root).x(this.width - 40).y(88).w(30).h(20);
        this.root.add(plus);

        GuiButtonElement done = new GuiButtonElement(this.mc, IKey.str("Done"),
                b -> this.mc.displayGuiScreen(this.parent));
        done.flex().relative(this.root).x(x).y(this.height - 30).w(w).h(20);
        this.root.add(done);
        this.context.pushViewport(this.viewport);
        this.root.resize();
        this.context.popViewport();
    }

    private void setMargin(int delta) {
        ModConfig.setHudPosition(ModConfig.hudHorizontal, ModConfig.hudVertical, ModConfig.hudMargin + delta);
        rebuild();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

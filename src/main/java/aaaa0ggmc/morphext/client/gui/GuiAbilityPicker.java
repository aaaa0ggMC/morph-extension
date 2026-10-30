package aaaa0ggmc.morphext.client.gui;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.client.gui.elements.GuiAbilityListElement;
import aaaa0ggmc.morphext.client.gui.elements.GuiLabelElement;
import aaaa0ggmc.morphext.client.gui.elements.GuiTextFieldElement;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

/**
 * 能力选择器（McLib 框架）：搜索框 + 可滚动能力列表。
 * 选中能力回调给配置界面；"(clear)" 一项用于清空槽位。
 */
public class GuiAbilityPicker extends GuiBase {
    private final GuiAbilityConfig parent;
    private final Consumer<IAbility> callback;

    private GuiAbilityListElement list;
    private GuiTextFieldElement search;

    public GuiAbilityPicker(GuiAbilityConfig parent, Consumer<IAbility> callback) {
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    public void initGui() {
        super.initGui();
        aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge.refresh();
        this.build();
        this.resizeRoot();
    }

    private void build() {
        int w = this.width;

        GuiLabelElement title = new GuiLabelElement(this.mc, "Pick an ability (" + AbilityRegistry.count() + " registered)");
        title.flex().relative(this.root).x(10).y(8).w(w - 20).h(14);
        this.root.add(title);

        this.search = new GuiTextFieldElement(this.mc, w - 20, 18);
        this.search.flex().relative(this.root).x(10).y(28).w(w - 20).h(18);
        this.search.getField().setFocused(true);
        this.root.add(this.search);

        /* 清空选项 */
        GuiButtonElement clear = new GuiButtonElement(this.mc, IKey.str("(clear this slot)"), b -> this.pick(null));
        clear.flex().relative(this.root).x(10).y(52).w(w - 20).h(18);
        this.root.add(clear);

        /* 能力列表 */
        this.list = new GuiAbilityListElement(this.mc, this::pick);
        this.list.flex().relative(this.root).x(10).y(76).w(w - 20).h(this.height - 76 - 34);
        this.root.add(this.list);

        GuiButtonElement cancel = new GuiButtonElement(this.mc, IKey.str("Cancel"), b -> this.back());
        cancel.flex().relative(this.root).x(10).y(this.height - 30).w(w - 20).h(20);
        this.root.add(cancel);
    }

    private void pick(IAbility ability) {
        this.callback.accept(ability);
    }

    private void back() {
        this.mc.displayGuiScreen(this.parent);
    }

    private void resizeRoot() {
        this.context.pushViewport(this.viewport);
        this.root.resize();
        this.context.popViewport();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.back();
            return;
        }
        super.keyTyped(typedChar, keyCode);
        if (this.search != null) {
            this.list.setFilter(this.search.getText());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // McLib 的 GuiBase 不画背景遮罩，直接透出世界；补上默认变暗背景保证可读性
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

package aaaa0ggmc.morphext.client.gui.elements;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** McLib 框架里的文本输入元素（内部托管一个原版 GuiTextField）。 */
public class GuiTextFieldElement extends GuiElement {
    private final GuiTextField field;
    private Runnable onEnter;

    public GuiTextFieldElement(Minecraft mc, int width, int height) {
        super(mc);
        this.area.set(0, 0, width, height);
        this.field = new GuiTextField(0, mc.fontRenderer, 0, 0, width, height);
        this.field.setEnableBackgroundDrawing(false);
        this.field.setMaxStringLength(64);
    }

    public GuiTextField getField() {
        return this.field;
    }

    public String getText() {
        return this.field.getText();
    }

    public void setText(String text) {
        this.field.setText(text);
    }

    public GuiTextFieldElement onEnter(Runnable onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    @Override
    public void draw(GuiContext context) {
        this.field.x = this.area.x;
        this.field.y = this.area.y;
        this.field.drawTextBox();
    }

    @Override
    public boolean keyTyped(GuiContext context) {
        if (context.keyCode == Keyboard.KEY_ESCAPE) {
            if (this.field.isFocused()) {
                this.field.setFocused(false);
                return true;
            }
            return false;
        }
        if (context.keyCode == Keyboard.KEY_RETURN || context.keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.onEnter != null) {
                this.onEnter.run();
            }
            return true;
        }
        if (!this.field.isFocused()) {
            return false;
        }
        return this.field.textboxKeyTyped(context.typedChar, context.keyCode);
    }

    @Override
    public boolean mouseClicked(GuiContext context) {
        this.field.setFocused(true);
        this.field.mouseClicked(context.mouseX, context.mouseY, context.mouseButton);
        return true;
    }
}

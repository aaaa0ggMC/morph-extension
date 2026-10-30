package aaaa0ggmc.morphext.client.gui.elements;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** McLib 框架里的简单文本标签元素。 */
public class GuiLabelElement extends GuiElement {
    private final FontRenderer font;
    private String text;
    private int color = 0xFFFFFF;
    private boolean shadow = true;
    private boolean centered = true;

    public GuiLabelElement(Minecraft mc, String text) {
        super(mc);
        this.font = mc.fontRenderer;
        this.text = text;
    }

    public GuiLabelElement color(int color, boolean shadow) {
        this.color = color;
        this.shadow = shadow;
        return this;
    }

    public GuiLabelElement centered(boolean centered) {
        this.centered = centered;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void draw(GuiContext context) {
        if (this.text == null || this.text.isEmpty()) {
            return;
        }
        int y = this.area.y + (this.area.h - 8) / 2;
        if (this.centered) {
            int x = this.area.x + (this.area.w - this.font.getStringWidth(this.text)) / 2;
            this.font.drawString(this.text, x, y, this.color, this.shadow);
        } else {
            this.font.drawString(this.text, this.area.x, y, this.color, this.shadow);
        }
    }
}

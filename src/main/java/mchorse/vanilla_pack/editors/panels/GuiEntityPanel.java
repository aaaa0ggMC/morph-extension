package mchorse.vanilla_pack.editors.panels;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.vanilla_pack.editors.GuiEntityMorph;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 加固版实体形态编辑面板 (Patched GuiEntityPanel)。
 * 防御构造期间 Trackpad 与 GuiTexturePicker 回调 this.morph == null 导致的 NPE 崩溃。
 */
@SideOnly(Side.CLIENT)
public class GuiEntityPanel extends GuiMorphPanel<EntityMorph, GuiEntityMorph>
{
    public GuiButtonElement texture;
    public GuiTrackpadElement scale;
    public GuiTexturePicker picker;

    public GuiEntityPanel(Minecraft mc, GuiEntityMorph editor)
    {
        super(mc, editor);

        GuiElement element = new GuiElement(mc);

        element.flex().relative(this).y(1F).w(130).anchor(0, 1F).column(4).vertical().stretch().padding(10);

        this.scale = new GuiTrackpadElement(mc, (v) -> {
            if (this.morph != null) {
                this.morph.scale = v.floatValue();
            }
        });
        this.texture = new GuiButtonElement(mc, IKey.lang("metamorph.gui.editor.texture"), (b) ->
        {
            this.picker.refresh();
            if (this.morph != null) {
                this.picker.fill(this.morph.userTexture);
            }
            this.add(this.picker);
            this.picker.resize();
        });
        this.texture.tooltip(IKey.lang("metamorph.gui.editor.texture_tooltip"), Direction.TOP);
        // 关键防护点：安全检查 this.morph != null
        this.picker = new GuiTexturePicker(mc, (rl) -> {
            if (this.morph != null) {
                this.morph.userTexture = RLUtils.clone(rl);
            }
        });
        this.picker.flex().relative(this).wh(1F, 1F);

        element.add(this.texture);
        element.add(Elements.label(IKey.lang("metamorph.gui.editor.scale")).marginTop(12), this.scale);
        this.add(element);
    }

    @Override
    public void fillData(EntityMorph morph)
    {
        super.fillData(morph);

        this.scale.setValue(morph.scale);
    }
}

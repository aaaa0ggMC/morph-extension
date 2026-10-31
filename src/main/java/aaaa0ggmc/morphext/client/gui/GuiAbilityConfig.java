package aaaa0ggmc.morphext.client.gui;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.client.gui.elements.GuiLabelElement;
import aaaa0ggmc.morphext.config.ModConfig;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置 UI（McLib 框架）：轮盘 Profile、槽位数量（1-8 可配置）与槽位能力绑定。
 * 点击槽位按钮 -> 打开能力选择器（选择器内可清空）。
 */
public class GuiAbilityConfig extends GuiBase {
    private final List<GuiButtonElement> slotButtons = new ArrayList<>();
    private GuiLabelElement maxSlotsLabel;
    private GuiLabelElement profileLabel;
    private int editingSlot = -1;

    @Override
    public void initGui() {
        super.initGui();
        aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge.refresh();
        this.rebuild();
    }

    private void build() {
        int w = this.width;

        GuiLabelElement title = new GuiLabelElement(this.mc, "Wheel Config");
        title.flex().relative(this.root).x(10).y(8).w(w - 110).h(14);
        this.root.add(title);

        GuiButtonElement hud = new GuiButtonElement(this.mc, IKey.str("HUD position"),
                b -> this.mc.displayGuiScreen(new GuiHudConfig(this)));
        hud.flex().relative(this.root).x(w - 100).y(4).w(90).h(20);
        this.root.add(hud);

        /* ---- Profile 行 ---- */
        GuiButtonElement prevProfile = new GuiButtonElement(this.mc, IKey.str("<"), b -> {
            int count = ModConfig.profileCount();
            ModConfig.setActiveProfile((ModConfig.activeProfile() - 1 + count) % count);
            this.rebuild();
        });
        prevProfile.flex().relative(this.root).x(10).y(28).w(20).h(18);
        this.root.add(prevProfile);

        this.profileLabel = new GuiLabelElement(this.mc, profileText());
        this.profileLabel.flex().relative(this.root).x(36).y(28).w(w - 200).h(18);
        this.root.add(this.profileLabel);

        GuiButtonElement nextProfile = new GuiButtonElement(this.mc, IKey.str(">"), b -> {
            int count = ModConfig.profileCount();
            ModConfig.setActiveProfile((ModConfig.activeProfile() + 1) % count);
            this.rebuild();
        });
        nextProfile.flex().relative(this.root).x(w - 148).y(28).w(20).h(18);
        this.root.add(nextProfile);

        GuiButtonElement status = new GuiButtonElement(this.mc, IKey.str("Status"), b ->
                this.mc.displayGuiScreen(new GuiAbilityStatus(this)));
        status.flex().relative(this.root).x(w - 90).y(28).w(80).h(18);
        this.root.add(status);

        /* ---- max slots 行 ---- */
        GuiLabelElement maxLabel = new GuiLabelElement(this.mc, "Max slots:");
        maxLabel.flex().relative(this.root).x(10).y(52).w(80).h(18);
        this.root.add(maxLabel);

        GuiButtonElement minus = new GuiButtonElement(this.mc, IKey.str("-"), b -> {
            ModConfig.setMaxSlots(ModConfig.maxSlots - 1);
            this.rebuild();
        });
        minus.flex().relative(this.root).x(95).y(52).w(24).h(18);
        this.root.add(minus);

        this.maxSlotsLabel = new GuiLabelElement(this.mc, String.valueOf(ModConfig.maxSlots));
        this.maxSlotsLabel.flex().relative(this.root).x(123).y(52).w(30).h(18);
        this.root.add(this.maxSlotsLabel);

        GuiButtonElement plus = new GuiButtonElement(this.mc, IKey.str("+"), b -> {
            ModConfig.setMaxSlots(ModConfig.maxSlots + 1);
            this.rebuild();
        });
        plus.flex().relative(this.root).x(157).y(52).w(24).h(18);
        this.root.add(plus);

        /* ---- Profile 数量 ---- */
        GuiLabelElement profileCountLabel = new GuiLabelElement(this.mc,
                "Profiles: " + ModConfig.profileCount());
        profileCountLabel.flex().relative(this.root).x(w - 220).y(52).w(90).h(18);
        this.root.add(profileCountLabel);

        GuiButtonElement profileMinus = new GuiButtonElement(this.mc, IKey.str("-"), b -> {
            ModConfig.setProfileCount(ModConfig.profileCount() - 1);
            this.rebuild();
        });
        profileMinus.flex().relative(this.root).x(w - 128).y(52).w(24).h(18);
        this.root.add(profileMinus);

        GuiButtonElement profilePlus = new GuiButtonElement(this.mc, IKey.str("+"), b -> {
            ModConfig.setProfileCount(ModConfig.profileCount() + 1);
            this.rebuild();
        });
        profilePlus.flex().relative(this.root).x(w - 100).y(52).w(24).h(18);
        this.root.add(profilePlus);

        /* ---- 8 个槽位（当前 Profile） ---- */
        int top = 78;
        int rowH = 22;
        for (int i = 0; i < ModConfig.MAX_SLOTS_HARD_CAP; i++) {
            final int slot = i;
            boolean active = i < ModConfig.activeSlotCount();
            String id = ModConfig.getSlot(i);
            IAbility ability = AbilityRegistry.get(id);
            String label = (i + 1) + ". " + (ability != null ? ability.getDisplayName()
                    : id.isEmpty() ? "(empty)" : id);

            GuiButtonElement button = new GuiButtonElement(this.mc, IKey.str(label), b -> this.openPicker(slot));
            button.flex().relative(this.root).x(10).y(top + i * rowH).w(w - 20).h(19);
            if (!active) {
                button.background(false).textColor(0x777777, false);
            }
            this.root.add(button);
            this.slotButtons.add(button);
        }

        /* ---- 底部：完成 ---- */
        GuiButtonElement done = new GuiButtonElement(this.mc, IKey.str("Done"), b -> this.close());
        done.flex().relative(this.root).x(10).y(top + ModConfig.MAX_SLOTS_HARD_CAP * rowH + 6).w(w - 20).h(20);
        this.root.add(done);
    }

    private String profileText() {
        int p = ModConfig.activeProfile();
        String key = ModConfig.getProfileKeyName(p);
        return "Profile " + (p + 1) + "/" + ModConfig.profileCount()
                + ": " + ModConfig.getProfileName(p)
                + (key == null || key.isEmpty() ? "" : "  [" + key + "]");
    }

    private void rebuild() {
        this.slotButtons.clear();
        this.root.removeAll();
        if (!this.context.keybinds.hasParent()) {
            this.root.add(this.context.keybinds);
        }
        this.build();
        this.resizeRoot();
    }

    private void resizeRoot() {
        this.context.pushViewport(this.viewport);
        this.root.resize();
        this.context.popViewport();
    }

    private void openPicker(int slot) {
        this.editingSlot = slot;
        this.mc.displayGuiScreen(new GuiAbilityPicker(this, ability -> {
            if (this.editingSlot >= 0) {
                ModConfig.setSlot(this.editingSlot, ability == null ? "" : ability.getId());
            }
            this.mc.displayGuiScreen(this);
        }));
    }

    private void close() {
        this.mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.close();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // McLib 的 GuiBase 不画背景遮罩，直接透出世界；补上默认变暗背景保证可读性
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

package aaaa0ggmc.morphext.client.gui;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import aaaa0ggmc.morphext.client.RenderUtil;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 状态总览（自绘，不依赖 McLib 框架）：
 *  - Active states：状态能力，左键行切换（能力自定义语义）
 *  - Passives：morph 授予的被动能力（只读，双列网格）
 *  - 右键任意行/格子：打开详情面板（名称 + 类型 + 状态 + 描述）
 * 内容超出可视区时右侧出现滚动条（滚轮 / 拖动滑块 / 点轨道）。
 */
public class GuiAbilityStatus extends GuiScreen {
    private final GuiAbilityConfig parent;
    private final List<IStatefulAbility> active = new ArrayList<>();
    private final List<IStatefulAbility> passives = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> passiveCells = new ArrayList<>();
    private String passiveSource = "";
    private int scroll = 0;
    private int maxScroll = 0;

    /* 滚动条状态（drawScreen 里算，鼠标处理里用） */
    private int trackTop;
    private int trackBottom;
    private int thumbY;
    private int thumbH;
    private boolean dragging = false;
    private int dragOffset = 0;

    /* 详情面板 */
    private IAbility detail;
    private int detailScroll = 0;
    private int detailMaxScroll = 0;
    private int detailPanelY;
    private int detailPanelH;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("morph-extension");

    public GuiAbilityStatus(GuiAbilityConfig parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        /* 打开即同步 morph 授予能力（事件可能在服务端触发，这里兜底刷新） */
        MetamorphBridge.refresh();
        this.collect();
    }

    public void refreshStates() {
        this.collect();
    }

    private void collect() {
        this.active.clear();
        this.passives.clear();
        this.passiveSource = "";
        java.util.Collection<IAbility> all = AbilityRegistry.all();
        LOGGER.info("[GuiAbilityStatus] collect: total abilities in registry={}", all.size());
        for (IAbility ability : all) {
            boolean isStateful = ability instanceof IStatefulAbility;
            boolean isVisible = ability != null && ability.isStatusVisible();
            boolean isUnlocked = AbilityUnlocks.isUnlocked(ability);
            boolean isGranted = ability != null && AbilityRegistry.isGranted(ability.getId());
            if (!isStateful || !isVisible) {
                continue;
            }
            if (!isUnlocked) {
                LOGGER.info("[GuiAbilityStatus] skipped locked ability: id={} isGranted={} isReady={}",
                        ability.getId(), isGranted, AbilityUnlocks.isReady());
                continue;
            }
            IStatefulAbility stateful = (IStatefulAbility) ability;
            boolean isActive = stateful.isActive(this.mc.player);
            if (!isActive) {
                LOGGER.info("[GuiAbilityStatus] skipped inactive ability: id={}", ability.getId());
                continue;
            }
            if (stateful.isPassive()) {
                this.passives.add(stateful);
                if (this.passiveSource.isEmpty()) {
                    this.passiveSource = stateful.getStatusGroup();
                }
            } else {
                this.active.add(stateful);
            }
        }

        // 智能合并多阵营状态：若宿主拥有多个细分阵营被动，在 Status 列表中聚合为一个整洁的 MergedFactionStatus 展现
        List<aaaa0ggmc.morphext.faction.FactionPassive> factionPassives = new ArrayList<>();
        for (IStatefulAbility p : this.passives) {
            if (p instanceof aaaa0ggmc.morphext.faction.FactionPassive) {
                factionPassives.add((aaaa0ggmc.morphext.faction.FactionPassive) p);
            }
        }
        if (factionPassives.size() > 1) {
            this.passives.removeAll(factionPassives);
            this.passives.add(0, new aaaa0ggmc.morphext.faction.MergedFactionStatus(factionPassives));
        }

        LOGGER.info("[GuiAbilityStatus] collected: active={} passives={} source={}",
                this.active.size(), this.passives.size(), this.passiveSource);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        /* 归一化 GL 状态：标准 src-alpha 混合 + 关闭深度测试（图标写入的深度会挡住后画的内容） */
        RenderUtil.resetBlendFunc();
        GlStateManager.disableDepth();
        FontRenderer font = this.fontRenderer;
        int w = this.width;
        int top = 10;
        int bottom = this.height - 34;
        int off = -this.scroll;
        this.rows.clear();
        this.passiveCells.clear();

        int y = top;

        /* 标题 */
        font.drawStringWithShadow("Ability States", 10, y + off, 0xFFFFFF);
        y += 16;

        /* Active states */
        font.drawStringWithShadow("Active states (left-click: toggle, right-click: details)", 10, y + off, 0xFFFFC44D);
        y += 14;
        if (this.active.isEmpty()) {
            font.drawStringWithShadow("(none)", 12, y + off, 0x888888);
            y += 12;
        }
        for (IStatefulAbility ability : this.active) {
            List<String> info = ability.getStateInfo(this.mc.player);
            int h = 18 + (info == null ? 0 : info.size() * 10);
            this.rows.add(new Row(10, y, w - 20, h, ability));
            if (y + off + h > top && y + off < bottom) {
                Gui.drawRect(10, y + off, w - 10, y + h + off, 0x44000000);
                this.drawIcon(ability.getIcon(), 12, y + off + 1);
                font.drawStringWithShadow(ability.getDisplayName() + " : " + ability.getStateLabel(this.mc.player),
                        32, y + off + 5, 0xFFFFC44D);
                if (info != null) {
                    for (int i = 0; i < info.size(); i++) {
                        font.drawStringWithShadow(info.get(i), 32, y + off + 15 + i * 10, 0xAAAAAA);
                    }
                }
            }
            y += h + 3;
        }
        y += 8;

        /* Passives */
        String source = this.passiveSource.isEmpty() ? "morph" : this.passiveSource;
        font.drawStringWithShadow("Passives (granted by " + source + ", right-click: details)", 10, y + off, 0xFF7EC7FF);
        y += 14;
        if (this.passives.isEmpty()) {
            font.drawStringWithShadow("(none)", 12, y + off, 0x888888);
            y += 12;
        } else {
            int cols = 2;
            int cellW = (w - 20) / cols;
            int cellH = 20;
            for (int i = 0; i < this.passives.size(); i++) {
                IStatefulAbility ability = this.passives.get(i);
                int cx = 10 + (i % cols) * cellW;
                int cy = y + (i / cols) * (cellH + 2);
                this.passiveCells.add(new Row(cx, cy, cellW - 2, cellH, ability));
                if (cy + off + cellH > top && cy + off < bottom) {
                    Gui.drawRect(cx, cy + off, cx + cellW - 2, cy + cellH + off, 0x44000000);
                    this.drawIcon(ability.getIcon(), cx + 2, cy + off + 2);
                    String rawName = ability.getDisplayName();
                    int maxNameW = cellW - 26;
                    String name = font.getStringWidth(rawName) > maxNameW ?
                            font.trimStringToWidth(rawName, Math.max(10, maxNameW - font.getStringWidth("…"))).trim() + "…" : rawName;
                    font.drawStringWithShadow(name, cx + 20, cy + off + 6, 0xFFFFFF);
                }
            }
            int rows = (this.passives.size() + cols - 1) / cols;
            y += rows * (cellH + 2);
        }

        this.maxScroll = Math.max(0, y - bottom);
        if (this.maxScroll <= 0) {
            this.scroll = 0;
        }

        /* 右侧滚动条：内容超出可视区时显示，滑块可拖动、轨道可点击跳转 */
        this.trackTop = top + 2;
        this.trackBottom = bottom - 2;
        int contentH = Math.max(1, y - top);
        int visibleH = Math.max(1, this.trackBottom - this.trackTop);
        if (this.maxScroll > 0) {
            this.thumbH = Math.max(20, visibleH * visibleH / contentH);
            int travel = visibleH - this.thumbH;
            this.thumbY = this.trackTop + (int) (travel * ((float) this.scroll / this.maxScroll));
            int barX = this.width - 8;
            Gui.drawRect(barX, this.trackTop, barX + 4, this.trackBottom, 0x66000000);
            Gui.drawRect(barX, this.thumbY, barX + 4, this.thumbY + this.thumbH, 0xFFAAAAAA);
            String hint = "wheel / drag";
            this.fontRenderer.drawStringWithShadow(hint,
                    this.width - 10 - this.fontRenderer.getStringWidth(hint), 12, 0x888888);
        }

        /* Done 按钮 */
        int doneY = this.height - 28;
        boolean hover = mouseX >= 10 && mouseX < w - 10 && mouseY >= doneY && mouseY < doneY + 20;
        Gui.drawRect(10, doneY, w - 10, doneY + 20, hover ? 0xFF3A7BD5 : 0xFF2A5A9A);
        String done = "Done";
        font.drawStringWithShadow(done, w / 2 - font.getStringWidth(done) / 2, doneY + 6, 0xFFFFFF);

        /* 详情面板（最上层） */
        if (this.detail != null) {
            this.drawDetail(mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /* ---------------- 详情面板 ---------------- */

    private void drawDetail(int mouseX, int mouseY) {
        FontRenderer font = this.fontRenderer;
        IAbility ability = this.detail;
        /* 画面板前归一化 GL 状态：标准混合 + 关闭深度测试，
           否则背景里图标的深度会让底板和文字被剔除（图标“压在文字上”）。 */
        RenderUtil.resetBlendFunc();
        GlStateManager.disableDepth();
        int pw = Math.min(this.width - 20, (int) (this.width * 0.8));
        int px = (this.width - pw) / 2;
        int textX = px + 12;
        int textW = pw - 24;

        String desc = ability.getDescription();
        List<String> lines = (desc == null || desc.isEmpty())
                ? Collections.singletonList("(no description)")
                : font.listFormattedStringToWidth(desc, textW);

        int headerH = 46;
        int footerH = 16;
        int bodyH = lines.size() * 10;
        int ph = headerH + bodyH + footerH;
        int maxPh = this.height - 20;
        this.detailPanelH = ph;
        this.detailPanelY = Math.max(4, (this.height - ph) / 2);
        int py = this.detailPanelY;

        /* 面板内容超高时内部滚动 */
        this.detailMaxScroll = Math.max(0, ph - maxPh);
        if (this.detailScroll > this.detailMaxScroll) {
            this.detailScroll = this.detailMaxScroll;
        }
        int bodyOff = -this.detailScroll;

        Gui.drawRect(px, py, px + pw, py + ph, 0xF0161620);
        Gui.drawRect(px, py, px + pw, py + 1, 0xFF7EC7FF);
        Gui.drawRect(px, py + ph - 1, px + pw, py + ph, 0xFF555555);

        /* 头部：图标 + 名称 + 类型/状态 */
        this.drawIcon(ability.getIcon(), px + 10, py + 10);
        font.drawStringWithShadow(ability.getDisplayName(), px + 30, py + 10, 0xFFFFC44D);
        String type = ability.isPassive() ? "Passive" : "Active";
        String group = ability.getStatusGroup();
        if (!group.isEmpty()) {
            type = type + " - granted by " + group;
        }
        font.drawStringWithShadow(type, px + 30, py + 22, 0xAAAAAA);
        if (ability instanceof IStatefulAbility) {
            font.drawStringWithShadow("State: "
                    + ((IStatefulAbility) ability).getStateLabel(this.mc.player), px + 30, py + 32, 0xAAAAAA);
        }

        /* 正文 */
        int ty = py + headerH + bodyOff;
        for (String line : lines) {
            if (ty + 8 > py && ty < py + ph - footerH) {
                font.drawStringWithShadow(line, textX, ty, 0xFFFFFF);
            }
            ty += 10;
        }

        /* 底部提示 */
        font.drawStringWithShadow("right-click / Esc to close",
                this.width / 2 - font.getStringWidth("right-click / Esc to close") / 2,
                py + ph - 12, 0x888888);

        /* 面板内滚动条 */
        if (this.detailMaxScroll > 0) {
            int bx = px + pw - 6;
            Gui.drawRect(bx, py + 2, bx + 3, py + ph - 2, 0x66000000);
            int tH = Math.max(12, (ph - 4) * (ph - 4) / Math.max(1, ph + this.detailMaxScroll));
            int tY = py + 2 + (int) ((ph - 4 - tH)
                    * ((float) this.detailScroll / this.detailMaxScroll));
            Gui.drawRect(bx, tY, bx + 3, tY + tH, 0xFFAAAAAA);
        }
    }

    private void drawIcon(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.enableGUIStandardItemLighting();
        this.mc.getRenderItem().renderItemIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        /* 物品渲染把混合函数留在非标准状态，且图标以更近的 z 写入深度缓冲：
           之后若不关深度测试，后画的半透明底板/文字会被深度剔除，图标反而“压在文字上”。 */
        RenderUtil.resetBlendFunc();
        GlStateManager.disableDepth();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /* ---------------- 输入 ---------------- */

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        /* 详情面板打开时，任何点击先关面板 */
        if (this.detail != null) {
            this.detail = null;
            this.detailScroll = 0;
            return;
        }
        if (mouseButton == 1) {
            /* 右键：查详情（被动格子在虚拟坐标里，要加 scroll） */
            int vy = mouseY + this.scroll;
            for (Row row : this.rows) {
                if (row.contains(mouseX, vy)) {
                    this.detail = row.ability;
                    return;
                }
            }
            for (Row cell : this.passiveCells) {
                if (cell.contains(mouseX, vy)) {
                    this.detail = cell.ability;
                    return;
                }
            }
            return;
        }
        if (mouseButton == 0) {
            /* 滚动条交互：拖动滑块 / 点击轨道跳转 */
            int barX = this.width - 8;
            if (this.maxScroll > 0 && mouseX >= barX - 2 && mouseX <= barX + 6
                    && mouseY >= this.trackTop && mouseY <= this.trackBottom) {
                if (mouseY >= this.thumbY && mouseY <= this.thumbY + this.thumbH) {
                    this.dragging = true;
                    this.dragOffset = mouseY - this.thumbY;
                } else {
                    int travel = Math.max(1, this.trackBottom - this.trackTop - this.thumbH);
                    this.scroll = (int) ((float) (mouseY - this.trackTop - this.thumbH / 2)
                            / travel * this.maxScroll);
                    this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll));
                }
                return;
            }
            int doneY = this.height - 28;
            if (mouseX >= 10 && mouseX < this.width - 10 && mouseY >= doneY && mouseY < doneY + 20) {
                this.back();
                return;
            }
            int vy = mouseY + this.scroll;
            for (Row row : this.rows) {
                if (mouseX >= 10 && mouseX < this.width - 10 && vy >= row.y && vy < row.y + row.h) {
                    /* 开关类才响应点击；释放型（mob act）点击不触发，只在轮盘里释放 */
                    if (row.ability.isStateToggleable()) {
                        aaaa0ggmc.morphext.client.ClientAbilityExecutor.activate(row.ability);
                        this.collect();
                    }
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        if (this.detail != null) {
            if (this.detailMaxScroll > 0) {
                this.detailScroll -= Integer.signum(wheel) * 12;
                this.detailScroll = Math.max(0, Math.min(this.detailMaxScroll, this.detailScroll));
            }
            return;
        }
        if (this.maxScroll > 0) {
            this.scroll -= Integer.signum(wheel) * 24;
            this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.dragging) {
            int travel = Math.max(1, this.trackBottom - this.trackTop - this.thumbH);
            this.scroll = (int) ((float) (mouseY - this.dragOffset - this.trackTop)
                    / travel * this.maxScroll);
            this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll));
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            if (this.detail != null) {
                this.detail = null;
                this.detailScroll = 0;
                return;
            }
            this.back();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void back() {
        this.mc.displayGuiScreen(this.parent);
    }

    private static final class Row {
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final IStatefulAbility ability;

        private Row(int x, int y, int w, int h, IStatefulAbility ability) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.ability = ability;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.w
                    && mouseY >= this.y && mouseY < this.y + this.h;
        }
    }
}

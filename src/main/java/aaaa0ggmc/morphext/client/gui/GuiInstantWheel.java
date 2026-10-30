package aaaa0ggmc.morphext.client.gui;

import aaaa0ggmc.morphext.ability.AbilityCooldowns;
import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.config.ModConfig;
import aaaa0ggmc.morphext.client.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * InstantUI —— 按住快捷键唤出的能力轮盘。
 *
 * 交互（快速模式）：
 *   按住键 -> 轮盘出现 -> 鼠标移动高亮 -> 点击或松键 -> 切换高亮能力并关闭
 * 交互（搜索模式）：
 *   快速模式下按其他键 / 点搜索框 -> 进入搜索模式；松键不关闭；Esc 或 X 关闭
 * 暂停策略：单人游戏暂停（打开轮盘时世界停转），联机无法暂停、世界继续运行。
 */
public class GuiInstantWheel extends GuiScreen {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private enum Mode {QUICK, SEARCH}

    private static final int VISIBLE_RESULTS = 8;
    private static final int ROW_HEIGHT = 20;

    private Mode mode = Mode.QUICK;

    /** 首帧不做松键检测（打开时键必然还按着），之后每帧检测。 */
    private boolean keyReleased = false;
    /** 上次观测到的轮盘键状态（诊断用）。 */
    private boolean lastDownState = true;
    private boolean ignoreOpeningKeyPress = true;
    private boolean searchHovered;

    private final List<IAbility> bound = new ArrayList<>();
    private int hoveredSector = -1;
    private String activeAbility = "";

    private final int openKeyCode;

    private GuiTextField searchField;
    private List<IAbility> results = new ArrayList<>();
    private int resultScroll = 0;
    private int hoveredResult = -1;

    // 右上角两个小按钮（齿轮=配置，X=关闭）
    private int gearX, gearY, gearW, gearH;
    private int closeX, closeY, closeW, closeH;
    private int searchX, searchY, searchW, searchH;

    public GuiInstantWheel(int openKeyCode) {
        this.openKeyCode = openKeyCode;
        if (aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge.available()) {
            aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge.refresh();
        }
        this.activeAbility = ModConfig.activeAbility;
        int count = ModConfig.activeSlotCount();
        for (int i = 0; i < count; i++) {
            IAbility ability = AbilityRegistry.get(ModConfig.SLOT_ABILITY_IDS[i]);
            // 未解锁的能力不能绑定/使用
            if (ability != null && AbilityUnlocks.isUnlocked(ability)) {
                this.bound.add(ability);
            }
        }
        this.results = AbilityRegistry.search("");
    }

    /* ---------------- 生命周期 ---------------- */

    @Override
    public boolean doesGuiPauseGame() {
        IntegratedServer server = this.mc.getIntegratedServer();
        // 只有真正的单机暂停；联机（含开放局域网）无法暂停
        return this.mc.isSingleplayer() && (server == null || !server.getPublic());
    }

    @Override
    public void initGui() {
        super.initGui();
        // 搜索框放顶部居中（角落按钮在 y=6..24 之下）
        this.searchW = Math.min(240, this.width - 40);
        this.searchH = 16;
        this.searchX = this.width / 2 - this.searchW / 2;
        this.searchY = 30;
        this.searchField = new GuiTextField(0, this.fontRenderer, this.searchX, this.searchY, this.searchW, this.searchH);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(64);

        int bw = 18;
        int bx = this.width - 6;
        this.closeX = bx - bw;
        this.closeY = 6;
        this.closeW = bw;
        this.closeH = bw;
        this.gearX = this.closeX - bw - 3;
        this.gearY = 6;
        this.gearW = bw;
        this.gearH = bw;
    }

    @Override
    public void updateScreen() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    /**
     * 松键检测放在 drawScreen（而不是 updateScreen）：
     * 单人游戏打开轮盘会暂停，暂停后 updateScreen 不再被调用；
     * drawScreen 每帧都会跑（暂停时也渲染），且此处统一处理键盘/鼠标键位。
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.searchHovered = this.isInside(mouseX, mouseY, this.searchX - 2, this.searchY - 2,
                this.searchW + 4, this.searchH + 4);
        if (!isWheelKeyDown()) {
            this.ignoreOpeningKeyPress = false;
        }
        if (this.mode == Mode.QUICK && this.keyReleased) {
            boolean down = isWheelKeyDown();
            if (down != this.lastDownState) {
                // 诊断日志：状态翻转时记录（排查鼠标键位松键检测问题）
                LOGGER.info("[wheel] key state changed: openKey={} down={} (mouse={})",
                        this.openKeyCode, down, this.openKeyCode < 0);
                this.lastDownState = down;
            }
            if (!down) {
                this.keyReleased = false;
                // 悬停搜索框后松开轮盘键即可搜索，不必精确点击且不会误选旧扇区。
                if (this.searchHovered) {
                    this.enterSearchMode("");
                } else {
                    IAbility ability = this.getHoveredAbility();
                    if (ability != null) {
                        LOGGER.info("[wheel] release-select: {}", ability.getId());
                        this.select(ability);
                        return;
                    }
                    LOGGER.info("[wheel] release-close (nothing hovered)");
                    this.close();
                    return;
                }
            }
        }
        this.keyReleased = true;

        this.drawDefaultBackground();

        if (this.mode == Mode.QUICK) {
            this.drawWheel(mouseX, mouseY);
        } else {
            this.drawSearch(mouseX, mouseY);
        }

        this.drawCornerButtons(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 轮盘键是否按住（兼容鼠标键位：鼠标按键的 keyCode 为负，code+100=按钮号）。 */
    private boolean isWheelKeyDown() {
        if (this.openKeyCode < 0) {
            return Mouse.isButtonDown(this.openKeyCode + 100);
        }
        return Keyboard.isKeyDown(this.openKeyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (this.mode == Mode.SEARCH) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                int max = Math.max(0, this.results.size() - this.visibleResultRows());
                this.resultScroll = Math.max(0, Math.min(max, this.resultScroll + (wheel > 0 ? -1 : 1)));
            }
        }
    }

    /* ---------------- 输入 ---------------- */

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.isInside(mouseX, mouseY, this.closeX, this.closeY, this.closeW, this.closeH)) {
            this.close();
            return;
        }
        if (this.isInside(mouseX, mouseY, this.gearX, this.gearY, this.gearW, this.gearH)) {
            this.mc.displayGuiScreen(new GuiAbilityConfig());
            return;
        }

        if (this.mode == Mode.QUICK) {
            // 点搜索框 -> 搜索模式
            if (this.isInside(mouseX, mouseY, this.searchX - 2, this.searchY - 2, this.searchW + 4, this.searchH + 4)) {
                this.enterSearchMode("");
                return;
            }
            // 点击扇区 -> 选中（不触发，按释放键才触发）
            if (mouseButton == 0) {
                IAbility ability = this.getHoveredAbility();
                if (ability != null) {
                    this.select(ability);
                }
            }
            return;
        }

        // 搜索模式
        this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && this.hoveredResult >= 0 && this.hoveredResult < this.results.size()) {
            this.select(this.results.get(this.hoveredResult));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.ignoreOpeningKeyPress && keyCode == this.openKeyCode) {
            return; // 搜索模式也必须吞掉首次按住 G 产生的自动重复字符。
        }
        if (this.mode == Mode.QUICK) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == this.openKeyCode) {
                // Esc 关闭；轮盘自身的键交给“松键检测”处理
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    this.close();
                }
                return;
            }
            // 其他任意键 -> 进入搜索模式（可打印字符预填进搜索框）
            this.enterSearchMode(typedChar >= ' ' && typedChar != 127 ? String.valueOf(typedChar) : "");
            return;
        }

        // 搜索模式
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.close();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!this.results.isEmpty()) {
                int index = this.hoveredResult >= 0 ? this.hoveredResult : this.resultScroll;
                this.select(this.results.get(Math.max(0, Math.min(index, this.results.size() - 1))));
            }
            return;
        }
        this.searchField.textboxKeyTyped(typedChar, keyCode);
        this.refreshResults();
    }

    /* ---------------- 绘制 ---------------- */

    private void drawWheel(int mouseX, int mouseY) {
        /* 可用区域：顶部避开搜索框/角落按钮，底部避开热键栏（不同 GUI Scale 下坐标空间不同，必须动态算） */
        int topLimit = 52;
        int bottomLimit = this.height - 46;
        int cx = this.width / 2;
        int cy = (topLimit + bottomLimit) / 2;
        int n = this.bound.size();

        int minDim = Math.min(this.width, this.height);
        int band = (int) Math.max(12, Math.min(26, minDim * 0.09F));
        int cellSize = (int) Math.max(8, Math.min(14, minDim * 0.05F));
        int usableHeight = bottomLimit - topLimit;
        int maxByHeight = Math.max(40, (usableHeight - 20) / 2 - band);
        int maxByWidth = Math.max(40, this.width / 2 - band - 12);
        int ringRadius = Math.min(Math.min(maxByHeight, maxByWidth), (int) (minDim * 0.40F));

        this.hoveredSector = -1;
        if (n > 0) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            // 只有落在环带（含格子外接）内才算悬停；中心区域无选中
            if (dist <= ringRadius + band + 4) {
                double angle = Math.toDegrees(Math.atan2(dy, dx)); // 0=右 90=下
                double step = 360D / n;
                int sector = (int) (((angle + 90 + 360) % 360) / step);
                if (sector >= 0 && sector < n) {
                    this.hoveredSector = sector;
                }
            }
        }

        if (n == 0) {
            this.drawCenteredString(this.fontRenderer,
                    I18n.translateToLocal("gui.morphext.wheel.empty"), cx, cy - 12, 0xFFFFFF);
            this.drawCenteredString(this.fontRenderer,
                    I18n.translateToLocalFormatted("gui.morphext.wheel.count", n, ModConfig.activeSlotCount()),
                    cx, cy + 4, 0xAAAAAA);
        } else {
            double step = 360D / n;
            double gap = n >= 2 ? Math.min(2.0, step * 0.06) : 0.0;

            /* 当前选中项索引（按释放键触发的那个） */
            int activeIndex = -1;
            for (int i = 0; i < n; i++) {
                if (this.bound.get(i).getId().equals(this.activeAbility)) {
                    activeIndex = i;
                    break;
                }
            }

            /* 环带：每格一段环形扇区，之间留缝（单个能力时整环无逢） */
            for (int i = 0; i < n; i++) {
                double a0 = -90 + i * step;
                double a1 = a0 + step;
                int color = (i == this.hoveredSector) ? 0xCC3AA5E0 : 0x88101428;
                this.drawRingSegment(cx, cy, ringRadius - band, ringRadius + band, a0 + gap, a1 - gap, color);
            }

            /* 菱形格子 + 图标（单个能力时置顶） */
            for (int i = 0; i < n; i++) {
                double mid = Math.toRadians(-90 + (n == 1 ? 0 : (i + 0.5)) * step);
                int ix = (int) (cx + Math.cos(mid) * ringRadius);
                int iy = (int) (cy + Math.sin(mid) * ringRadius);
                this.drawCell(ix, iy, cellSize, i == this.hoveredSector, i == activeIndex);
                this.renderIcon(this.bound.get(i).getIcon(), ix - 8, iy - 8);
            }
            /* 中央：悬停名优先；否则显示当前选中（按释放键将触发）的能力名 */
            if (this.hoveredSector >= 0) {
                IAbility hovered = this.bound.get(this.hoveredSector);
                String name = hovered.getDisplayName();
                if (this.fontRenderer.getStringWidth(name) > this.width - 80) {
                    name = this.fontRenderer.trimStringToWidth(name, this.width - 88) + "…";
                }
                this.drawCenteredString(this.fontRenderer, name, cx, cy - 4, 0xFFFFFF);
                if (!AbilityCooldowns.isReady(hovered.getId())) {
                    this.drawCenteredString(this.fontRenderer,
                            I18n.translateToLocalFormatted("gui.morphext.cooldown.short",
                                    AbilityCooldowns.getRemainingText(hovered.getId())),
                            cx, cy + 8, 0xFF7755);
                }
            } else if (activeIndex >= 0) {
                String name = this.bound.get(activeIndex).getDisplayName();
                if (this.fontRenderer.getStringWidth(name) > this.width - 80) {
                    name = this.fontRenderer.trimStringToWidth(name, this.width - 88) + "…";
                }
                this.drawCenteredString(this.fontRenderer, name, cx, cy - 4, 0xFFFFC44D);
                this.drawCenteredString(this.fontRenderer,
                        I18n.translateToLocal("gui.morphext.wheel.selected"), cx, cy + 8, 0x808080);
            }

            this.drawCenteredString(this.fontRenderer,
                    I18n.translateToLocalFormatted("gui.morphext.wheel.count", n, ModConfig.activeSlotCount()),
                    cx, cy + ringRadius + band + 10, 0xFFFFFF);
        }

        // 搜索框（可见：背景 + 边框 + 占位提示）
        this.drawSearchBox();
    }

    /** 绘制顶部搜索框（原版 GuiTextField 关闭背景后不可见，需要自绘外框）。 */
    private void drawSearchBox() {
        boolean focused = this.searchField.isFocused();
        int x0 = this.searchX - 2;
        int y0 = this.searchY - 2;
        int x1 = this.searchX + this.searchW + 2;
        int y1 = this.searchY + this.searchH + 2;

        this.drawRect(x0, y0, x1, y1, focused ? 0xE6000000 : 0xB3000000);
        int border = focused || this.searchHovered ? 0xFF4DB8FF : 0xFF4A4A5A;
        this.drawRect(x0, y0, x1, y0 + 1, border);
        this.drawRect(x0, y1 - 1, x1, y1, border);
        this.drawRect(x0, y0, x0 + 1, y1, border);
        this.drawRect(x1 - 1, y0, x1, y1, border);

        if (!focused && this.searchField.getText().isEmpty()) {
            this.fontRenderer.drawStringWithShadow(I18n.translateToLocal("gui.morphext.search.placeholder"),
                    this.searchX + 4, this.searchY + 4, 0xFF909090);
        }
        this.searchField.drawTextBox();
    }

    /** 环形扇区（内外半径之间的圆弧带）。 */
    private void drawRingSegment(double cx, double cy, double rIn, double rOut, double a0, double a1, int color) {
        float r = (color >> 16 & 255) / 255F;
        float g = (color >> 8 & 255) / 255F;
        float b = (color & 255) / 255F;
        float a = (color >> 24 & 255) / 255F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int steps = Math.max(8, (int) ((a1 - a0) / 3));
        for (int i = 0; i <= steps; i++) {
            double ang = Math.toRadians(a0 + (a1 - a0) * i / steps);
            bb.pos(cx + Math.cos(ang) * rOut, cy + Math.sin(ang) * rOut, 0).color(r, g, b, a).endVertex();
            bb.pos(cx + Math.cos(ang) * rIn, cy + Math.sin(ang) * rIn, 0).color(r, g, b, a).endVertex();
        }
        tess.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** 菱形格子背景。 */
    private void drawCell(int cx, int cy, int s, boolean highlight, boolean active) {
        float r, g, b, a;
        if (highlight) {
            r = 0.35F; g = 0.72F; b = 0.94F; a = 0.87F;
        } else if (active) {
            r = 0.95F; g = 0.72F; b = 0.20F; a = 0.85F;
        } else {
            r = 0.13F; g = 0.13F; b = 0.19F; a = 0.60F;
        }

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        bb.pos(cx, cy - s, 0).color(r, g, b, a).endVertex();
        bb.pos(cx + s, cy, 0).color(r, g, b, a).endVertex();
        bb.pos(cx, cy + s, 0).color(r, g, b, a).endVertex();
        bb.pos(cx - s, cy, 0).color(r, g, b, a).endVertex();
        tess.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private int visibleResultRows() {
        int top = this.searchY + this.searchH + 8;
        int available = this.height - top - 30;
        return Math.max(3, Math.min(VISIBLE_RESULTS, available / ROW_HEIGHT));
    }

    private void drawSearch(int mouseX, int mouseY) {
        int rowH = ROW_HEIGHT;
        int rows = this.visibleResultRows();
        int listW = Math.min(280, this.width - 40);
        int x = this.width / 2 - listW / 2;
        int y = this.searchY + this.searchH + 8;

        this.drawRect(x - 4, y - 4, x + listW + 4, y + rows * rowH + 4, 0xAA000000);

        this.hoveredResult = -1;
        for (int i = 0; i < rows; i++) {
            int index = i + this.resultScroll;
            if (index >= this.results.size()) {
                break;
            }
            IAbility ability = this.results.get(index);
            int ry = y + i * rowH;
            boolean hover = mouseX >= x && mouseX <= x + listW && mouseY >= ry && mouseY < ry + rowH;
            if (hover) {
                this.hoveredResult = index;
            }
            this.drawRect(x, ry, x + listW, ry + rowH,
                    hover ? 0x66FFFFFF : (i % 2 == 0 ? 0x44000000 : 0x22000000));
            this.renderIcon(ability.getIcon(), x + 3, ry + 2);
            String name = ability.getDisplayName();
            if (this.fontRenderer.getStringWidth(name) > listW - 44) {
                name = this.fontRenderer.trimStringToWidth(name, listW - 48) + "…";
            }
            this.fontRenderer.drawStringWithShadow(name, x + 24, ry + 6, 0xFFFFFF);
        }

        this.drawSearchBox();
        this.drawCenteredString(this.fontRenderer,
                I18n.translateToLocalFormatted("gui.morphext.search.count", this.results.size()),
                this.width / 2, y + rows * rowH + 8, 0xAAAAAA);
    }

    private void drawCornerButtons(int mouseX, int mouseY) {
        this.drawRect(this.closeX, this.closeY, this.closeX + this.closeW, this.closeY + this.closeH,
                this.isInside(mouseX, mouseY, this.closeX, this.closeY, this.closeW, this.closeH) ? 0xCC883333 : 0x66202020);
        this.drawCenteredString(this.fontRenderer, "X", this.closeX + this.closeW / 2, this.closeY + 5, 0xFFFFFF);
        this.drawRect(this.gearX, this.gearY, this.gearX + this.gearW, this.gearY + this.gearH,
                this.isInside(mouseX, mouseY, this.gearX, this.gearY, this.gearW, this.gearH) ? 0xCC335588 : 0x66202020);
        this.drawCenteredString(this.fontRenderer, "*", this.gearX + this.gearW / 2, this.gearY + 5, 0xFFFFFF);
    }

    private void drawSector(double cx, double cy, double radius, double a0, double a1, int color) {
        float r = (color >> 16 & 255) / 255F;
        float g = (color >> 8 & 255) / 255F;
        float b = (color & 255) / 255F;
        float a = (color >> 24 & 255) / 255F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        bb.pos(cx, cy, 0).color(r, g, b, a).endVertex();
        int steps = Math.max(8, (int) ((a1 - a0) / 4));
        for (int i = 0; i <= steps; i++) {
            double ang = Math.toRadians(a0 + (a1 - a0) * i / steps);
            bb.pos(cx + Math.cos(ang) * radius, cy + Math.sin(ang) * radius, 0).color(r, g, b, a).endVertex();
        }
        tess.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void renderIcon(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, "?", x + 8, y + 4, 0x666666);
            return;
        }
        /* 显式绑定方块图集：GUI 上下文里此前可能绑定的是字体/控件图集，不绑会渲染出字形垃圾 */
        this.mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.enableGUIStandardItemLighting();
        this.mc.getRenderItem().renderItemIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        /* 物品渲染后：混合函数复位 + 关深度测试，否则后画的半透明底板会被剔除 */
        RenderUtil.resetBlendFunc();
        GlStateManager.disableDepth();
    }

    /* ---------------- 逻辑 ---------------- */

    private IAbility getHoveredAbility() {
        if (this.mode == Mode.QUICK && this.hoveredSector >= 0 && this.hoveredSector < this.bound.size()) {
            return this.bound.get(this.hoveredSector);
        }
        return null;
    }

    private void enterSearchMode(String initialText) {
        this.mode = Mode.SEARCH;
        if (this.searchField != null) {
            this.searchField.setText(initialText);
            this.searchField.setFocused(true);
        }
        this.refreshResults();
    }

    private void refreshResults() {
        this.results = AbilityRegistry.search(this.searchField == null ? "" : this.searchField.getText());
        this.resultScroll = 0;
        this.hoveredResult = -1;
    }

    /** 轮盘切换：选中能力（持久化），不触发；触发由释放键负责。 */
    private void select(IAbility ability) {
        if (ability != null) {
            ModConfig.setActiveAbility(ability.getId());
            this.activeAbility = ability.getId();
        }
        this.close();
    }

    private void close() {
        this.mc.displayGuiScreen(null);
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void onGuiClosed() {
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }
    }
}

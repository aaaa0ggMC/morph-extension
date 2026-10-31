package aaaa0ggmc.morphext.client.gui;

import aaaa0ggmc.morphext.ability.AbilityCooldowns;
import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import aaaa0ggmc.morphext.client.ClientAbilityExecutor;
import aaaa0ggmc.morphext.config.ModConfig;
import aaaa0ggmc.morphext.client.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.SoundEvents;
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
import java.util.Locale;

/**
 * InstantUI —— 按住快捷键唤出的能力轮盘（多 Profile）。
 *
 * 交互（快速模式）：
 *   按住键 -> 轮盘出现 -> 鼠标移动高亮 -> 点击或松键 -> 切换高亮能力并关闭
 *   数字键 1..8 -> 直接选中对应槽位并关闭（随后按释放键使用）
 *   Profile 快捷键（默认 Q/W/E/R/T/Y）-> 切换 Profile，轮盘保持打开
 *   鼠标悬停左侧 Profile Tab -> 高亮并切换
 * 交互（搜索模式）：
 *   快速模式下按其他键 / 点搜索框 -> 进入搜索模式；松键不关闭；Esc 或 X 关闭
 * 暂停策略：单人游戏暂停（打开轮盘时世界停转），联机无法暂停、世界继续运行。
 */
public class GuiInstantWheel extends GuiScreen {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private enum Mode {QUICK, SEARCH}

    private static final int VISIBLE_RESULTS = 8;
    private static final int ROW_HEIGHT = 20;

    /** 一个固定槽位：即使能力当前不属于玩家，也保留位置并显示“已遗忘”。 */
    private static final class SlotEntry {
        final String id;
        final IAbility ability;
        final boolean empty;
        final boolean locked;
        final String displayName;
        final ItemStack icon;

        SlotEntry(String id, IAbility ability, boolean empty, boolean locked, String displayName, ItemStack icon) {
            this.id = id;
            this.ability = ability;
            this.empty = empty;
            this.locked = locked;
            this.displayName = displayName;
            this.icon = icon;
        }
    }

    private Mode mode = Mode.QUICK;

    /** 首帧不做松键检测（打开时键必然还按着），之后每帧检测。 */
    private boolean keyReleased = false;
    /** 上次观测到的轮盘键状态（诊断用）。 */
    private boolean lastDownState = true;
    private boolean ignoreOpeningKeyPress = true;
    private boolean searchHovered;

    private final List<SlotEntry> slots = new ArrayList<>();
    private int hoveredSector = -1;
    private String activeAbility = "";

    // 最近一次绘制的轮盘几何，用于右侧详情面板定位
    private int wheelCx, wheelCy, wheelRadius, wheelBand;

    private final int openKeyCode;

    private GuiTextField searchField;
    private List<IAbility> results = new ArrayList<>();
    private int resultScroll = 0;
    private int hoveredResult = -1;

    // Profile 左侧 Tab 几何
    private int tabX, tabY, tabW, tabH, tabGap;
    private int hoveredProfile = -1;

    // 右上角两个小按钮（齿轮=配置，X=关闭）
    private int gearX, gearY, gearW, gearH;
    private int closeX, closeY, closeW, closeH;
    private int searchX, searchY, searchW, searchH;

    public GuiInstantWheel(int openKeyCode) {
        this.openKeyCode = openKeyCode;
        if (aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge.available()) {
            aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge.refresh();
        }
        this.reloadSlots();
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

        this.tabX = 6;
        this.tabY = 58;
        this.tabW = Math.min(112, Math.max(76, this.width / 5));
        this.tabGap = 4;
        this.tabH = 19;
        int maxTabs = Math.max(1, (this.height - this.tabY - 24) / (this.tabH + this.tabGap));
        if (ModConfig.profileCount() > maxTabs) {
            this.tabH = Math.max(12, (this.height - this.tabY - 24) / ModConfig.profileCount() - this.tabGap);
        }
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
                } else if (this.hoveredSector >= 0
                        && this.hoveredSector < this.slots.size()
                        && !this.slots.get(this.hoveredSector).empty) {
                    LOGGER.info("[wheel] release-select slot {}", this.hoveredSector + 1);
                    // 选中后必须关闭；否则松键状态会在下一帧再次进入 release 分支，
                    // 导致选中音效每帧重复、界面反复刷新。
                    if (this.selectSlot(this.hoveredSector)) {
                        this.close();
                    }
                    return;
                } else {
                    LOGGER.info("[wheel] release-close (nothing hovered)");
                    this.close();
                    return;
                }
            }
        }
        this.keyReleased = true;

        this.drawDefaultBackground();

        if (this.mode == Mode.QUICK) {
            this.updateHoveredTab(mouseX, mouseY);
            this.drawWheel(mouseX, mouseY);
            this.drawProfileTabs(mouseX, mouseY);
            this.drawDetailPanel();
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
            // 左侧 Profile Tab：点击切换
            int profile = this.profileTabAt(mouseX, mouseY);
            if (profile >= 0) {
                this.switchProfile(profile);
                return;
            }
            // 点搜索框 -> 搜索模式
            if (this.isInside(mouseX, mouseY, this.searchX - 2, this.searchY - 2, this.searchW + 4, this.searchH + 4)) {
                this.enterSearchMode("");
                return;
            }
            // 点击扇区 -> 选中并关闭（随后按释放键使用）
            if (mouseButton == 0 && this.hoveredSector >= 0) {
                if (this.selectSlot(this.hoveredSector)) {
                    this.close();
                }
            }
            return;
        }

        // 搜索模式
        this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && this.hoveredResult >= 0 && this.hoveredResult < this.results.size()) {
            IAbility ability = this.results.get(this.hoveredResult);
            if (ability instanceof IStatefulAbility) {
                int rowH = ROW_HEIGHT;
                int listW = Math.min(280, this.width - 40);
                int x = this.width / 2 - listW / 2;
                int y = this.searchY + this.searchH + 8;
                int rowIdx = this.hoveredResult - this.resultScroll;
                int ry = y + rowIdx * rowH;
                int btnW = 76;
                int btnH = rowH - 4;
                int btnX = x + listW - btnW - 3;
                int btnY = ry + 2;

                if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    // 点击了状态切换按钮：就地切换状态，不选中槽位，不关闭搜索界面
                    ClientAbilityExecutor.activate(ability);
                    this.mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return;
                }
            }

            this.select(ability);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == this.openKeyCode) {
            // 1) 打开轮盘时按住的开关键产生的首次字符/重复字符；
            // 2) 松开 G 之后才被 GUI 队列消费的残留 G 事件（此时物理键已经抬起）。
            // 只有“松开后重新明确按下”的 G（isKeyDown=true 且已过 opening 阶段）才允许输入。
            if (this.ignoreOpeningKeyPress || this.openKeyCode < 0 || !Keyboard.isKeyDown(this.openKeyCode)) {
                return;
            }
        }
        if (this.mode == Mode.QUICK) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.close();
                return;
            }
            if (keyCode == this.openKeyCode) {
                // 轮盘自身的键交给“松键检测”处理
                return;
            }
            // 数字键 1..8：快速选中对应槽位并关闭（随后按释放键使用）
            int slot = numberKeyToSlot(keyCode);
            if (slot >= 0) {
                if (this.selectSlot(slot)) {
                    this.close();
                }
                return;
            }
            // Profile 快捷键（默认 Q/W/E/R/T/Y）：切换方案，轮盘保持打开
            int profile = this.profileKeyToIndex(keyCode);
            if (profile >= 0) {
                this.switchProfile(profile);
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

    /* ---------------- Profile ---------------- */

    private void reloadSlots() {
        this.slots.clear();
        int count = ModConfig.activeSlotCount();
        for (int i = 0; i < count; i++) {
            String id = ModConfig.getSlot(i);
            boolean empty = id == null || id.isEmpty();
            IAbility ability = empty ? null : AbilityRegistry.get(id);
            boolean locked = !empty && AbilityUnlocks.isReady()
                    && (ability == null || !AbilityUnlocks.isUnlocked(ability));
            String name;
            ItemStack icon;
            if (empty) {
                name = "";
                icon = ItemStack.EMPTY;
            } else if (ability != null) {
                name = ability.getDisplayName();
                icon = ability.getIcon();
            } else {
                name = prettifyId(id);
                icon = ItemStack.EMPTY;
            }
            this.slots.add(new SlotEntry(id, ability, empty, locked, name, icon));
        }
        this.activeAbility = ModConfig.getActiveAbility();
    }

    private static String prettifyId(String id) {
        if (id == null || id.isEmpty()) {
            return "?";
        }
        String path = id;
        int colon = path.indexOf(':');
        if (colon >= 0 && colon + 1 < path.length()) {
            path = path.substring(colon + 1);
        }
        String[] parts = path.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }

    /** 切换到指定 Profile（0-based），并重新装载该方案的槽位与选中能力。 */
    private void switchProfile(int profile) {
        if (profile < 0 || profile >= ModConfig.profileCount() || profile == ModConfig.activeProfile()) {
            return;
        }
        ModConfig.setActiveProfile(profile);
        this.reloadSlots();
        this.hoveredSector = -1;
        this.mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        LOGGER.info("[wheel] profile switched: {} ({})",
                ModConfig.activeProfile() + 1, ModConfig.getProfileName(ModConfig.activeProfile()));
    }

    private int profileKeyToIndex(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return -1;
        }
        int count = ModConfig.profileCount();
        for (int i = 0; i < count; i++) {
            if (resolveProfileKey(ModConfig.getProfileKeyName(i)) == keyCode) {
                return i;
            }
        }
        return -1;
    }

    /** 配置里允许写按键名（Q/W/E）或数字键码；无法解析时返回 KEY_NONE。 */
    private static int resolveProfileKey(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return Keyboard.KEY_NONE;
        }
        String value = configured.trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        int code = Keyboard.getKeyIndex(value.toUpperCase(Locale.ROOT));
        return code == Keyboard.KEY_NONE ? Keyboard.KEY_NONE : code;
    }

    /** 数字键映射到槽位：1..8 与数字小键盘 1..8（小键盘键码不连续，不能做减法）。 */
    private static int numberKeyToSlot(int keyCode) {
        if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_8) {
            return keyCode - Keyboard.KEY_1;
        }
        switch (keyCode) {
            case Keyboard.KEY_NUMPAD1: return 0;
            case Keyboard.KEY_NUMPAD2: return 1;
            case Keyboard.KEY_NUMPAD3: return 2;
            case Keyboard.KEY_NUMPAD4: return 3;
            case Keyboard.KEY_NUMPAD5: return 4;
            case Keyboard.KEY_NUMPAD6: return 5;
            case Keyboard.KEY_NUMPAD7: return 6;
            case Keyboard.KEY_NUMPAD8: return 7;
            default: return -1;
        }
    }

    private void updateHoveredTab(int mouseX, int mouseY) {
        int hovered = this.profileTabAt(mouseX, mouseY);
        this.hoveredProfile = hovered;
        if (hovered >= 0 && hovered != ModConfig.activeProfile()) {
            // 鼠标划到 Tab 上即切换（需求：悬停高亮 + 切换轮盘）
            this.switchProfile(hovered);
        }
    }

    private int profileTabAt(int mouseX, int mouseY) {
        int count = ModConfig.profileCount();
        for (int i = 0; i < count; i++) {
            int y = this.tabY + i * (this.tabH + this.tabGap);
            if (this.isInside(mouseX, mouseY, this.tabX, y, this.tabW, this.tabH)) {
                return i;
            }
        }
        return -1;
    }

    /* ---------------- 绘制 ---------------- */

    private void drawWheel(int mouseX, int mouseY) {
        /* 可用区域：左侧避开 Profile Tab，顶部避开搜索框/角落按钮，底部避开热键栏 */
        int topLimit = 52;
        int bottomLimit = this.height - 46;
        // 轮盘始终在屏幕水平居中；左侧 Profile Tab 只通过限制最大半径避免遮挡。
        int cx = this.width / 2;
        int cy = (topLimit + bottomLimit) / 2;
        int n = this.slots.size();

        int minDim = Math.min(this.width, this.height);
        int band = (int) Math.max(12, Math.min(26, minDim * 0.09F));
        int cellSize = (int) Math.max(8, Math.min(14, minDim * 0.05F));
        int usableHeight = bottomLimit - topLimit;
        int maxByHeight = Math.max(40, (usableHeight - 20) / 2 - band);
        int maxByWidth = Math.max(40, this.width / 2 - band - 12);
        int maxByTabs = Math.max(40, this.width / 2 - band - (this.tabX + this.tabW + 8));
        int ringRadius = Math.min(Math.min(Math.min(maxByHeight, maxByWidth), maxByTabs), (int) (minDim * 0.40F));
        this.wheelCx = cx;
        this.wheelCy = cy;
        this.wheelRadius = ringRadius;
        this.wheelBand = band;

        this.hoveredSector = -1;
        if (n > 0) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            // 只有落在环带（含格子外接）内才算悬停；中心区域无选中
            if (dist >= ringRadius - band - 8 && dist <= ringRadius + band + 8) {
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
        } else {
            double step = 360D / n;
            double gap = n >= 2 ? Math.min(2.0, step * 0.06) : 0.0;

            /* 当前选中项索引（按释放键触发的那个） */
            int activeIndex = -1;
            for (int i = 0; i < n; i++) {
                if (this.slots.get(i).id.equals(this.activeAbility)) {
                    activeIndex = i;
                    break;
                }
            }

            /* 环带：每格一段环形扇区，之间留缝（单个能力时整环无逢） */
            for (int i = 0; i < n; i++) {
                double a0 = -90 + i * step;
                double a1 = a0 + step;
                SlotEntry slot = this.slots.get(i);
                int color;
                if (i == this.hoveredSector) {
                    color = slot.empty ? 0x88606070 : slot.locked ? 0xCC8A4040 : 0xCC3AA5E0;
                } else if (slot.empty) {
                    color = 0x66101418;
                } else if (slot.locked) {
                    color = 0x881A1010;
                } else {
                    color = 0x88101428;
                }
                this.drawRingSegment(cx, cy, ringRadius - band, ringRadius + band, a0 + gap, a1 - gap, color);
            }

            /* 菱形格子 + 图标（单个能力时置顶） + 锁定覆盖 + 槽位号 */
            for (int i = 0; i < n; i++) {
                SlotEntry slot = this.slots.get(i);
                double mid = Math.toRadians(-90 + (n == 1 ? 0 : (i + 0.5)) * step);
                int ix = (int) (cx + Math.cos(mid) * ringRadius);
                int iy = (int) (cy + Math.sin(mid) * ringRadius);
                this.drawCell(ix, iy, cellSize, i == this.hoveredSector, i == activeIndex && !slot.empty, slot.locked);
                if (slot.empty) {
                    this.drawCenteredString(this.fontRenderer, "-", ix, iy - 4, 0x707078);
                } else {
                    this.renderIcon(slot.icon, ix - 8, iy - 8);
                    if (slot.locked) {
                        // 变暗 + 锁形标记：位置保留，仍然可以选中，但释放时会被拒绝
                        this.drawRect(ix - 8, iy - 8, ix + 8, iy + 8, 0x99200000);
                        this.drawLockIcon(ix, iy);
                    }
                }
                // 槽位号画在环带外沿，方便使用数字键快速选择
                double labelMid = Math.toRadians(-90 + (i + 0.5) * step);
                int lx = (int) (cx + Math.cos(labelMid) * (ringRadius + band + 9)) - 3;
                int ly = (int) (cy + Math.sin(labelMid) * (ringRadius + band + 9)) - 4;
                this.fontRenderer.drawStringWithShadow(String.valueOf(i + 1), lx, ly,
                        slot.empty ? 0x707078 : slot.locked ? 0xCC6666 : 0xFFFFFF);
            }

            /*
             * 中央区域：深色圆盘遮住背后的 Metamorph 形态名 HUD，避免两段长文本叠在一起。
             * 这里只放“简要名称 + 一行状态”，完整描述在右侧详情面板。
             */
            int innerRadius = ringRadius - band - 3;
            if (innerRadius > 16) {
                this.drawRingSegment(cx, cy, 0, innerRadius, 0, 360, 0xD80A0C14);
            }
            int centerMaxW = innerRadius > 20 ? innerRadius * 2 - 16 : this.width - 80;

            if (this.hoveredSector >= 0) {
                SlotEntry hovered = this.slots.get(this.hoveredSector);
                if (hovered.empty) {
                    this.drawCenteredString(this.fontRenderer,
                            I18n.translateToLocal("gui.morphext.wheel.empty_slot"), cx, cy - 10, 0xAAAAAA);
                    this.drawCenteredString(this.fontRenderer,
                            I18n.translateToLocal("gui.morphext.wheel.hint"), cx, cy + 2, 0x777780);
                } else {
                    String name = this.trimFont(hovered.displayName, centerMaxW);
                    this.drawCenteredString(this.fontRenderer, name, cx, cy - 10,
                            hovered.locked ? 0xCC7777 : 0xFFFFFF);
                    String sub = "";
                    int subColor = 0x808080;
                    if (hovered.locked) {
                        sub = I18n.translateToLocal("gui.morphext.wheel.locked.short");
                        subColor = 0xCC5555;
                    } else if (hovered.ability instanceof IStatefulAbility
                            && ((IStatefulAbility) hovered.ability).isStateToggleable()) {
                        sub = ((IStatefulAbility) hovered.ability).getStateLabel(this.mc.player);
                        subColor = 0x9AB8D8;
                    } else if (hovered.ability != null && !AbilityCooldowns.isReady(hovered.ability.getId())) {
                        sub = I18n.translateToLocalFormatted("gui.morphext.cooldown.short",
                                AbilityCooldowns.getRemainingText(hovered.ability.getId()));
                        subColor = 0xFF7755;
                    }
                    if (!sub.isEmpty()) {
                        this.drawCenteredString(this.fontRenderer, this.trimFont(sub, centerMaxW),
                                cx, cy + 2, subColor);
                    }
                }
            } else {
                SlotEntry selected = activeIndex >= 0 ? this.slots.get(activeIndex) : null;
                if (selected != null && !selected.empty) {
                    String name = this.trimFont(selected.displayName, centerMaxW);
                    this.drawCenteredString(this.fontRenderer, name, cx, cy - 10,
                            selected.locked ? 0xCC7777 : 0xFFFFC44D);
                    this.drawCenteredString(this.fontRenderer,
                            this.trimFont(I18n.translateToLocal(selected.locked
                                    ? "gui.morphext.wheel.locked.short"
                                    : "gui.morphext.wheel.selected"), centerMaxW),
                            cx, cy + 2, selected.locked ? 0xCC5555 : 0x808080);
                } else {
                    this.drawCenteredString(this.fontRenderer,
                            I18n.translateToLocal("gui.morphext.wheel.hint"), cx, cy - 4, 0x777780);
                }
            }

            int bound = 0;
            for (SlotEntry slot : this.slots) {
                if (!slot.empty) {
                    bound++;
                }
            }
            int infoY = Math.min(cy + ringRadius + band + 8, this.height - 48);
            this.drawCenteredString(this.fontRenderer,
                    I18n.translateToLocalFormatted("gui.morphext.wheel.count", bound, n)
                            + "  ·  " + I18n.translateToLocalFormatted("gui.morphext.wheel.profile",
                            ModConfig.getProfileName(ModConfig.activeProfile())),
                    cx, infoY, 0xFFFFFF);
        }

        // 搜索框（可见：背景 + 边框 + 占位提示）
        this.drawSearchBox();
    }

    private String trimFont(String text, int width) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (this.fontRenderer.getStringWidth(text) <= width) {
            return text;
        }
        return this.fontRenderer.trimStringToWidth(text, Math.max(8, width - 8)) + "…";
    }

    /** 详情面板使用的槽位：鼠标悬停优先，否则显示当前选中槽位。 */
    private SlotEntry detailSlot() {
        if (this.hoveredSector >= 0 && this.hoveredSector < this.slots.size()) {
            return this.slots.get(this.hoveredSector);
        }
        for (SlotEntry slot : this.slots) {
            if (!slot.empty && slot.id.equals(this.activeAbility)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 右侧详情面板：完整名称、状态、来源、技能描述/效果与 ID。
     * 轮盘中央只保留简要名称，避免 Metamorph 形态名 HUD 与长文本叠在一起。
     */
    private void drawDetailPanel() {
        int right = this.width - 12;
        // 给槽位图标/槽位号留出明显间距，避免详情面板贴住右侧槽位 2。
        int wheelRight = this.wheelCx + this.wheelRadius + this.wheelBand + 28;
        int available = right - wheelRight;
        int panelW = Math.min(220, available);
        int panelX = right - panelW;
        int panelY = 64;
        int panelH = this.height - panelY - 54;
        if (panelH < 80 || panelW < 120) {
            return;
        }

        this.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC0A0C14);
        this.drawRect(panelX, panelY, panelX + panelW, panelY + 1, 0xFF3A4A5E);
        this.drawRect(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF3A4A5E);
        this.drawRect(panelX, panelY, panelX + 1, panelY + panelH, 0xFF3A4A5E);
        this.drawRect(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF3A4A5E);

        int x = panelX + 8;
        int y = panelY + 8;
        int w = panelW - 16;
        int maxY = panelY + panelH - 12;
        this.fontRenderer.drawStringWithShadow(I18n.translateToLocal("gui.morphext.wheel.details.title"),
                x, y, 0x9AB8D8);
        y += 13;
        this.drawRect(x, y, x + w, y + 1, 0x663A4A5E);
        y += 6;

        SlotEntry slot = this.detailSlot();
        if (slot == null) {
            this.drawWrappedText(I18n.translateToLocal("gui.morphext.wheel.details.empty"),
                    x, y, w, maxY, 0x909090);
            return;
        }
        if (slot.empty) {
            this.drawWrappedText(I18n.translateToLocal("gui.morphext.wheel.details.empty_slot"),
                    x, y, w, maxY, 0x909090);
            return;
        }

        y = this.drawWrappedText(slot.displayName, x, y, w, Math.min(maxY, y + 22),
                slot.locked ? 0xDD8888 : 0xFFFFFF);
        y += 3;

        if (slot.locked) {
            y = this.drawWrappedText(I18n.translateToLocal("gui.morphext.wheel.details.locked"),
                    x, y, w, maxY, 0xCC7777);
            y += 4;
        } else {
            int cd = AbilityCooldowns.getRemaining(slot.id);
            if (slot.ability != null) {
                cd = Math.max(cd, slot.ability.getExternalCooldownTicks(this.mc.player));
            }
            if (cd > 0) {
                y = this.drawWrappedText(I18n.translateToLocalFormatted("gui.morphext.wheel.details.cooldown",
                                String.format(java.util.Locale.ROOT, "%.1f", cd / 20F)),
                        x, y, w, maxY, 0xFFAA66);
                y += 4;
            } else if (slot.ability instanceof IStatefulAbility
                    && ((IStatefulAbility) slot.ability).isStateToggleable()) {
                y = this.drawWrappedText(I18n.translateToLocalFormatted("gui.morphext.wheel.details.state",
                                ((IStatefulAbility) slot.ability).getStateLabel(this.mc.player)),
                        x, y, w, maxY, 0x9AB8D8);
                y += 4;
            }
        }

        if (slot.ability instanceof aaaa0ggmc.morphext.integration.metamorph.MorphGrantedAbility) {
            String source = ((aaaa0ggmc.morphext.integration.metamorph.MorphGrantedAbility) slot.ability)
                    .getStatusGroup();
            if (source != null && !source.isEmpty()) {
                y = this.drawWrappedText(I18n.translateToLocalFormatted("gui.morphext.wheel.details.source", source),
                        x, y, w, maxY, 0x8A9AA8);
                y += 2;
            }
        }

        if (slot.ability != null) {
            String description = slot.ability.getDescription();
            if (description != null && !description.isEmpty()) {
                y = this.drawWrappedText(description, x, y, w, maxY, 0xC8C8D0);
            }
            if (slot.ability instanceof IStatefulAbility) {
                List<String> info = ((IStatefulAbility) slot.ability).getStateInfo(this.mc.player);
                if (info != null) {
                    for (String line : info) {
                        if (y >= maxY) break;
                        y = this.drawWrappedText(line, x, y, w, maxY, 0xA8A8B0);
                    }
                }
            }
        }

        String idLine = I18n.translateToLocalFormatted("gui.morphext.wheel.details.id", slot.id);
        this.fontRenderer.drawStringWithShadow(this.trimFont(idLine, w), x, panelY + panelH - 12, 0x606068);
    }

    /** 自动按面板宽度换行并限制最大高度；超出时以省略号收尾。 */
    private int drawWrappedText(String text, int x, int y, int width, int maxY, int color) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                y += 3;
                continue;
            }
            List<String> lines = this.fontRenderer.listFormattedStringToWidth(paragraph, Math.max(8, width));
            if (lines == null || lines.isEmpty()) {
                y += 3;
                continue;
            }
            for (String line : lines) {
                if (line == null || line.isEmpty()) {
                    y += 3;
                    continue;
                }
                if (y + 10 > maxY) {
                    this.fontRenderer.drawStringWithShadow("…", x, y, color);
                    return y + 10;
                }
                this.fontRenderer.drawStringWithShadow(line, x, y, color);
                y += 10;
            }
        }
        return y;
    }

    private void drawProfileTabs(int mouseX, int mouseY) {
        int count = ModConfig.profileCount();
        for (int i = 0; i < count; i++) {
            int y = this.tabY + i * (this.tabH + this.tabGap);
            boolean active = i == ModConfig.activeProfile();
            boolean hover = i == this.hoveredProfile;
            int bg = active ? 0xDD2F6F9F : hover ? 0xAA3A4A5E : 0x77101418;
            this.drawRect(this.tabX, y, this.tabX + this.tabW, y + this.tabH, bg);
            this.drawRect(this.tabX, y, this.tabX + 2, y + this.tabH,
                    active ? 0xFF7FD4FF : hover ? 0xAA7FD4FF : 0x55607080);
            String label = profileTabLabel(i);
            if (this.fontRenderer.getStringWidth(label) > this.tabW - 10) {
                label = this.fontRenderer.trimStringToWidth(label, this.tabW - 12) + "…";
            }
            this.fontRenderer.drawStringWithShadow(label, this.tabX + 6, y + (this.tabH - 8) / 2,
                    active ? 0xFFFFFF : 0xC8C8D0);
        }
    }

    private String profileTabLabel(int profile) {
        String keyName = profileKeyLabel(profile);
        String name = ModConfig.getProfileName(profile);
        return keyName.isEmpty()
                ? name
                : I18n.translateToLocalFormatted("gui.morphext.profile.tab", keyName, name);
    }

    private static String profileKeyLabel(int profile) {
        int code = resolveProfileKey(ModConfig.getProfileKeyName(profile));
        if (code == Keyboard.KEY_NONE) {
            return "";
        }
        String name = Keyboard.getKeyName(code);
        return name == null ? "" : name;
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
    private void drawCell(int cx, int cy, int s, boolean highlight, boolean active, boolean locked) {
        float r, g, b, a;
        if (highlight) {
            r = 0.35F; g = 0.72F; b = 0.94F; a = 0.87F;
        } else if (active) {
            r = 0.95F; g = 0.72F; b = 0.20F; a = 0.85F;
        } else if (locked) {
            r = 0.32F; g = 0.12F; b = 0.12F; a = 0.70F;
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

    /** 在图标中央画一个小锁：锁体 + 半圆锁梁，避免额外贴图依赖。 */
    private void drawLockIcon(int cx, int cy) {
        int body = 0xFFFFCC44;
        // 锁梁（左右立柱 + 顶部横梁）
        this.drawRect(cx - 3, cy - 5, cx - 1, cy + 1, body);
        this.drawRect(cx + 1, cy - 5, cx + 3, cy + 1, body);
        this.drawRect(cx - 3, cy - 6, cx + 3, cy - 5, body);
        // 锁体
        this.drawRect(cx - 5, cy - 1, cx + 5, cy + 6, 0xFFFFAA22);
        this.drawRect(cx - 1, cy + 1, cx + 1, cy + 4, 0xFF6A3A00);
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

            boolean isStateful = ability instanceof IStatefulAbility;
            int btnW = 76;
            int btnH = rowH - 4;
            int btnX = x + listW - btnW - 3;
            int btnY = ry + 2;

            if (isStateful) {
                IStatefulAbility stateful = (IStatefulAbility) ability;
                String stateText = stateful.getStateLabel(this.mc.player);
                boolean active = stateful.isActive(this.mc.player);
                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                int btnBg = active
                        ? (btnHover ? 0xEE43A047 : 0xCC2E7D32)
                        : (btnHover ? 0x88666666 : 0x55333333);
                this.drawRect(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                this.drawRect(btnX, btnY, btnX + btnW, btnY + 1, 0x44FFFFFF);
                this.drawRect(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0x44000000);

                if (this.fontRenderer.getStringWidth(stateText) > btnW - 4) {
                    stateText = this.fontRenderer.trimStringToWidth(stateText, btnW - 6) + "…";
                }
                this.drawCenteredString(this.fontRenderer, stateText, btnX + btnW / 2, btnY + 4, active ? 0xAAFFAA : 0xDDDDDD);
            }

            int textMaxW = isStateful ? listW - 24 - btnW - 8 : listW - 44;
            String name = ability.getDisplayName();
            if (this.fontRenderer.getStringWidth(name) > textMaxW) {
                name = this.fontRenderer.trimStringToWidth(name, textMaxW - 4) + "…";
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

    /** 选中槽位（重复点击/数字键都走这里）。锁定槽位同样可以选中，释放时再提示。 */
    private boolean selectSlot(int index) {
        if (index < 0 || index >= this.slots.size()) {
            return false;
        }
        SlotEntry slot = this.slots.get(index);
        if (slot.empty) {
            return false;
        }
        ModConfig.setActiveAbility(slot.id);
        this.activeAbility = slot.id;
        this.mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        LOGGER.info("[wheel] slot-select: {} ({}{})", slot.id, index + 1, slot.locked ? ", locked" : "");
        return true;
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

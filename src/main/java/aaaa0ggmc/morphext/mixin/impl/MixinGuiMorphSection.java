package aaaa0ggmc.morphext.mixin.impl;

import aaaa0ggmc.morphext.client.RenderUtil;
import aaaa0ggmc.morphext.client.render.MorphThumbnailCache;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Keys;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiMorphSection;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 注入 Metamorph GuiMorphSection：
 * 1. 接管 drawMorph：普通格子走 2D 缩略图贴图缓存，悬停/选中回退 60fps 实时 3D；
 * 2. 接管 drawMorphs：虚拟列表滚动（只渲染当前可见行，跳过不可见模型循环）；
 * 3. 沿用原版筛选和高度计算，避免收藏/折叠/编辑后缓存失效遗漏；
 * 4. 全量采用 @Inject，规避 Cleanroom 0.6.13-alpha 下 MixinExtras 的 Redirect 强转报错。
 */
@Mixin(value = GuiMorphSection.class, remap = false)
public abstract class MixinGuiMorphSection extends GuiElement {

    @Shadow public MorphSection section;
    @Shadow public AbstractMorph hoverMorph;
    @Shadow public MorphCategory hoverCategory;
    @Shadow public AbstractMorph morph;
    @Shadow public String filter;
    @Shadow public boolean favorite;
    @Shadow public int cellWidth;
    @Shadow public int cellHeight;
    @Shadow public boolean last;

    @Shadow public abstract int getPerRow();
    @Shadow public abstract boolean noFilter();
    @Shadow public abstract boolean isMatching(AbstractMorph morph);

    @Unique private static final int MORPHEXT_HEADER_HEIGHT = 20;
    @Unique private static final int MORPHEXT_CATEGORY_HEIGHT = 16;


    public MixinGuiMorphSection() {
        super(null);
    }

    /**
     * 接管单个格子的渲染，纯 @Inject 实现
     */
    @Inject(method = "drawMorph", at = @At("HEAD"), cancellable = true)
    private void morphext$onDrawMorph(GuiContext context, AbstractMorph targetMorph, int x, int y, int w, int h, boolean hover, boolean selected, CallbackInfo ci) {
        ci.cancel();

        if (selected && !targetMorph.errorRendering) {
            Gui.drawRect(x, y, x + w, y + h, 0xaa000000 + McLib.primaryColor.get());
        } else if (hover) {
            Gui.drawRect(x, y, x + w, y + h, 0x66000000);
        }

        int spot = (int) (w * 0.4F);
        int spotX = x + w / 2;
        int spotY = y + h / 2;

        GuiDraw.drawDropCircleShadow(spotX, spotY, spot, (int) (spot * 0.65F), 10, 0x44000000, 0);

        if (targetMorph.errorRendering) {
            GuiDraw.drawOutline(x, y, x + w, y + h, 0x88ff0000, 4);
            GuiDraw.drawOutline(x, y, x + w, y + h, 0xffff0000, 2);
            return;
        }

        // 核心优化：智能缩略图/实时 3D 渲染
        if (!MorphThumbnailCache.drawMorphSmart(targetMorph, this.mc, x, y, w, h, hover, selected)) {
            return;
        }

        if (selected) {
            GuiDraw.drawOutline(x, y, x + w, y + h, 0xff000000 + McLib.primaryColor.get(), 2);
        }

        if (targetMorph.keybind != -1) {
            String key = Keys.getKeyName(targetMorph.keybind);
            int kw = this.font.getStringWidth(key);
            int kx = x + w - 6 - kw;
            int ky = y + h - 6 - this.font.FONT_HEIGHT;

            Gui.drawRect(kx - 3, ky - 2, kx + kw + 3, ky + this.font.FONT_HEIGHT + 2, 0xff000000);
            this.font.drawStringWithShadow(key, kx, ky, 0xffffff);
        }

        if (targetMorph.favorite) {
            RenderUtil.resetBlendFunc();
            GuiDraw.drawOutlinedIcon(Icons.FAVORITE, x + 2, y + 2, 0xffffffff);
        }
    }

    /**
     * 获取指定分类下缓存的筛选后列表
     */
    @Unique
    private List<AbstractMorph> morphext$getFilteredMorphs(MorphCategory category) {
        List<AbstractMorph> original = category.getMorphs();
        if (this.noFilter()) return original;
        List<AbstractMorph> filtered = new ArrayList<>();
        for (AbstractMorph candidate : original) {
            if (this.isMatching(candidate)) filtered.add(candidate);
        }
        return filtered;
    }

    /**
     * 接管 drawMorphs：虚拟列表滚动优化，只计算和渲染落在当前视口内的行
     */
    @Inject(method = "drawMorphs", at = @At("HEAD"), cancellable = true)
    private void morphext$optimizedDrawMorphs(GuiContext context, CallbackInfo ci) {
        ci.cancel();

        if (this.section.categories.isEmpty()) {
            return;
        }

        /* 绘制大分类标题 */
        Icon toggleIcon = this.section.hidden ? Icons.MOVE_DOWN : Icons.MOVE_UP;
        Gui.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.y + MORPHEXT_HEADER_HEIGHT, 0xbb000000);
        this.font.drawStringWithShadow(this.section.getTitle(), this.area.x + 7, this.area.y + 10 - this.font.FONT_HEIGHT / 2, 0xffffff);
        toggleIcon.render(this.area.ex() - 18 - 3, this.area.y + 10 + (this.section.hidden ? 1 : -1), 0, 0.5F);

        int y = MORPHEXT_HEADER_HEIGHT;
        this.hoverMorph = null;
        this.hoverCategory = null;

        if (this.section.hidden) {
            return;
        }

        Area viewport = context.getViewport();
        int row = this.getPerRow();
        float cellWFloat = this.area.w / (float) row;

        for (MorphCategory category : this.section.categories) {
            List<AbstractMorph> morphs = this.morphext$getFilteredMorphs(category);
            int count = morphs.size();

            int catHeaderY = this.area.y + y;

            // 仅在标题落在视口内时绘制分类标题与图标
            if (catHeaderY + MORPHEXT_CATEGORY_HEIGHT >= viewport.y && catHeaderY <= viewport.ey()) {
                GuiDraw.drawTextBackground(this.font, category.getTitle(), this.area.x + 7, catHeaderY + 8 - this.font.FONT_HEIGHT / 2, 0xeeeeee, ColorUtils.HALF_BLACK, 2);
                (category.hidden ? Icons.MOVE_DOWN : Icons.MOVE_UP).render(this.area.ex() - 18 - 3, catHeaderY + MORPHEXT_CATEGORY_HEIGHT / 2 + (category.hidden ? 1 : -1), 0, 0.5F);
            }

            // 悬停分类判断
            int catRows = (Math.max(count, 1) + row - 1) / row;
            int totalCatGridH = catRows * this.cellHeight;
            Area.SHARED.copy(this.area);
            Area.SHARED.y = catHeaderY;
            Area.SHARED.h = MORPHEXT_CATEGORY_HEIGHT + totalCatGridH;
            if (Area.SHARED.isInside(context.mouseX, context.mouseY)) {
                this.hoverCategory = category;
            }

            y += MORPHEXT_CATEGORY_HEIGHT + 5;

            if (category.isHidden() || (count == 0 && !this.noFilter())) {
                continue;
            }

            int catGridStartY = this.area.y + y;
            int catGridEndY = catGridStartY + totalCatGridH;

            // 视口剔除：若整个分类格子都在视口上方，直接跳过整个循环
            if (catGridEndY < viewport.y) {
                y += totalCatGridH + 5;
                continue;
            }

            // 若整个分类格子都在视口下方，直接结束后续分类
            if (catGridStartY > viewport.ey()) {
                break;
            }

            // 计算当前分类下可见行的起止行号 (Virtual Window)
            int startRow = Math.max(0, (viewport.y - catGridStartY) / this.cellHeight);
            int endRow = Math.min(catRows, (viewport.ey() - catGridStartY) / this.cellHeight + 1);

            int startIdx = startRow * row;
            int endIdx = Math.min(count, endRow * row);

            for (int idx = startIdx; idx < endIdx; idx++) {
                AbstractMorph m = morphs.get(idx);
                int curRow = idx / row;
                int curCol = idx % row;

                int mx = this.area.x + Math.round(curCol * cellWFloat);
                int my = this.area.y + y + curRow * this.cellHeight;
                int w = Math.round((curCol + 1) * cellWFloat) - Math.round(curCol * cellWFloat);

                Area.SHARED.set(mx, my, w, this.cellHeight);
                if (Area.SHARED.isInside(context.mouseX, context.mouseY)) {
                    this.hoverMorph = m;
                }

                if (Area.SHARED.intersects(viewport)) {
                    GuiDraw.scissor(mx, my, w, this.cellHeight, context);
                    // 内部调用的 drawMorph 已经被上面的 @Inject 接管走缩略图/智能 3D
                    this.drawMorph(context, m, mx, my, w, this.cellHeight, this.hoverMorph == m, this.morph == m);
                    GuiDraw.unscissor(context);
                }
            }

            y += totalCatGridH + 5;
        }
    }

    @Shadow
    protected abstract void drawMorph(GuiContext context, AbstractMorph morph, int x, int y, int w, int h, boolean hover, boolean selected);
}

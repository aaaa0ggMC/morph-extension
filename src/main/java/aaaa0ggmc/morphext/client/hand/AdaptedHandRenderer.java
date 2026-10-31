package aaaa0ggmc.morphext.client.hand;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;

/**
 * 通用第一人称智能手部渲染包装器 (Adapted Hand Renderer)。
 *
 * 核心机制：
 * 1. 继承 ModelRenderer，作为 Metamorph morph.rightHand / morph.leftHand 的透明代理；
 * 2. 在第一人称视角下，通过分析被包装骨骼的 AABB 空间包围盒，智能计算：
 *    - 姿态反转：对负 Y 轴向上生长（如法杖、立式触手、书本）的骨骼进行 180° X 轴智能旋转，使其由视口右下向内伸入；
 *    - 方向前倾：对前冲扑咬（-Z 轴延伸）的骨骼智能旋转 90°，将其适配为前臂朝向；
 *    - 视口等比缩放：将巨型首领骨骼（>15 格）等比收敛至约 13~14 格，杜绝全屏遮挡与穿模；
 *    - 视口重心中正：根据骨骼厚度与握持点自动偏移至第一人称握持视线位置；
 * 3. 完整支持来自 HandPreset 与 morph_abilities.json 的微调覆写。
 */
public class AdaptedHandRenderer extends ModelRenderer {

    private final ModelRenderer original;
    private final boolean isRightHand;
    private final HandPreset preset;
    private final ModelBounds rootBounds;
    private final ModelBounds totalBounds;

    public AdaptedHandRenderer(ModelBase model, ModelRenderer original, boolean isRightHand, HandPreset preset) {
        super(model);
        this.original = original;
        this.isRightHand = isRightHand;
        this.preset = preset;
        this.rootBounds = ModelBoundsCalculator.computeRootBounds(original);
        this.totalBounds = ModelBoundsCalculator.computeTotalBounds(original);

        this.textureWidth = original.textureWidth;
        this.textureHeight = original.textureHeight;
        this.mirror = original.mirror;
    }

    public ModelRenderer getOriginal() {
        return original;
    }

    @Override
    public void render(float scale) {
        if (this.isHidden || !this.showModel || original == null) {
            return;
        }

        GlStateManager.pushMatrix();

        // 1. Metamorph 手臂根部锚点位移 (右手通常在 -6, 4, 0; 左手在 +6, 4, 0)
        GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);

        // 2. 启发式智能比例缩放 (Scale Normalization)
        float appliedScale = 1.0F;
        float maxSpan = totalBounds.valid ? totalBounds.maxSpan() : 12.0F;

        if (preset != null && preset.scale > 0.001F) {
            appliedScale = preset.scale;
        } else if (maxSpan > 15.0F) {
            // 针对过长/过大肢体（如 Jzahar 28格巨杖、铁傀儡 30格手臂），等比收敛至标准第一人称 13.5 格视野
            appliedScale = 13.5F / maxSpan;
        } else if (maxSpan < 5.0F && maxSpan > 0.1F) {
            // 针对过小肢体（微型节肢），适度放大以清晰呈现
            appliedScale = Math.min(1.8F, 8.5F / maxSpan);
        }

        if (appliedScale != 1.0F) {
            GlStateManager.scale(appliedScale, appliedScale, appliedScale);
        }

        // 3. 启发式智能姿态纠偏 (Orientation & Rotations)
        float rotX = 0.0F;
        float rotY = 0.0F;
        float rotZ = 0.0F;

        boolean isNegativeYDominant = false;

        if (preset != null && (preset.extraRotationX != 0 || preset.extraRotationY != 0 || preset.extraRotationZ != 0)) {
            rotX = (float) Math.toDegrees(preset.extraRotationX);
            rotY = (float) Math.toDegrees(preset.extraRotationY);
            rotZ = (float) Math.toDegrees(preset.extraRotationZ);
        } else if (rootBounds.valid) {
            float spanX = rootBounds.spanX();
            float spanY = rootBounds.spanY();
            float spanZ = rootBounds.spanZ();

            // 规则 A：骨骼在模型中沿负 Y 轴向上生长（如法杖、立持道具、书籍）
            // 在第一人称视锥中，负 Y 会被摄像机翻转到头顶与视野后方！
            if (spanY >= spanX && spanY >= spanZ && rootBounds.minY < -6.0F && rootBounds.maxY <= 3.0F) {
                rotX += 180.0F;
                isNegativeYDominant = true;
            }
            // 规则 B：沿 -Z 水平向前伸展（如四足前肢扑跃形态、修格斯触肢）
            else if (spanZ > spanY && spanZ > spanX && rootBounds.minZ < -4.0F) {
                rotX += 90.0F;
            }
        }

        if (rotX != 0.0F) GlStateManager.rotate(rotX, 1.0F, 0.0F, 0.0F);
        if (rotY != 0.0F) GlStateManager.rotate(rotY, 0.0F, 1.0F, 0.0F);
        if (rotZ != 0.0F) GlStateManager.rotate(rotZ, 0.0F, 0.0F, 1.0F);

        // 4. 启发式智能重心偏移 (Centering & Offsets)
        float transX = 0.0F;
        float transY = 0.0F;
        float transZ = 0.0F;

        if (preset != null && (preset.offsetX != 0 || preset.offsetY != 0 || preset.offsetZ != 0)) {
            transX = preset.offsetX;
            transY = preset.offsetY;
            transZ = preset.offsetZ;
        } else if (rootBounds.valid) {
            // 对齐到第一人称主干线：右手瞄准中心 X ≈ -1.0F，左手瞄准中心 X ≈ +1.0F
            float targetCenterX = isRightHand ? -1.0F : 1.0F;
            float rootCenterX = rootBounds.centerX();
            transX = targetCenterX - rootCenterX;

            if (isNegativeYDominant) {
                // 若翻转了 180°，原始骨骼底部在 rootBounds.maxY，翻转后对齐至手腕 Y ≈ 0.0F
                transY = -rootBounds.maxY;
            } else {
                // 正常骨骼：将顶部关节对齐到 Y ≈ -2.0F
                transY = -rootBounds.minY - 2.0F;
            }
        }

        if (transX != 0.0F || transY != 0.0F || transZ != 0.0F) {
            GlStateManager.translate(transX * scale, transY * scale, transZ * scale);
        }

        // 5. 渲染实际原版骨骼部件与子部件
        float origRpx = original.rotationPointX;
        float origRpy = original.rotationPointY;
        float origRpz = original.rotationPointZ;
        float origRax = original.rotateAngleX;
        float origRay = original.rotateAngleY;
        float origRaz = original.rotateAngleZ;

        // 隔离原骨骼在全身视角下的身体位移，由本适配器全权接管第一人称视口
        original.rotationPointX = 0;
        original.rotationPointY = 0;
        original.rotationPointZ = 0;
        original.rotateAngleX = 0;
        original.rotateAngleY = 0;
        original.rotateAngleZ = 0;

        try {
            original.render(scale);
        } finally {
            original.rotationPointX = origRpx;
            original.rotationPointY = origRpy;
            original.rotationPointZ = origRpz;
            original.rotateAngleX = origRax;
            original.rotateAngleY = origRay;
            original.rotateAngleZ = origRaz;
            GlStateManager.popMatrix();
        }
    }
}

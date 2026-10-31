package aaaa0ggmc.morphext.client.hand;

import net.minecraft.client.model.ModelRenderer;

/**
 * 查找到的左右手 ModelRenderer 部件及关联的渲染预设。
 */
public class HandPair {
    public final ModelRenderer rightHand;
    public final ModelRenderer leftHand;
    public final HandPreset preset;

    public HandPair(ModelRenderer rightHand, ModelRenderer leftHand, HandPreset preset) {
        this.rightHand = rightHand;
        this.leftHand = leftHand;
        this.preset = preset;
    }

    public boolean hasHands() {
        return rightHand != null || leftHand != null;
    }
}

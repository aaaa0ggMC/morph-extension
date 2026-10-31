package aaaa0ggmc.morphext.mixin.impl;

import aaaa0ggmc.morphext.integration.metamorph.MorphAnimationRegistry;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 Metamorph Morphing capability：
 * 在 setMorph 执行完毕后，根据 MorphAnimationRegistry 动态决定是否压制动画。
 * 彻底消除动作引发的同生物微调（如羊吃草、羊剪毛、潜影贝闭壳等）意外触发 20-tick 翻转与爆炸粒子的违和体验。
 */
@Mixin(value = Morphing.class, remap = false)
public abstract class MixinMorphing implements IMorphing {

    @Shadow private int animation;
    @Shadow private AbstractMorph previousMorph;
    @Shadow private Morph morph;

    @Inject(method = "setMorph", at = @At("RETURN"))
    private void morphext$onSetMorph(AbstractMorph newMorph, CallbackInfo ci) {
        if (this.animation > 0) {
            if (MorphAnimationRegistry.shouldSuppressAnimation(this.previousMorph, newMorph)) {
                this.animation = -1;
                this.previousMorph = null;
            }
        }
    }
}

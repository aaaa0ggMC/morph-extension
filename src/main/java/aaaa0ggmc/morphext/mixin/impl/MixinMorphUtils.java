package aaaa0ggmc.morphext.mixin.impl;

import aaaa0ggmc.morphext.client.render.SafeMorphRenderer;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 Metamorph MorphUtils：
 * 在 HEAD 处直接接入 SafeMorphRenderer，杜绝 IllegalStateException 异常风暴。
 * 使用基础 @Inject 实现，完全避免 Cleanroom 下 MixinExtras 的 Redirect ClassCastException。
 */
@Mixin(value = MorphUtils.class, remap = false)
public abstract class MixinMorphUtils {

    @Inject(method = "renderOnScreen", at = @At("HEAD"), cancellable = true)
    private static void morphext$onRenderOnScreen(AbstractMorph morph, EntityPlayer player, int x, int y, float scale, float alpha, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(SafeMorphRenderer.renderOnScreen(morph, player, x, y, scale, alpha));
    }

    @Inject(method = "renderDirect", at = @At("HEAD"), cancellable = true)
    private static void morphext$onRenderDirect(AbstractMorph morph, EntityLivingBase entity, double x, double y, double z, float yaw, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(SafeMorphRenderer.renderDirect(morph, entity, x, y, z, yaw, partialTick));
    }
}

package aaaa0ggmc.morphext.mixin.impl;

import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.vanilla_pack.morphs.ShulkerMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 Metamorph ShulkerMorph：
 * 彻底消除对变身玩家的 blockPos 原地定死锁定，允许玩家自由浮空、移动与瞬移脱困。
 */
@Mixin(value = ShulkerMorph.class, remap = false)
public abstract class MixinShulkerMorph extends EntityMorph {

    @Shadow public BlockPos blockPos;

    @Inject(method = "morph", at = @At("RETURN"))
    private void morphext$onMorph(EntityLivingBase target, CallbackInfo ci) {
        if (target instanceof EntityPlayer) {
            this.blockPos = null;
        }
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void morphext$onUpdate(EntityLivingBase target, CallbackInfo ci) {
        if (target instanceof EntityPlayer) {
            this.blockPos = null;
        }
    }
}

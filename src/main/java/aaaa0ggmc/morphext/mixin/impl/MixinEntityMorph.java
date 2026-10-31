package aaaa0ggmc.morphext.mixin.impl;

import aaaa0ggmc.morphext.client.render.GenericMorphRenderRegistry;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.bodypart.BodyPartManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 Metamorph EntityMorph.render：
 * 接管变身形态在世界中的 3D 渲染，接入 GenericMorphRenderRegistry 统一偏航旋转注册中心。
 * 
 * 彻底解决特定生物（如深渊国度阿索拉 Asorah / EntityDragonBoss）因未被 Metamorph 硬编码为 EntityDragon
 * 而导致变身时模型方向刚好反转 180° 的问题。
 */
@Mixin(value = EntityMorph.class, remap = false)
public abstract class MixinEntityMorph extends AbstractMorph {

    @Shadow public RenderLivingBase renderer;
    @Shadow protected EntityLivingBase entity;
    @Shadow public BodyPartManager parts;
    @Shadow public float scale;
    @Shadow public static EntityLivingBase renderEntity;

    @Shadow public abstract EntityLivingBase getEntity(World world);
    @Shadow public abstract void setupLimbs();
    @Shadow protected abstract void setupBodyPart();
    @Shadow private void replaceUserTexture() {}
    @Shadow private void restoreMobTexture() {}

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void morphext$onRender(EntityLivingBase hostEntity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (hostEntity == null) {
            ci.cancel();
            return;
        }

        RenderLivingBase render = this.renderer;
        if (render == null) {
            this.getEntity(hostEntity.world);
            render = this.renderer;
        }

        if (render != null && this.entity != null) {
            /* Make transformation seamless */
            this.entity.rotationYaw = hostEntity.rotationYaw;
            this.entity.rotationPitch = hostEntity.rotationPitch;
            this.entity.rotationYawHead = hostEntity.rotationYawHead;
            this.entity.renderYawOffset = hostEntity.renderYawOffset;

            this.entity.prevRotationYaw = hostEntity.prevRotationYaw;
            this.entity.prevRotationPitch = hostEntity.prevRotationPitch;
            this.entity.prevRotationYawHead = hostEntity.prevRotationYawHead;
            this.entity.prevRenderYawOffset = hostEntity.prevRenderYawOffset;

            this.parts.initBodyParts();
            if (!this.parts.parts.isEmpty()) {
                this.setupLimbs();
            }

            boolean wasSneak = false;
            ModelBase model = render.getMainModel();
            if (model instanceof ModelBiped) {
                wasSneak = ((ModelBiped) model).isSneak;
                ((ModelBiped) model).isSneak = hostEntity.isSneaking();
            }

            renderEntity = hostEntity;
            this.setupBodyPart();
            this.replaceUserTexture();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.scale(this.scale, this.scale, this.scale);

            // 通用形态旋转判定：支持 EntityDragon 以及 GenericMorphRenderRegistry 注册的形态与实体
            float yawOffset = GenericMorphRenderRegistry.getYawOffset(this.name, this.entity);
            if (yawOffset != 0.0F) {
                GlStateManager.rotate(yawOffset, 0.0F, 1.0F, 0.0F);
            }

            Minecraft.getMinecraft().getRenderManager().renderEntity(this.entity, 0.0D, 0.0D, 0.0D, entityYaw, partialTicks, false);

            GlStateManager.popMatrix();

            this.restoreMobTexture();

            if (model instanceof ModelBiped) {
                ((ModelBiped) model).isSneak = wasSneak;
            }

            renderEntity = null;
        }

        ci.cancel();
    }

    @Inject(method = "finishInitializingSettings", at = @At("RETURN"))
    private void morphext$onFinishInitializingSettings(CallbackInfo ci) {
        if (this.settings != null) {
            aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition def =
                    aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig.get(this.name);
            if (def != null && def.speed != null) {
                this.settings.speed = def.speed;
                this.settings.hasSpeed = true;
            } else if (this.settings.speed <= 0.001F) {
                this.settings.speed = 0.08F;
                this.settings.hasSpeed = true;
            }
        }
    }
}

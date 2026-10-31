package aaaa0ggmc.morphext.client.hand;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.events.RegisterSettingsEvent;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * 通用第一人称手部渲染拦截与骨骼注入控制器 (Generic Hand Adapter)。
 *
 * 1. 自动点亮手部开关：在 Metamorph 注册/重载设置时，对所有可识别手部的形态自动标记 settings.hands = true。
 * 2. 运行时动态注入：在第一人称渲染前，对非 Biped 实体模型注入经过语义扫描或显式配置的正确左右手骨骼部件。
 * 3. 第一人称位姿纠偏：支持旋转与平移修正，确保特殊生物肢体在第一人称视角下挥舞自然、不穿模。
 */
@SideOnly(Side.CLIENT)
public final class GenericHandAdapter {
    private static final Logger LOGGER = LogManager.getLogger("morphext-hand-adapter");
    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new GenericHandAdapter());
        LOGGER.info("[morphext] Generic Hand Adaptation Engine initialized.");
    }

    /**
     * 在 Metamorph 设置加载/重载时，为所有支持手部的生物自动激活 hands = true 开关。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRegisterSettings(RegisterSettingsEvent event) {
        if (event == null || event.settings == null) return;

        int activatedCount = 0;
        for (Map.Entry<String, MorphSettings> entry : event.settings.entrySet()) {
            String name = entry.getKey();
            MorphSettings settings = entry.getValue();
            if (settings == null) continue;

            // 若形态已经开启了 hands 则跳过
            if (settings.hands) continue;

            if (GenericHandRegistry.hasPreset(name)) {
                settings.hands = true;
                settings.hasHands = true;
                activatedCount++;
            }
        }
        if (activatedCount > 0) {
            LOGGER.info("[morphext] Auto-activated hands for {} morph settings.", activatedCount);
        }
    }

    /**
     * 第一人称手部渲染前拦截：确保当前 Morph 注入了最精准的手部骨骼部件。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderSpecificHandPre(RenderSpecificHandEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        IMorphing cap = Morphing.get(player);
        if (cap == null || !cap.isMorphed()) return;

        AbstractMorph currentMorph = cap.getCurrentMorph();
        if (!(currentMorph instanceof EntityMorph)) return;

        EntityMorph morph = (EntityMorph) currentMorph;
        adaptMorphHands(morph);
    }

    public static void adaptMorphHands(EntityMorph morph) {
        if (morph == null) return;

        if (morph.renderer == null && Minecraft.getMinecraft().world != null) {
            try {
                morph.getEntity(Minecraft.getMinecraft().world);
            } catch (Throwable ignored) {
            }
        }
        if (morph.renderer == null) return;

        // 若主模型是标准人类 ModelBiped，Metamorph 原生处理非常良好，无需干预
        if (morph.renderer.getMainModel() instanceof ModelBiped) {
            return;
        }

        HandPair pair = GenericHandRegistry.findHands(morph);
        if (pair.hasHands()) {
            net.minecraft.client.model.ModelRenderer rawRight = pair.rightHand != null ? pair.rightHand : pair.leftHand;
            net.minecraft.client.model.ModelRenderer rawLeft = pair.leftHand != null ? pair.leftHand : pair.rightHand;

            net.minecraft.client.model.ModelBase model = morph.renderer.getMainModel();

            // 若尚未经过通用智能适配包装或绑定的原始骨骼发生变更，则动态创建 AdaptedHandRenderer
            if (!(morph.rightHand instanceof AdaptedHandRenderer) || ((AdaptedHandRenderer) morph.rightHand).getOriginal() != rawRight) {
                morph.rightHand = new AdaptedHandRenderer(model, rawRight, true, pair.preset);
            }
            if (!(morph.leftHand instanceof AdaptedHandRenderer) || ((AdaptedHandRenderer) morph.leftHand).getOriginal() != rawLeft) {
                morph.leftHand = new AdaptedHandRenderer(model, rawLeft, false, pair.preset);
            }

            morph.triedHands = true;
            if (morph.getSettings() != null) {
                morph.getSettings().hands = true;
                morph.getSettings().hasHands = true;
            }

            // 确保纹理已初始化，避免 Metamorph 内部因 texture 为空直接跳过渲染
            if (morph.texture == null) {
                try {
                    java.lang.reflect.Method setupTex = EntityMorph.class.getDeclaredMethod("setupTexture");
                    setupTex.setAccessible(true);
                    setupTex.invoke(morph);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private GenericHandAdapter() {
    }
}

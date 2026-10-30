package aaaa0ggmc.morphext.variant;

import aaaa0ggmc.morphext.variant.vanilla.AgeableVariantHandler;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 生物形态变种处理器注册中心。
 */
public final class VariantRegistry {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final List<IVariantHandler> HANDLERS = new ArrayList<>();
    private static boolean initialized = false;

    private VariantRegistry() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 注册原版幼年与变种处理器
        register(new AgeableVariantHandler());

        LOGGER.info("[morphext] VariantRegistry initialized with {} handlers", HANDLERS.size());
    }

    /**
     * 注册自定义变种处理器（可供第三方或补丁模组调用，如 Ice and Fire 龙类处理器）。
     */
    public static synchronized void register(IVariantHandler handler) {
        if (handler != null && !HANDLERS.contains(handler)) {
            HANDLERS.add(0, handler); // 新注册的处理器优先匹配
            LOGGER.info("[morphext] Registered variant handler: {}", handler.getClass().getName());
        }
    }

    /**
     * 根据形态名称与实体实例查找适用的变种处理器。
     */
    public static IVariantHandler getHandler(String morphName, EntityLivingBase entity) {
        init();
        for (IVariantHandler handler : HANDLERS) {
            if (handler.appliesTo(morphName, entity)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 根据 AbstractMorph 查找适用的变种处理器。
     */
    public static IVariantHandler getHandler(AbstractMorph morph) {
        if (morph == null || morph.name == null) {
            return null;
        }
        EntityLivingBase entity = null;
        if (morph instanceof EntityMorph) {
            entity = ((EntityMorph) morph).getEntity();
        }
        return getHandler(morph.name, entity);
    }
}

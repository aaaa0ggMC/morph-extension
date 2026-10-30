package aaaa0ggmc.morphext.integration.metamorph;

import net.minecraftforge.fml.common.Loader;

/**
 * 软依赖门面：UI 层通过它调用 Metamorph 集成，不直接引用 Metamorph 类。
 * 这样将来把集成拆成独立 addon 模组时，核心模组无需改动。
 */
public final class MetamorphBridge {
    private static boolean initialized = false;

    private MetamorphBridge() {
    }

    public static boolean available() {
        return initialized && Loader.isModLoaded("metamorph");
    }

    /** 由 MetamorphIntegration.init() 在 Metamorph 存在时调用。 */
    public static void markInitialized() {
        initialized = true;
    }

    public static void refresh() {
        if (available()) {
            MetamorphIntegration.refresh();
            MetamorphIntegration.syncGranted();
        }
    }
}

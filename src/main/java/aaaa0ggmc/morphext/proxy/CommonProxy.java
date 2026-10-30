package aaaa0ggmc.morphext.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {
    /** 能力卷轴（制作者工具，NBT 指定能力 id）。普通版移除保留状态，强力版连状态一起清除。 */
    public static final aaaa0ggmc.morphext.item.AbilityScrollItem ABILITY_SCROLL =
            new aaaa0ggmc.morphext.item.AbilityScrollItem(false);
    public static final aaaa0ggmc.morphext.item.AbilityScrollItem ABILITY_SCROLL_POWERFUL =
            new aaaa0ggmc.morphext.item.AbilityScrollItem(true);

    public void preInit(FMLPreInitializationEvent event) {
        aaaa0ggmc.morphext.ability.PlayerAbilityStore.init(event.getModConfigurationDirectory());
        aaaa0ggmc.morphext.network.AbilityNetwork.init();
    }

    public void init(FMLInitializationEvent event) {
        aaaa0ggmc.morphext.ability.ServerAbilityService.register("morphext:",
                new aaaa0ggmc.morphext.ability.CoreServerAbilities());
        if (net.minecraftforge.fml.common.Loader.isModLoaded("metamorph")) {
            aaaa0ggmc.morphext.integration.metamorph.MetamorphServerAbilities.register();
        }
    }

    /** 网络线程回调；客户端代理负责转入客户端主线程。 */
    public void onActivationResult(String id, aaaa0ggmc.morphext.ability.ActivationResult result) {
    }

    public void onPlayerSnapshot(String json, String wipedAbility) { }

    public void postInit(FMLPostInitializationEvent event) {
    }
}

package aaaa0ggmc.morphext.item;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.proxy.CommonProxy;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 物品注册（1.12.2 走 RegistryEvent.Register）。 */
@Mod.EventBusSubscriber(modid = MorphExtension.MODID)
public class ItemRegistryHandler {
    private ItemRegistryHandler() {
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(CommonProxy.ABILITY_SCROLL);
        event.getRegistry().register(CommonProxy.ABILITY_SCROLL_POWERFUL);
    }
}

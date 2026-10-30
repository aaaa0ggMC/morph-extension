package aaaa0ggmc.morphext;

import aaaa0ggmc.morphext.ability.AbilityStateStore;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.config.ModConfig;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge;
import aaaa0ggmc.morphext.proxy.CommonProxy;
import aaaa0ggmc.morphext.proxy.ClientProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

@Mod(
        modid = MorphExtension.MODID,
        name = MorphExtension.NAME,
        version = MorphExtension.VERSION,
        acceptedMinecraftVersions = "[1.12,1.13)",
        dependencies = "required-after:mclib@[2.4,)"
)
public class MorphExtension {
    public static final String MODID = "aaaa0ggmc_morph_extension";
    public static final String NAME = "aaaa0ggmc's morph extension";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static MorphExtension instance;

    @SidedProxy(
            serverSide = "aaaa0ggmc.morphext.proxy.CommonProxy",
            clientSide = "aaaa0ggmc.morphext.proxy.ClientProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));
        AbilityStateStore.init(event.getModConfigurationDirectory());
        AbilityUnlocks.init(event.getModConfigurationDirectory());
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        aaaa0ggmc.morphext.ability.PlayerAbilityStore.clear();
    }
}

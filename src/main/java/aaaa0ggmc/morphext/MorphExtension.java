package aaaa0ggmc.morphext;

import aaaa0ggmc.morphext.ability.AbilityStateStore;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.config.ModConfig;
import aaaa0ggmc.morphext.conversion.ConversionPipeline;
import aaaa0ggmc.morphext.conversion.rule.ConversionRuleRegistry;
import aaaa0ggmc.morphext.conversion.rule.StructureDetectors;
import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.morphext.follower.FollowerHandler;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge;
import aaaa0ggmc.morphext.proxy.CommonProxy;
import aaaa0ggmc.morphext.proxy.ClientProxy;
import aaaa0ggmc.morphext.skill.SkillRegistry;
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
        // 通用机制：形态类别 + 转化规则（数据驱动，模组侧只提供 JSON）
        FormCategories.init(event.getModConfigurationDirectory());
        ConversionRuleRegistry.init(event.getModConfigurationDirectory());
        // 通用机制：技能系统（数据驱动，技能定义由整合包以 JSON 提供）
        SkillRegistry.init(event.getModConfigurationDirectory());
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 死亡转化管线：内置检测器 + 规则阶段 + 事件入口
        StructureDetectors.registerBuiltins();
        ConversionPipeline.register(ConversionRuleRegistry.stage());
        ConversionPipeline.registerEvents();
        // 通用机制：随从框架接线（EntityJoinWorldEvent 由 @Mod.EventBusSubscriber 自动注入）
        FollowerHandler.init();
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new aaaa0ggmc.morphext.command.CommandMorphExt());
    }

    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        aaaa0ggmc.morphext.ability.PlayerAbilityStore.clear();
    }
}

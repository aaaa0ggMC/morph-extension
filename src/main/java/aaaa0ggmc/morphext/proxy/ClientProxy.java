package aaaa0ggmc.morphext.proxy;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.SimpleAbility;
import aaaa0ggmc.morphext.ability.SprintAbility;
import aaaa0ggmc.morphext.client.KeyHandler;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphEventHandler;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        KeyHandler.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // 演示能力：验证“注册 -> 绑定 -> 轮盘激活”整条链路（带 5 秒冷却用于测试冷却系统）
        AbilityRegistry.register(new SimpleAbility(
                "morphext:demo_hello",
                "Demo: Hello",
                new ItemStack(Items.DIAMOND),
                player -> {
                    if (player != null) {
                        player.sendMessage(new TextComponentString("[morph extension] ability activated!"));
                    }
                }
        ).cooldown(100).description("演示能力。释放时在聊天框发送一条提示信息，开发测试用")
                .executionSide(aaaa0ggmc.morphext.ability.ExecutionSide.SERVER));
        // 代码级内置能力：直接触发行为（不模拟按键）
        AbilityRegistry.register(new SprintAbility());
        AbilityRegistry.register(new aaaa0ggmc.morphext.ability.HumanStatureAbility());
        // Metamorph 集成（软依赖：仅当 Metamorph 存在时启用）
        if (Loader.isModLoaded("metamorph")) {
            MetamorphIntegration.init();
            MetamorphEventHandler.register();
            aaaa0ggmc.morphext.variant.HumanStatureRenderHandler.register();
            aaaa0ggmc.morphext.variant.MorphCreativeInjector.register();
            aaaa0ggmc.morphext.client.RenderSafetyHandler.register();
            aaaa0ggmc.morphext.client.hand.GenericHandAdapter.register();
        }
        // 注：不再自动注册 vanilla 键位（那会产生 Hotbar/Perspective 之类的噪音）。
        // 需要键位型能力时由 addon 显式 new KeybindAbility(specificKeyBinding) 注册。
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
    }

    @Override
    public void onActivationResult(String id, aaaa0ggmc.morphext.ability.ActivationResult result) {
        Minecraft mc = Minecraft.getMinecraft();
        Object connection = mc.getConnection();
        mc.addScheduledTask(() -> {
            if (connection != null && connection == mc.getConnection() && mc.player != null) {
                aaaa0ggmc.morphext.client.ClientAbilityExecutor.acceptResult(id, result);
            }
        });
    }

    @Override
    public void onPlayerSnapshot(String json, String wipedAbility) {
        Minecraft mc = Minecraft.getMinecraft();
        Object connection = mc.getConnection();
        mc.addScheduledTask(() -> {
            if (connection != null && connection == mc.getConnection()) {
                try {
                    aaaa0ggmc.morphext.client.ClientPlayerData.accept(json, wipedAbility);
                } catch (RuntimeException error) {
                    aaaa0ggmc.morphext.client.ClientPlayerData.clear();
                    org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                            .error("Invalid player ability snapshot", error);
                }
            }
        });
    }
}

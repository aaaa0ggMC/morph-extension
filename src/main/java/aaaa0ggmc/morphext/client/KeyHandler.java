package aaaa0ggmc.morphext.client;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbilityCooldowns;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.ability.AbilityStateStore;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.client.gui.GuiAbilityConfig;
import aaaa0ggmc.morphext.client.gui.GuiAbilityStatus;
import aaaa0ggmc.morphext.client.gui.GuiInstantWheel;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = MorphExtension.MODID)
public class KeyHandler {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("morph-extension");

    /** 按住唤出轮盘（默认 G，避免与 MineMenu 的 R 冲突；可在控制里改）。 */
    public static final KeyBinding OPEN_WHEEL = new KeyBinding(
            "key.morphext.open_wheel", Keyboard.KEY_G, "key.categories.morphext");
    /** 打开配置 UI（默认不绑定，在控制里自行指派）。 */
    public static final KeyBinding OPEN_CONFIG = new KeyBinding(
            "key.morphext.open_config", Keyboard.KEY_NONE, "key.categories.morphext");
    /** 打开状态类技能总览（默认 B）。 */
    public static final KeyBinding OPEN_STATUS = new KeyBinding(
            "key.morphext.open_status", Keyboard.KEY_B, "key.categories.morphext");
    /** 释放当前选中的能力（默认 V）。 */
    public static final KeyBinding CAST_ABILITY = new KeyBinding(
            "key.morphext.cast", Keyboard.KEY_V, "key.categories.morphext");

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_WHEEL);
        ClientRegistry.registerKeyBinding(OPEN_CONFIG);
        ClientRegistry.registerKeyBinding(OPEN_STATUS);
        ClientRegistry.registerKeyBinding(CAST_ABILITY);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        if (OPEN_WHEEL.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiInstantWheel(OPEN_WHEEL.getKeyCode()));
        } else if (OPEN_CONFIG.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiAbilityConfig());
        } else if (OPEN_STATUS.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiAbilityStatus(null));
        }
    }

    /** 上一次 tick 时轮盘键的物理状态（边沿检测用）。 */
    private static boolean wheelKeyDown = false;
    /** 上一次 tick 时释放键的物理状态（边沿检测用）。 */
    private static boolean castKeyDown = false;
    /** 是否已屏蔽 Metamorph 自己的动作键（只做一次）。 */
    private static boolean metamorphKeyBlocked = false;

    /**
     * 轮盘开启 + 能力释放的统一驱动：每个 client tick 轮询物理按键状态（键鼠通用）。
     *
     * 不在 KeyInputEvent 中消费释放键：鼠标按压队列必须每 tick 及时清空，
     * 否则物理边沿执行后，陈旧的 isPressed 队列可能在下次键盘事件中再次翻转开关。
     * 每 tick 合并物理边沿与按压队列，兼容键鼠和两次 tick 之间的快速点击。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            wheelKeyDown = false;
            castKeyDown = false;
            while (CAST_ABILITY.isPressed()) { }
            return;
        }
        if (!metamorphKeyBlocked) {
            blockMetamorphActionKey();
        }
        boolean wheelDown = isPhysicallyDown(OPEN_WHEEL.getKeyCode());
        if (wheelDown && !wheelKeyDown && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiInstantWheel(OPEN_WHEEL.getKeyCode()));
        }
        wheelKeyDown = wheelDown;

        boolean castDown = isPhysicallyDown(CAST_ABILITY.getKeyCode());
        // 每 tick 消费全部按压队列，不能等下一个无关键盘事件才消费陈旧的鼠标按压。
        boolean queuedPress = false;
        while (CAST_ABILITY.isPressed()) {
            queuedPress = true;
        }
        boolean castEdge = !castKeyDown && (castDown || queuedPress);
        if (castEdge && mc.currentScreen == null && mc.inGameHasFocus) {
            LOGGER.info("[morphext] cast: code={} via={}", CAST_ABILITY.getKeyCode(),
                    castDown ? "edge" : "queued");
            castActiveAbility(mc);
        }
        castKeyDown = castDown;
    }

    /**
     * 屏蔽 Metamorph 自己的动作键（key.metamorph.action，默认 V）。
     *
     * 否则同一个 morph 动作会有两个键位：morphext 的释放键（玩家绑的鼠标 5）
     * 和 Metamorph 的 V——按哪个都能触发，键位不统一。
     * 屏蔽后动作只从 morphext 的释放键走（统一经过解锁/冷却/轮盘选中体系）。
     */
    private static void blockMetamorphActionKey() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null || mc.gameSettings.keyBindings == null) {
            return;
        }
        for (KeyBinding binding : mc.gameSettings.keyBindings) {
            if (binding != null && "key.metamorph.action".equals(binding.getKeyDescription())) {
                if (binding.getKeyCode() != Keyboard.KEY_NONE) {
                    int old = binding.getKeyCode();
                    binding.setKeyCode(Keyboard.KEY_NONE);
                    LOGGER.info("[morphext] blocked Metamorph action key (code={}); "
                            + "morph actions now only respond to the morphext cast key", old);
                }
                metamorphKeyBlocked = true;
                return;
            }
        }
    }

    /** 释放键的显示名（键鼠通用）：鼠标键位显示 MOUSE n，键盘显示键名。UI 文案用它，不写死。 */
    public static String castKeyName() {
        int code = CAST_ABILITY.getKeyCode();
        if (code == 0 || code == Keyboard.KEY_NONE) {
            return "?";
        }
        if (code < 0) {
            /* MC 的显示规则：鼠标键位 keyCode = 按钮号 - 100，显示为 1 开始的 MOUSE n */
            return "MOUSE " + (code + 101);
        }
        String name = Keyboard.getKeyName(code);
        return (name == null || name.isEmpty()) ? String.valueOf(code) : name;
    }

    /** 物理按键状态查询：鼠标键位（负 keyCode）走 Mouse.isButtonDown，键盘走 Keyboard.isKeyDown。 */
    public static boolean isPhysicallyDown(int keyCode) {
        if (keyCode == 0 || keyCode == Keyboard.KEY_NONE) {
            return false;
        }
        if (keyCode < 0) {
            return Mouse.isButtonDown(keyCode + 100);
        }
        return Keyboard.isKeyDown(keyCode);
    }

    /** 释放键：触发当前选中的能力（未解锁/冷却中则拒绝并提示）。 */
    private static void castActiveAbility(Minecraft mc) {
        ClientAbilityExecutor.activateById(ClientWheelConfig.getActiveAbility());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 登录：恢复能力状态存档 + 刷新 Metamorph morph 能力
        Minecraft.getMinecraft().addScheduledTask(() -> {
            MetamorphBridge.refresh();
        });
    }
}

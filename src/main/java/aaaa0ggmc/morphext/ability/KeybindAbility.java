package aaaa0ggmc.morphext.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.InputEvent;

/**
 * 键位能力：激活时“按下”一个 KeyBinding（向监听 KeyInputEvent 的模组转发一次输入）。
 *
 * 注意：依赖目标模组通过 InputEvent.KeyInputEvent 监听按键（绝大多数模组如此）。
 * 原生按键（如打开物品栏）依赖 processKeyBinds，仅在无界面打开时生效——本模组关闭轮盘后
 * 会残留一帧的按下状态，大部分情况可用，但不作为强保证。
 */
public class KeybindAbility extends Ability {
    private final KeyBinding keyBinding;

    public KeybindAbility(KeyBinding keyBinding) {
        super("keybind:" + keyBinding.getKeyCode(), keyBinding.getKeyDescription());
        this.icon(new ItemStack(Items.PAPER));
        this.keyBinding = keyBinding;
    }

    public KeyBinding getKeyBinding() {
        return this.keyBinding;
    }

    @Override
    public String getDisplayName() {
        return new TextComponentTranslation(this.keyBinding.getKeyDescription()).getUnformattedText();
    }

    @Override
    public void onActivate(EntityPlayer player) {
        // 1) 置按下状态 + pressTime=1：让 processKeyBinds 下一 tick 认到一次“按下”
        //    （原版一次性动作如打开物品栏因此可用；持续按住类动作仍不可靠）
        KeyBinding.setKeyBindState(this.keyBinding.getKeyCode(), true);
        setPressTime(this.keyBinding, 1);
        // 2) 广播输入事件：监听 KeyInputEvent 的模组立即收到
        Minecraft mc = Minecraft.getMinecraft();
        boolean focus = mc.inGameHasFocus;
        mc.inGameHasFocus = true;
        try {
            MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
        } finally {
            mc.inGameHasFocus = focus;
        }
    }

    /** 反射写 KeyBinding.pressTime（MCP 私有字段）。 */
    private static void setPressTime(KeyBinding keyBinding, int value) {
        try {
            java.lang.reflect.Field field = KeyBinding.class.getDeclaredField("pressTime");
            field.setAccessible(true);
            field.setInt(keyBinding, value);
        } catch (Throwable ignored) {
        }
    }
}

package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 生物自主 AI 开关状态能力 (Mob Autonomous AI Toggle Ability)。
 *
 * 状态语义：
 *  - AI [OFF]: 关闭（默认）。变身实体处于纯净手动控制状态 (NoAI = true)，无任何实体 AI 干扰。
 *  - AI [ON]: 开启。激活变身实体的受控 AI 沙盒引擎 (NoAI = false)，
 *    自动进行头部观察、战斗警戒、目标伴战与原生技能协同，并内置防自残、防自爆、防拆家安全屏障。
 */
public class MobAiToggleAbility extends Ability implements IStatefulAbility {
    public static final String ID = "morphext:mob_ai";

    public MobAiToggleAbility() {
        super(ID, "AI [ON/OFF]");
        // 红石比较器作为智能与逻辑判定的代表图标
        this.icon(new ItemStack(Items.COMPARATOR));
    }

    @Override
    public boolean isInnate() {
        // 变形体专属调试开关：由当前 EntityMorph 临时授予，不做全局常驻能力。
        return false;
    }

    @Override
    public ExecutionSide getExecutionSide() {
        return ExecutionSide.SERVER;
    }

    public static boolean isEnabled() {
        JsonObject state = AbilityStateStore.get(ID);
        return state.has("enabled") && state.get("enabled").isJsonPrimitive() && state.get("enabled").getAsBoolean();
    }

    @Override
    public String getDisplayName() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            try {
                if (I18n.canTranslate("morphext.ability.mob_ai.name")) {
                    return I18n.translateToLocal("morphext.ability.mob_ai.name");
                }
            } catch (Throwable ignored) {}
        }
        return "生物自主 AI";
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        boolean on = isEnabled();
        if (on) {
            return TextFormatting.GREEN + "[" + I18n.translateToLocal("gui.morphext.state.ai.on") + "]" + TextFormatting.RESET;
        } else {
            return TextFormatting.GRAY + "[" + I18n.translateToLocal("gui.morphext.state.ai.off") + "]" + TextFormatting.RESET;
        }
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return isEnabled();
    }

    @Override
    public void onActivate(EntityPlayer player) {
        if (player != null && player.world.isRemote) {
            aaaa0ggmc.morphext.client.ClientAbilityExecutor.activate(this);
        }
    }

    @Override
    public JsonObject saveState() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", isEnabled());
        return json;
    }

    @Override
    public void loadState(JsonObject state) {
    }

    @Override
    public String getDescription() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            try {
                if (I18n.canTranslate("morphext.ability.mob_ai.desc")) {
                    return I18n.translateToLocal("morphext.ability.mob_ai.desc");
                }
            } catch (Throwable ignored) {}
        }
        return "控制变身生物的自主 AI 行为状态 [开/关]：\n" +
                "· [AI: OFF]（默认）：完全手动操控，静默无实体 AI 干扰；\n" +
                "· [AI: ON]：启用沙盒伴战 AI，生物将自主警戒、锁定敌对目标协助战斗，并受到防自残与防自毁安全保护。";
    }
}

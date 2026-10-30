package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** 内置能力的服务端执行器，不依赖客户端注册表，不把玩家状态存到共享能力实例里。 */
public final class CoreServerAbilities implements ServerAbilityService.Handler {
    public static final String SPRINT = "morphext:sprint";

    @Override
    public boolean isAvailable(EntityPlayerMP player, String id) {
        return SPRINT.equals(id) || "morphext:demo_hello".equals(id) || HumanStatureAbility.ID.equals(id);
    }

    @Override
    public int getCooldownTicks(EntityPlayerMP player, String id) {
        return "morphext:demo_hello".equals(id) ? 100 : 0;
    }

    @Override
    public ActivationResult activate(EntityPlayerMP player, String id) {
        if (SPRINT.equals(id)) {
            JsonObject state = PlayerAbilityStore.get(player).getState(SPRINT);
            boolean enabled = !isEnabled(state);
            state.addProperty("enabled", enabled);
            PlayerAbilityStore.update(player, data -> data.putState(SPRINT, state));
            if (!enabled) {
                player.setSprinting(false);
            }
            return ActivationResult.success(0);
        }
        if (HumanStatureAbility.ID.equals(id)) {
            JsonObject state = PlayerAbilityStore.get(player).getState(HumanStatureAbility.ID);
            int currentId = 0;
            if (state.has("mode") && state.get("mode").isJsonPrimitive()) {
                currentId = state.get("mode").getAsInt();
            }
            HumanStatureAbility.Mode next = HumanStatureAbility.Mode.fromId(currentId).next();
            state.addProperty("mode", next.id);
            PlayerAbilityStore.update(player, data -> data.putState(HumanStatureAbility.ID, state));

            TextComponentTranslation modeText = new TextComponentTranslation("morphext.stature.mode." + next.id);
            player.sendStatusMessage(new TextComponentTranslation("morphext.stature.switched", modeText), true);
            return ActivationResult.success(0);
        }
        if ("morphext:demo_hello".equals(id)) {
            player.sendMessage(new TextComponentString("[morph extension] ability activated!"));
            return ActivationResult.success(100);
        }
        return ActivationResult.unavailable();
    }

    @Override
    public void onRevoked(EntityPlayerMP player, String id) {
        if (SPRINT.equals(id)) { player.setSprinting(false); }
    }

    @Override
    public void onDataWiped(EntityPlayerMP player, String id) { onRevoked(player, id); }

    /** 仅在服务端玩家 tick 中调用；读取该玩家自己的持久开关。 */
    public static void tick(EntityPlayerMP player) {
        // 服务端不需要每 tick 强行覆盖 player.setSprinting(true)；
        // 疾跑状态由客户端根据实际移动输入通过动作包同步，静止时强行覆盖会导致脚底持续喷扬尘粒子。
    }

    public static boolean isEnabled(JsonObject state) {
        return state.has("enabled") && state.get("enabled").isJsonPrimitive()
                && state.get("enabled").getAsJsonPrimitive().isBoolean()
                && state.get("enabled").getAsBoolean();
    }
}

package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

/** 内置能力的服务端执行器，不依赖客户端注册表，不把玩家状态存到共享能力实例里。 */
public final class CoreServerAbilities implements ServerAbilityService.Handler {
    public static final String SPRINT = "morphext:sprint";

    @Override
    public boolean isAvailable(EntityPlayerMP player, String id) {
        return SPRINT.equals(id) || "morphext:demo_hello".equals(id);
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
            player.setSprinting(enabled);
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
        if (!PlayerAbilityStore.isLoaded(player)) { return; }
        JsonObject state = PlayerAbilityStore.getLoadedState(player, SPRINT);
        if (isEnabled(state)
                && !PlayerAbilityStore.isRemoved(player, SPRINT)) {
            player.setSprinting(true);
        }
    }

    public static boolean isEnabled(JsonObject state) {
        return state.has("enabled") && state.get("enabled").isJsonPrimitive()
                && state.get("enabled").getAsJsonPrimitive().isBoolean()
                && state.get("enabled").getAsBoolean();
    }
}

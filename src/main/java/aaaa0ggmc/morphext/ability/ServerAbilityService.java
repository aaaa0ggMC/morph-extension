package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayerMP;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端执行器注册表。它不使用客户端 AbilityRegistry/全局 JSON 解锁表。
 * 动态 addon 必须以服务端玩家当前状态验证请求，不得相信客户端发来的授予信息。
 */
public final class ServerAbilityService {
    public interface Handler {
        ActivationResult activate(EntityPlayerMP player, String abilityId);
        /** 天生拥有或从该玩家当前服务端形态/进度授予，不读取客户端镜像。 */
        default boolean isAvailable(EntityPlayerMP player, String abilityId) { return false; }
        /** 能提前确定的冷却应在执行前声明，先持久化再产生效果，避免保存失败后重复释放。 */
        default int getCooldownTicks(EntityPlayerMP player, String abilityId) { return 0; }
        default void onRevoked(EntityPlayerMP player, String abilityId) { }
        default void onDataWiped(EntityPlayerMP player, String abilityId) { }
    }

    private static final Map<String, Handler> HANDLERS = new LinkedHashMap<>();

    private ServerAbilityService() { }

    /** 在 common 初始化期间注册，一种命名空间只允许一个执行器。 */
    public static void register(String prefix, Handler handler) {
        if (prefix == null || prefix.isEmpty() || handler == null || HANDLERS.containsKey(prefix)) {
            throw new IllegalArgumentException("Invalid or duplicate server ability prefix: " + prefix);
        }
        HANDLERS.put(prefix, handler);
    }

    /** 仅允许服务端主线程调用。未知能力默认拒绝，不以客户端注册表兜底执行。 */
    public static ActivationResult activate(EntityPlayerMP player, String id) {
        if (player == null || player.world.isRemote || player.isDead || !PlayerAbilityData.validId(id)) {
            return ActivationResult.unavailable();
        }
        try {
            for (Map.Entry<String, Handler> entry : HANDLERS.entrySet()) {
                if (id.startsWith(entry.getKey())) {
                    PlayerAbilityData data = PlayerAbilityStore.get(player);
                    if (!data.isAllowed(id, entry.getValue().isAvailable(player, id))) {
                        return new ActivationResult(ActivationResult.Status.LOCKED, 0);
                    }
                    int cooldown = data.getCooldown(id, PlayerAbilityStore.now(player));
                    if (cooldown > 0) {
                        return new ActivationResult(ActivationResult.Status.COOLDOWN, cooldown);
                    }
                    int reserved = Math.max(0, entry.getValue().getCooldownTicks(player, id));
                    if (reserved > 0) {
                        PlayerAbilityStore.update(player, next ->
                                next.setCooldown(id, PlayerAbilityStore.now(player), reserved));
                    }
                    ActivationResult raw = entry.getValue().activate(player, id);
                    ActivationResult result = raw == null ? ActivationResult.unavailable() : raw;
                    if (result.status == ActivationResult.Status.SUCCESS && result.cooldownTicks > reserved) {
                        PlayerAbilityStore.update(player, next ->
                                next.setCooldown(id, PlayerAbilityStore.now(player), result.cooldownTicks));
                    }
                    return new ActivationResult(result.status, Math.max(reserved, result.cooldownTicks));
                }
            }
        } catch (RuntimeException error) {
            org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                    .error("Server ability execution failed: {}", id, error);
            return new ActivationResult(ActivationResult.Status.FAILED, 0);
        }
        return ActivationResult.unavailable();
    }

    public static boolean isAvailable(EntityPlayerMP player, String id) {
        Handler handler = handler(id);
        return PlayerAbilityStore.get(player).isAllowed(id, handler != null && handler.isAvailable(player, id));
    }

    public static Handler handler(String id) {
        if (!PlayerAbilityData.validId(id)) { return null; }
        for (Map.Entry<String, Handler> entry : HANDLERS.entrySet()) {
            if (id.startsWith(entry.getKey())) { return entry.getValue(); }
        }
        return null;
    }
}

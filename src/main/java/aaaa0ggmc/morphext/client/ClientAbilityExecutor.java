package aaaa0ggmc.morphext.client;

import aaaa0ggmc.morphext.ability.*;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;

/** 键位和状态页共用的客户端入口。客户端只做 UX 预检，服务端能力等待响应后才确认。 */
public final class ClientAbilityExecutor {
    private ClientAbilityExecutor() { }

    public static void activate(IAbility ability) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || ability == null || ability.isPassive()) {
            return;
        }        if (!AbilityUnlocks.isReady()) {
            mc.player.sendStatusMessage(new TextComponentTranslation("gui.morphext.syncing"), true);
            return;
        }
        if (!AbilityUnlocks.isUnlocked(ability)) {
            acceptResult(ability.getId(), new ActivationResult(ActivationResult.Status.LOCKED, 0));
            return;
        }
        int remaining = Math.max(AbilityCooldowns.getRemaining(ability.getId()),
                ability.getExternalCooldownTicks(mc.player));
        if (remaining > 0) {
            showResult(new ActivationResult(ActivationResult.Status.COOLDOWN, remaining));
            return;
        }
        if (ability.getExecutionSide() == ExecutionSide.SERVER) {
            AbilityNetwork.CHANNEL.sendToServer(new AbilityNetwork.Request(ability.getId()));
            return;
        }
        try {
            ActivationResult result = ability.activate(mc.player);
            if (result == null) {
                result = new ActivationResult(ActivationResult.Status.FAILED, 0);
            }
            if (result.status == ActivationResult.Status.SUCCESS && result.cooldownTicks > 0) {
                AbilityCooldowns.start(mc.player, ability.getId(), result.cooldownTicks);
            }
            showResult(result);
        } catch (RuntimeException error) {
            org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                    .error("Client ability execution failed: {}", ability.getId(), error);
            showResult(new ActivationResult(ActivationResult.Status.FAILED, 0));
        }
    }

    /**
     * 按能力 id 释放（键位入口使用）。轮盘槽位可能在能力暂时不可用时仍然保留绑定，
     * 此时 AbilityRegistry 可能只能从“最近见过”的缓存里取到原型，甚至完全取不到；
     * 后者也要给出“我忘了这个技能了”的反馈，而不是静默无响应。
     */
    public static void activateById(String abilityId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || abilityId == null || abilityId.isEmpty()) {
            return;
        }
        if (!AbilityUnlocks.isReady()) {
            mc.player.sendStatusMessage(new TextComponentTranslation("gui.morphext.syncing"), true);
            return;
        }
        IAbility ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            showResult(new ActivationResult(ActivationResult.Status.LOCKED, 0));
            return;
        }
        activate(ability);
    }

    public static void acceptResult(String id, ActivationResult result) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        if (result.cooldownTicks > 0) {
            AbilityCooldowns.confirm(id, result.cooldownTicks);
        }
        showResult(result);
    }

    private static void showResult(ActivationResult result) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || result.status == ActivationResult.Status.SUCCESS) {
            return;
        }
        String key;
        Object[] args = new Object[0];
        switch (result.status) {
            case COOLDOWN:
                key = "gui.morphext.cooldown";
                args = new Object[]{String.format(java.util.Locale.ROOT, "%.1f", result.cooldownTicks / 20F)};
                break;
            case LOCKED: key = "gui.morphext.locked"; break;
            case UNAVAILABLE: key = "gui.morphext.unavailable"; break;
            default: key = "gui.morphext.failed";
        }
        mc.player.sendStatusMessage(new TextComponentTranslation(key, args), true);
    }
}

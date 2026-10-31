package aaaa0ggmc.morphext.client;

import aaaa0ggmc.morphext.ability.*;
import aaaa0ggmc.morphext.config.ModConfig;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** 当前连接的只读镜像，不读写任何客户端永久权限文件。 */
public final class ClientPlayerData {
    private static Object connection;
    private static UUID owner;
    private static UUID requestedFor;

    private ClientPlayerData() { }

    public static void ensureSession() {
        Minecraft mc = Minecraft.getMinecraft();
        if (connection != mc.getConnection()) {
            clear();
            connection = mc.getConnection();
        }
        if (mc.player == null || mc.getConnection() == null) { return; }
        UUID current = mc.player.getUniqueID();
        if (owner != null && !owner.equals(current)) {
            clear();
            connection = mc.getConnection();
        }
        if (!AbilityUnlocks.isReady() && !current.equals(requestedFor)) {
            requestedFor = current;
            AbilityNetwork.CHANNEL.sendToServer(new AbilityNetwork.SyncRequest());
        }
    }

    public static void accept(String json, String wipedAbility) {
        Minecraft mc = Minecraft.getMinecraft();
        JsonObject snapshot = new JsonParser().parse(json).getAsJsonObject();
        UUID receivedOwner = UUID.fromString(snapshot.get("owner").getAsString());
        // 同时验证结构，客户端不能消费未来未知 schema。
        new PlayerAbilityData(receivedOwner, snapshot);
        if (!snapshot.has("serverTick") || !snapshot.get("serverTick").isJsonPrimitive()
                || !snapshot.get("serverTick").getAsJsonPrimitive().isNumber()
                || snapshot.get("serverTick").getAsLong() < 0) {
            throw new IllegalArgumentException("Missing server clock in player snapshot");
        }
        if (mc.player != null && !receivedOwner.equals(mc.player.getUniqueID())) { return; }
        if (connection != mc.getConnection() || owner != null && !owner.equals(receivedOwner)) {
            clear();
        }
        connection = mc.getConnection();
        owner = receivedOwner;
        IAbility sprint = AbilityRegistry.get(CoreServerAbilities.SPRINT);
        boolean wasSprintAllowed = AbilityUnlocks.isUnlocked(sprint);
        AbilityUnlocks.replaceSnapshot(snapshot);
        AbilityStateStore.replaceSnapshot(snapshot.getAsJsonObject("states"));
        ClientWheelConfig.replaceSnapshot(snapshot.getAsJsonObject("states"));
        JsonObject cooldowns = snapshot.getAsJsonObject("cooldowns");
        AbilityCooldowns.replaceConfirmed(cooldowns, snapshot.get("serverTick").getAsLong());
        if (wasSprintAllowed && !AbilityUnlocks.isUnlocked(sprint) && mc.player != null) {
            mc.player.setSprinting(false);
        }
        // 注意：不因为当前形态不再拥有某个能力就清空轮盘选中的槽位。
        // 槽位绑定与当前选择必须保留，能力暂时不可用时由轮盘显示锁定、释放时给出“已遗忘”提示。
        if (wipedAbility != null && !wipedAbility.isEmpty()) {
            AbilityCooldowns.clearAbility(wipedAbility);
            IAbility ability = AbilityRegistry.get(wipedAbility);
            if (ability != null && ability.getExecutionSide() == ExecutionSide.CLIENT && mc.player != null) {
                ability.onDataWiped(mc.player);
            }
        }
        if (mc.currentScreen instanceof aaaa0ggmc.morphext.client.gui.GuiAbilityStatus) {
            ((aaaa0ggmc.morphext.client.gui.GuiAbilityStatus) mc.currentScreen).refreshStates();
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && CoreServerAbilities.isEnabled(AbilityStateStore.get(CoreServerAbilities.SPRINT))) {
            mc.player.setSprinting(false);
        }
        owner = null;
        requestedFor = null;
        connection = null;
        AbilityUnlocks.load();
        AbilityStateStore.load();
        AbilityCooldowns.clear();
        ClientWheelConfig.reset();
        AbilityStateStore.applyToAbilities();
    }
}

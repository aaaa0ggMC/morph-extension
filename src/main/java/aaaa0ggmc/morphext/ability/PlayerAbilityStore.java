package aaaa0ggmc.morphext.ability;

import aaaa0ggmc.morphext.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** 服务端主线程拥有的玩家 JSON 存储。缓存按服务器实例隔离，磁盘按存档和 UUID 隔离。 */
public final class PlayerAbilityStore {
    public static final int MAX_JSON_BYTES = 262144;
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, Map<UUID, PlayerAbilityData>> CACHE = new WeakHashMap<>();
    private static Path legacyDirectory;

    private PlayerAbilityStore() { }

    public static void init(File configDirectory) {
        legacyDirectory = configDirectory.toPath().resolve("aaaa0ggmc_morph_extension");
    }

    private static MinecraftServer server(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        if (server == null || player.world.isRemote || !server.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Player ability data requires the server main thread");
        }
        return server;
    }

    private static Path path(EntityPlayerMP player) {
        return server(player).getWorld(0).getSaveHandler().getWorldDirectory().toPath()
                .resolve("data/morphext/players").resolve(player.getUniqueID().toString() + ".json");
    }

    public static long now(EntityPlayerMP player) {
        return server(player).getWorld(0).getTotalWorldTime();
    }

    /** 返回副本，不允许调用者绕过原子提交修改缓存。损坏文件抛错，不覆盖。 */
    public static PlayerAbilityData get(EntityPlayerMP player) {
        Map<UUID, PlayerAbilityData> players = CACHE.computeIfAbsent(server(player), s -> new HashMap<>());
        PlayerAbilityData data = players.get(player.getUniqueID());
        if (data == null) {
            Path file = path(player);
            try {
                if (Files.exists(file)) {
                    JsonObject json = readObject(file);
                    if (json.has("states") && json.get("states").isJsonObject()
                            && !json.getAsJsonObject("states").has(WheelConfigState.ID)) {
                        // 旧玩家文件没有轮盘状态：一次性迁移种子（ModConfig 内有全局“已迁移”标记，
                        // 种子只消费一次，之后的新玩家/新存档一律从默认轮盘布局开始）。
                        json.getAsJsonObject("states").add(WheelConfigState.ID, ModConfig.consumeWheelSeed());
                        data = new PlayerAbilityData(player.getUniqueID(), json);
                        save(file, data);
                    } else {
                        data = new PlayerAbilityData(player.getUniqueID(), json);
                    }
                } else {
                    data = createInitial(player);
                    save(file, data);
                    if (data.toJson().has("legacyImported")
                            && data.toJson().get("legacyImported").getAsBoolean()) {
                        // 一次性迁移：导入成功后让旧全局文件退役，防止后续新存档重复继承
                        retireLegacy();
                    }
                }
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("Cannot load player ability data; file left unchanged", error);
            }
            players.put(player.getUniqueID(), data);
        }
        return new PlayerAbilityData(player.getUniqueID(), data.toJson());
    }

    public static boolean isLoaded(EntityPlayerMP player) {
        Map<UUID, PlayerAbilityData> players = CACHE.get(server(player));
        return players != null && players.containsKey(player.getUniqueID());
    }

    /** 只读热路径，避免每个玩家 tick 序列化整个数据文档。 */
    public static JsonObject getLoadedState(EntityPlayerMP player, String id) {
        Map<UUID, PlayerAbilityData> players = CACHE.get(server(player));
        PlayerAbilityData data = players == null ? null : players.get(player.getUniqueID());
        return data == null ? new JsonObject() : data.getState(id);
    }

    public static boolean isRemoved(EntityPlayerMP player, String id) {
        Map<UUID, PlayerAbilityData> players = CACHE.get(server(player));
        PlayerAbilityData data = players == null ? null : players.get(player.getUniqueID());
        return data != null && data.isRemoved(id);
    }

    public static void update(EntityPlayerMP player, Consumer<PlayerAbilityData> change) {
        PlayerAbilityData next = get(player);
        change.accept(next);
        // 再验证一次结构；保存成功后才发布新缓存。
        next = new PlayerAbilityData(player.getUniqueID(), next.toJson());
        try {
            save(path(player), next);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot save player ability data; cache left unchanged", error);
        }
        CACHE.get(server(player)).put(player.getUniqueID(), next);
    }

    public static void evict(EntityPlayerMP player) {
        Map<UUID, PlayerAbilityData> players = CACHE.get(server(player));
        if (players != null) { players.remove(player.getUniqueID()); }
    }

    public static void clear() { CACHE.clear(); }

    private static PlayerAbilityData createInitial(EntityPlayerMP player) throws IOException {
        PlayerAbilityData fresh = new PlayerAbilityData(player.getUniqueID());
        JsonObject json = fresh.toJson();
        MinecraftServer server = server(player);
        boolean owner = server.isSinglePlayer() && player.getName().equalsIgnoreCase(server.getServerOwner());
        boolean imported = false;
        if (owner && legacyDirectory != null) {
            Path permissions = legacyDirectory.resolve("unlocks.json");
            Path states = legacyDirectory.resolve("ability_states.json");
            if (Files.exists(permissions)) {
                JsonObject old = readObject(permissions);
                if (old.has("unlocked")) { json.add("unlocked", old.get("unlocked")); }
                if (old.has("removed")) { json.add("removed", old.get("removed")); }
                imported = true;
            }
            if (Files.exists(states)) {
                json.add("states", readObject(states));
                imported = true;
            }
        }
        json.addProperty("legacyImported", imported);
        // 轮盘布局迁移：必须在 legacy 导入（可能整体替换 states）之后补进 states。
        // consumeWheelSeed 全局只消费一次，之后的新玩家文件从默认轮盘布局开始。
        json.getAsJsonObject("states").add(WheelConfigState.ID, ModConfig.consumeWheelSeed());
        return new PlayerAbilityData(player.getUniqueID(), json);
    }

    /** 一次性迁移收尾：把已导入过的旧全局文件改名留档，避免每个新存档重复继承。 */
    private static void retireLegacy() {
        if (legacyDirectory == null) {
            return;
        }
        for (String name : new String[] {"unlocks.json", "ability_states.json"}) {
            Path source = legacyDirectory.resolve(name);
            if (!Files.exists(source)) {
                continue;
            }
            try {
                Files.move(source, legacyDirectory.resolve(name + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[morphext] Legacy player data migrated and retired: {}", source);
            } catch (IOException error) {
                LOGGER.warn("[morphext] Failed to retire legacy player data {}", source, error);
            }
        }
    }

    private static JsonObject readObject(Path file) throws IOException {
        if (Files.size(file) > MAX_JSON_BYTES) { throw new IOException("Player data exceeds size limit"); }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = new JsonParser().parse(reader);
            if (!json.isJsonObject()) { throw new IOException("Expected a JSON object"); }
            return json.getAsJsonObject();
        }
    }

    private static void save(Path file, PlayerAbilityData data) throws IOException {
        byte[] json = GSON.toJson(data.toJson()).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_JSON_BYTES) { throw new IOException("Player data exceeds size limit"); }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "ability-", ".tmp");
        try {
            Files.write(temporary, json);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

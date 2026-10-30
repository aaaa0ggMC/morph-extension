package aaaa0ggmc.morphext.network;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.ability.ActivationResult;
import aaaa0ggmc.morphext.ability.ServerAbilityService;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import java.nio.charset.StandardCharsets;

/** 数据包只传能力 ID 和结果，不传玩家身份、形态实例或可执行对象。 */
public final class AbilityNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("morphext");
    /** 仅服务端主线程访问；不把玩家实体永久留在静态缓存中。 */
    private static final java.util.Map<EntityPlayerMP, String> LAST_PRESENTATION = new java.util.WeakHashMap<>();

    private AbilityNetwork() { }

    public static void init() {
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ReplyHandler.class, Reply.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(SnapshotHandler.class, Snapshot.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(SyncHandler.class, SyncRequest.class, 3, Side.SERVER);
    }

    public static void syncPlayer(EntityPlayerMP player, String wipedAbility) {
        if (player.connection == null || player.connection.player != player) { return; }
        PlayerAbilityData data = PlayerAbilityStore.get(player);
        String json = data.snapshot(PlayerAbilityStore.now(player)).toString();
        CHANNEL.sendTo(new Snapshot(json, wipedAbility), player);
        LAST_PRESENTATION.put(player, presentationKey(data));
    }

    /** 纯冷却变化只需释放响应，不发送全量快照；权限/状态真正变化时才同步。 */
    public static void syncPlayerIfChanged(EntityPlayerMP player) {
        PlayerAbilityData data = PlayerAbilityStore.get(player);
        if (!presentationKey(data).equals(LAST_PRESENTATION.get(player))) {
            syncPlayer(player, "");
        }
    }

    private static String presentationKey(PlayerAbilityData data) {
        com.google.gson.JsonObject json = data.toJson();
        json.remove("cooldowns");
        return json.toString();
    }

    public static class Snapshot implements IMessage {
        public String json;
        public String wipedAbility;
        public Snapshot() { }
        public Snapshot(String json, String wipedAbility) {
            this.json = json;
            this.wipedAbility = wipedAbility == null ? "" : wipedAbility;
        }
        @Override public void fromBytes(ByteBuf buf) {
            int size = buf.readInt();
            if (size < 0 || size > PlayerAbilityStore.MAX_JSON_BYTES || size > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid snapshot size");
            }
            byte[] bytes = new byte[size];
            buf.readBytes(bytes);
            json = new String(bytes, StandardCharsets.UTF_8);
            wipedAbility = ByteBufUtils.readUTF8String(buf);
            if (wipedAbility.length() > 256) { throw new IllegalArgumentException("Invalid wipe id"); }
        }
        @Override public void toBytes(ByteBuf buf) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > PlayerAbilityStore.MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Snapshot exceeds size limit");
            }
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
            ByteBufUtils.writeUTF8String(buf, wipedAbility);
        }
    }

    public static class SyncRequest implements IMessage {
        @Override public void fromBytes(ByteBuf buf) { }
        @Override public void toBytes(ByteBuf buf) { }
    }

    public static class SyncHandler implements IMessageHandler<SyncRequest, IMessage> {
        private static final java.util.Map<EntityPlayerMP, Integer> LAST_SYNC = new java.util.WeakHashMap<>();
        @Override public IMessage onMessage(SyncRequest message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Integer last = LAST_SYNC.get(player);
                if (last == null || player.ticksExisted - last >= 20) {
                    LAST_SYNC.put(player, player.ticksExisted);
                    aaaa0ggmc.morphext.ability.PlayerDataEvents.sync(player);
                }
            });
            return null;
        }
    }

    public static class SnapshotHandler implements IMessageHandler<Snapshot, IMessage> {
        @Override public IMessage onMessage(Snapshot message, MessageContext context) {
            MorphExtension.proxy.onPlayerSnapshot(message.json, message.wipedAbility);
            return null;
        }
    }

    public static class Request implements IMessage {
        public String id;
        public Request() { }
        public Request(String id) { this.id = id; }
        @Override public void fromBytes(ByteBuf buf) { id = readId(buf); }
        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, id); }
    }

    public static class Reply implements IMessage {
        public String id;
        public ActivationResult result;
        public Reply() { }
        public Reply(String id, ActivationResult result) { this.id = id; this.result = result; }
        @Override public void fromBytes(ByteBuf buf) {
            id = readId(buf);
            int status = buf.readUnsignedByte();
            if (status >= ActivationResult.Status.values().length) {
                throw new IllegalArgumentException("Invalid activation status");
            }
            result = new ActivationResult(ActivationResult.Status.values()[status], buf.readInt());
        }
        @Override public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, id);
            buf.writeByte(result.status.ordinal());
            buf.writeInt(result.cooldownTicks);
        }
    }

    private static String readId(ByteBuf buf) {
        String id = ByteBufUtils.readUTF8String(buf);
        if (!aaaa0ggmc.morphext.ability.PlayerAbilityData.validId(id)) {
            throw new IllegalArgumentException("Invalid ability id length");
        }
        return id;
    }

    public static class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override public IMessage onMessage(Request message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                // 登录玩家来自连接上下文，不从请求中取 UUID。
                if (player.connection == null || player.connection.player != player || player.isDead) {
                    return;
                }
                ActivationResult result = ServerAbilityService.activate(player, message.id);
                CHANNEL.sendTo(new Reply(message.id, result), player);
                if (result.status == ActivationResult.Status.SUCCESS) {
                    try {
                        syncPlayerIfChanged(player);
                    } catch (RuntimeException error) {
                        org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                                .error("Ability executed but player snapshot could not be synchronized", error);
                    }
                }
            });
            return null;
        }
    }

    public static class ReplyHandler implements IMessageHandler<Reply, IMessage> {
        @Override public IMessage onMessage(Reply message, MessageContext context) {
            // 通过 sided proxy 进入客户端主线程；common/network 不加载 Minecraft 类。
            MorphExtension.proxy.onActivationResult(message.id, message.result);
            return null;
        }
    }
}

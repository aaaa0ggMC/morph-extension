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
        CHANNEL.registerMessage(MorphSyncHandler.class, MorphSyncPacket.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(CreeperSwellingHandler.class, CreeperSwellingPacket.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(WheelConfigHandler.class, WheelConfigUpdate.class, 6, Side.SERVER);
    }

    /** 客户端 UI 把完整的轮盘配置状态（states["morphext:wheel"]）发往服务端落盘。 */
    public static void sendWheelConfig(com.google.gson.JsonObject state) {
        CHANNEL.sendToServer(new WheelConfigUpdate(state.toString()));
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

    /** 轮盘配置（states["morphext:wheel"]）整体上行：只传状态 JSON，不传玩家身份。 */
    public static class WheelConfigUpdate implements IMessage {
        /** 轮盘状态远小于玩家快照，给 16KB 上限足够宽松。 */
        public static final int MAX_BYTES = 16384;
        public String json;
        public WheelConfigUpdate() { }
        public WheelConfigUpdate(String json) { this.json = json; }
        @Override public void fromBytes(ByteBuf buf) {
            int size = buf.readInt();
            if (size < 0 || size > MAX_BYTES || size > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid wheel config size");
            }
            byte[] bytes = new byte[size];
            buf.readBytes(bytes);
            json = new String(bytes, StandardCharsets.UTF_8);
        }
        @Override public void toBytes(ByteBuf buf) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Wheel config exceeds size limit");
            }
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static class WheelConfigHandler implements IMessageHandler<WheelConfigUpdate, IMessage> {
        @Override public IMessage onMessage(WheelConfigUpdate message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.connection == null || player.connection.player != player || player.isDead) {
                    return;
                }
                com.google.gson.JsonObject state;
                try {
                    // 归一化解析：越界钳制、坏 id 清空，落盘的永远是合法形状。
                    com.google.gson.JsonElement parsed = new com.google.gson.JsonParser().parse(message.json);
                    if (!parsed.isJsonObject()) {
                        throw new IllegalArgumentException("Wheel config must be a JSON object");
                    }
                    state = aaaa0ggmc.morphext.ability.WheelConfigState
                            .fromJson(parsed.getAsJsonObject()).toJson();
                } catch (RuntimeException error) {
                    org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                            .warn("Rejected invalid wheel config update from {}", player.getName(), error);
                    return;
                }
                PlayerAbilityStore.update(player,
                        data -> data.putState(aaaa0ggmc.morphext.ability.WheelConfigState.ID, state));
                syncPlayerIfChanged(player);
            });
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

    public static class MorphSyncPacket implements IMessage {
        public int entityId;
        public boolean animate;
        public mchorse.metamorph.api.morphs.AbstractMorph morph;

        public MorphSyncPacket() {}

        public MorphSyncPacket(int entityId, mchorse.metamorph.api.morphs.AbstractMorph morph, boolean animate) {
            this.entityId = entityId;
            this.morph = morph;
            this.animate = animate;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.entityId = buf.readInt();
            this.animate = buf.readBoolean();
            if (buf.readBoolean()) {
                this.morph = mchorse.metamorph.api.MorphManager.INSTANCE.morphFromNBT(ByteBufUtils.readTag(buf));
            } else {
                this.morph = null;
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.entityId);
            buf.writeBoolean(this.animate);
            if (this.morph != null) {
                buf.writeBoolean(true);
                ByteBufUtils.writeTag(buf, mchorse.metamorph.api.MorphUtils.toNBT(this.morph));
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    public static class MorphSyncHandler implements IMessageHandler<MorphSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(MorphSyncPacket message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.client.multiplayer.WorldClient world = net.minecraft.client.Minecraft.getMinecraft().world;
                if (world == null) return;
                net.minecraft.entity.Entity entity = world.getEntityByID(message.entityId);
                if (entity instanceof net.minecraft.entity.player.EntityPlayer) {
                    mchorse.metamorph.capabilities.morphing.IMorphing capability =
                            entity.getCapability(mchorse.metamorph.capabilities.morphing.MorphingProvider.MORPHING_CAP, null);
                    if (capability != null) {
                        try {
                            if (!message.animate) {
                                aaaa0ggmc.morphext.integration.metamorph.MorphAnimationRegistry.setSilentSyncContext(true);
                            }
                            capability.setCurrentMorph(message.morph, (net.minecraft.entity.player.EntityPlayer) entity, true);
                        } finally {
                            aaaa0ggmc.morphext.integration.metamorph.MorphAnimationRegistry.setSilentSyncContext(false);
                        }
                    }
                }
            });
            return null;
        }
    }

    public static void sendCreeperSwelling(net.minecraft.entity.player.EntityPlayer player, boolean swelling) {
        if (player == null || player.world.isRemote) return;
        CreeperSwellingPacket packet = new CreeperSwellingPacket(player.getEntityId(), swelling);
        CHANNEL.sendToAllTracking(packet, player);
        if (player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(packet, (EntityPlayerMP) player);
        }
    }

    public static class CreeperSwellingPacket implements IMessage {
        public int entityId;
        public boolean swelling;

        public CreeperSwellingPacket() {}

        public CreeperSwellingPacket(int entityId, boolean swelling) {
            this.entityId = entityId;
            this.swelling = swelling;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.entityId = buf.readInt();
            this.swelling = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.entityId);
            buf.writeBoolean(this.swelling);
        }
    }

    public static class CreeperSwellingHandler implements IMessageHandler<CreeperSwellingPacket, IMessage> {
        @Override
        public IMessage onMessage(CreeperSwellingPacket message, MessageContext context) {
            MorphExtension.proxy.onCreeperSwelling(message.entityId, message.swelling);
            return null;
        }
    }
}

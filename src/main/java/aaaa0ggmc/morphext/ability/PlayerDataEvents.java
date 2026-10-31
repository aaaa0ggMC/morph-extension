package aaaa0ggmc.morphext.ability;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 服务端玩家生命周期，客户端事件不触碰磁盘/服务端缓存。 */
@Mod.EventBusSubscriber(modid = MorphExtension.MODID)
public final class PlayerDataEvents {
    private PlayerDataEvents() { }

    public static void sync(EntityPlayerMP player) {
        try {
            AbilityNetwork.syncPlayer(player, "");
        } catch (Throwable error) {
            org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                    .error("Player ability data unavailable; original files retained", error);
            player.sendStatusMessage(new TextComponentTranslation("gui.morphext.data_failed"), true);
        }
    }

    private static void sync(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && !player.world.isRemote) {
            sync((EntityPlayerMP) player);
        }
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { sync(event.player); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        aaaa0ggmc.morphext.integration.metamorph.vanilla.CreeperFuseAction.clearPrimed(event.player);
        sync(event.player);
    }
    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        aaaa0ggmc.morphext.integration.metamorph.vanilla.CreeperFuseAction.clearPrimed(event.player);
        sync(event.player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        aaaa0ggmc.morphext.integration.metamorph.vanilla.CreeperFuseAction.clearPrimed(event.player);
        if (event.player instanceof EntityPlayerMP && !event.player.world.isRemote) {
            PlayerAbilityStore.evict((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP
                && !event.player.world.isRemote && !event.player.isDead) {
            CoreServerAbilities.tick((EntityPlayerMP) event.player);
        }
    }
}

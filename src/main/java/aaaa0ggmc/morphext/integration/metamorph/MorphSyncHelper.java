package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.network.AbilityNetwork;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 通用形态同步调度工具 (Morph Sync Helper)。
 *
 * 供各类 Action 与变种切换时同步模型数据使用。
 * 遵循非硬编码设计：
 * 自动根据 MorphAnimationRegistry 及当前 Action 上下文决策是否应当播放过渡动画，
 * 并支持显式传入 animate 标记。
 */
public final class MorphSyncHelper {
    private static final Logger LOGGER = LogManager.getLogger("morphext-sync");

    private MorphSyncHelper() {}

    /**
     * 同步形态（自动根据当前执行中的 Action 及配置判定是否触发动画）
     */
    public static void syncMorph(EntityPlayer player, AbstractMorph morph) {
        if (player == null || morph == null) return;
        IMorphing capability = Morphing.get(player);
        AbstractMorph previous = capability != null ? capability.getCurrentMorph() : null;
        boolean suppress = MorphAnimationRegistry.shouldSuppressAnimation(previous, morph);
        syncMorph(player, morph, !suppress);
    }

    /**
     * 显式指定是否触发变身动画来同步形态
     */
    public static void syncMorph(EntityPlayer player, AbstractMorph morph, boolean animate) {
        if (player == null || morph == null) return;

        // 服务端核心同步
        if (!player.world.isRemote && player instanceof EntityPlayerMP) {
            EntityPlayerMP mp = (EntityPlayerMP) player;
            IMorphing capability = Morphing.get(mp);
            if (capability != null) {
                try {
                    if (!animate) {
                        MorphAnimationRegistry.setSilentSyncContext(true);
                    }
                    capability.setCurrentMorph(morph, mp, true);
                } finally {
                    MorphAnimationRegistry.setSilentSyncContext(false);
                }
            }

            // 发送给周围玩家与自身
            AbilityNetwork.MorphSyncPacket pkt = new AbilityNetwork.MorphSyncPacket(mp.getEntityId(), morph, animate);
            AbilityNetwork.CHANNEL.sendTo(pkt, mp);
            if (mp.world instanceof WorldServer) {
                for (EntityPlayer tracker : ((WorldServer) mp.world).getEntityTracker().getTrackingPlayers(mp)) {
                    if (tracker instanceof EntityPlayerMP) {
                        AbilityNetwork.CHANNEL.sendTo(pkt, (EntityPlayerMP) tracker);
                    }
                }
            }
            LOGGER.debug("[morphext-sync] Synced morph {} for player {} (animate={})", morph.name, mp.getName(), animate);
        }
    }
}

package aaaa0ggmc.morphext.client;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.AbilityCooldowns;
import aaaa0ggmc.morphext.ability.AbilityUnlocks;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphBridge;
import aaaa0ggmc.morphext.integration.metamorph.MetamorphIntegration;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 状态型能力的持续效果：每个 client tick 调用 onTick。
 * 能力在 onTick 里自行维持效果（如保持疾跑），因此效果跨越选中切换持续存在。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = MorphExtension.MODID)
public class AbilityTickHandler {
    private static net.minecraft.entity.player.EntityPlayer lastPlayer;
    private AbilityTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ClientPlayerData.ensureSession();
        if (mc.player == null) {
            // 快照可能早于本地玩家生成；连接仍存在时不能丢掉刚收到的权威冷却。
            if (lastPlayer != null) {
                AbilityRegistry.clearGrants(lastPlayer);
                lastPlayer = null;
                if (MetamorphBridge.available()) {
                    MetamorphIntegration.resetClientTracking();
                }
            }
            return;
        }
        if (lastPlayer != null && lastPlayer != mc.player) {
            AbilityRegistry.clearGrants(lastPlayer);
        }
        lastPlayer = mc.player;
        if (mc.isGamePaused()) {
            return;
        }
        AbilityCooldowns.tick();
        /* 同步外部冷却（Metamorph 动作自带的 MOB_ABILITY_COOLDOWN 等），仅轮询当前玩家已生效的能力 */
        if (mc.player != null) {
            for (IAbility ability : AbilityRegistry.getGrantedAbilities()) {
                int external = ability.getExternalCooldownTicks(mc.player);
                AbilityCooldowns.syncExternal(ability.getId(), external);
            }
        }
        /* Metamorph morph 轮询（事件可能只在服务端触发，这里兜底） */
        if (MetamorphBridge.available()) {
            try {
                MetamorphIntegration.tick();
            } catch (Throwable t) {
                // Prevent crash in tick handler
            }
        }
        for (IAbility ability : AbilityRegistry.all()) {
            // 锁定的能力不 tick——效果随移除停止（强力卷轴再清掉积累数据）
            if (ability instanceof IStatefulAbility && AbilityUnlocks.isUnlocked(ability)) {
                try {
                    ((IStatefulAbility) ability).onTick(mc.player);
                } catch (Throwable t) {
                    // Prevent single ability bug from killing client loop
                }
            }
        }
    }
}

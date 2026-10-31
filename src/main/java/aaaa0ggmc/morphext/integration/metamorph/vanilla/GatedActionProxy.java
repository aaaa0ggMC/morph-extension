package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import aaaa0ggmc.morphext.integration.metamorph.MorphActionContext;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端冷却守门代理 (Gated Action Proxy) —— 服务端冷却校验的最小可行版。
 * <p>
 * morphext 统一执行通道（ServerAbilityService → MetamorphServerAbilities.activate）在执行前
 * 已按“玩家 + 能力 id”强制冷却并预占 {@code PlayerAbilityData} 冷却表（持久化、主线程权威）；
 * 但 metamorph 原生 action key 通道（{@code ServerHandlerAction} → {@code morph.action()} →
 * {@code settings.action.execute()}）不经过该通道，可无冷却连发。
 * <p>
 * 本代理在 {@link VanillaActions} 注册时统一包装动作实例（registry 值即 morph settings.action），
 * 仅当<b>不在</b> morphext 执行上下文（{@link MorphActionContext}）时启用内部门禁：
 * 按玩家 + 动作类名（别名注册共享同一冷却）记录服务端截止 tick，冷却中拒绝执行。
 * morphext 通道内的执行完全不受影响，保持既有“按能力 id 冷却”语义。
 */
public final class GatedActionProxy implements IAction, ICooldownAction {
    /** 玩家 -> 动作门禁键 -> [截止 tick, 上次提示 tick]。仅服务端主线程读写。 */
    private static final Map<UUID, Map<String, long[]>> GATES = new ConcurrentHashMap<>();

    private final IAction delegate;
    private final String gateKey;

    private GatedActionProxy(IAction delegate, String gateKey) {
        this.delegate = delegate;
        this.gateKey = gateKey;
    }

    /** 注册入口：包装一次即可；已包装或空动作原样返回。 */
    public static IAction wrap(IAction action) {
        if (action == null || action instanceof GatedActionProxy) {
            return action;
        }
        return new GatedActionProxy(action, gateKeyOf(action));
    }

    /**
     * 门禁键取“有效委托实现”的类名：同一动作的别名注册（如 giant_titanic_slam 与
     * morphext:giant_titanic_slam 两个实例）与冷却代理（CooldownActionProxy 委托原版动作）
     * 共享同一门禁，别名换键绕过不可行。
     */
    private static String gateKeyOf(IAction action) {
        IAction effective = action;
        while (effective instanceof GatedActionProxy) {
            effective = ((GatedActionProxy) effective).delegate;
        }
        if (effective instanceof CooldownActionProxy) {
            effective = ((CooldownActionProxy) effective).getDelegate();
        }
        return effective.getClass().getName();
    }

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return delegate instanceof ICooldownAction ? ((ICooldownAction) delegate).getCooldownTicks(entity) : 0;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (MorphActionContext.get() != null || entity == null || entity.world == null || entity.world.isRemote) {
            // morphext 统一执行通道（已强制冷却）或客户端表现侧：直接委托，不重复计冷却
            delegate.execute(entity, morph);
            return;
        }
        long now = entity.world.getTotalWorldTime();
        if (!allow(entity, now)) {
            return;
        }
        delegate.execute(entity, morph);
        // 执行后按实际冷却值（动态冷却动作此时已写入本次取值）武装门禁
        arm(entity, now, getCooldownTicks(entity));
    }

    private boolean allow(EntityLivingBase entity, long now) {
        Map<String, long[]> gates = GATES.get(entity.getUniqueID());
        long[] gate = gates == null ? null : gates.get(gateKey);
        if (gate == null || gate[0] <= now) {
            return true;
        }
        // 轻量反馈：每秒至多提示一次，防刷包消息洪水
        if (entity instanceof EntityPlayerMP && now - gate[1] >= 20L) {
            gate[1] = now;
            ((EntityPlayerMP) entity).sendStatusMessage(
                    new TextComponentTranslation("gui.morphext.cooldown",
                            String.format(java.util.Locale.ROOT, "%.1f", (gate[0] - now) / 20.0F)), true);
        }
        return false;
    }

    private void arm(EntityLivingBase entity, long now, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return;
        }
        Map<String, long[]> gates = GATES.computeIfAbsent(entity.getUniqueID(), k -> new ConcurrentHashMap<>());
        gates.put(gateKey, new long[]{now + cooldownTicks, 0L});
    }

    /** 玩家登出清理（由 AbilityNetwork/玩家事件侧调用），避免静态表按 UUID 泄漏。 */
    public static void clear(UUID uuid) {
        if (uuid != null) {
            GATES.remove(uuid);
        }
    }
}

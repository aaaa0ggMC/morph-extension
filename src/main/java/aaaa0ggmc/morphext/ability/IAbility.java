package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 能力接口 —— 整个能力系统的契约。
 *
 * 其他模组（addon）只需实现本接口并调用 {@link AbilityRegistry#register(IAbility)}，
 * 能力就会自动出现在轮盘搜索与配置 UI 中。
 */
public interface IAbility {
    /** 全局唯一 id，配置里按此引用（如 "metamorphmax:fire_breath"）。 */
    String getId();

    /** 展示名（UI 显示）。 */
    String getDisplayName();

    /** 图标；可为 ItemStack.EMPTY。 */
    ItemStack getIcon();

    /** 兼容旧能力：在声明的执行侧由统一执行入口调用；轮盘选中本身不释放。 */
    void onActivate(EntityPlayer player);

    /** 旧实现默认保持客户端行为；修改世界/服务端状态的新能力必须声明 SERVER。 */
    default ExecutionSide getExecutionSide() {
        return ExecutionSide.CLIENT;
    }

    /** 新 addon 应覆盖此方法返回明确结果；旧 void API 通过此兼容层接入。 */
    default ActivationResult activate(EntityPlayer player) {
        onActivate(player);
        return ActivationResult.success(getCooldownTicks(player));
    }

    /** 客户端展示/旧状态生命周期钩子；不在这里修改服务端游戏状态。 */
    default void onGranted(EntityPlayer player) {
    }

    /** 来源回收/退出世界时调用；被动执行权归原模组的包装不可重复撤销原模组效果。 */
    default void onRevoked(EntityPlayer player) {
    }

    /** 客户端展示的天生能力标记；服务端另由 Handler.isAvailable 与玩家永久权限授权。 */
    default boolean isInnate() {
        return true;
    }

    /** 基础冷却（tick，20=1秒）；0 或无覆盖表示无冷却。缩减由 AbilityCooldowns 的注册器叠加。 */
    default int getCooldownTicks(EntityPlayer player) {
        return 0;
    }

    /** 被动能力：由 morph 等授予、自动生效、不可主动释放（不出现在轮盘/搜索，只进状态栏被动区）。 */
    default boolean isPassive() {
        return false;
    }

    /** 是否在状态总览页列出（默认列出；可释放的 morph action 之类设为 false 避免噪音）。 */
    default boolean isStatusVisible() {
        return true;
    }

    /** 状态总览页被动区的来源标注（如 morph 名），默认空。 */
    default String getStatusGroup() {
        return "";
    }

    /** 状态页里点击该行是否执行激活（开关类=true；mob act 这类“释放型”=false，避免误触发）。 */
    default boolean isStateToggleable() {
        return true;
    }

    /** 详情文案（状态页右键查看），默认空。 */
    default String getDescription() {
        return "";
    }

    /**
     * 外部体系自带冷却的剩余 tick（如 Metamorph Max 动作的 MOB_ABILITY_COOLDOWN 药效）。
     * HUD / 轮盘 / 释放检查会把这份冷却和 morphext 自己的冷却一起显示，
     * 因此接了第三方动作能力必须实现它，否则玩家看不到冷却。
     */
    default int getExternalCooldownTicks(EntityPlayer player) {
        return 0;
    }

    /** 自定义图标贴图（优先于 ItemStack 图标）；返回 null 时用 getIcon()。 */
    default net.minecraft.util.ResourceLocation getIconTexture() {
        return null;
    }

    /**
     * 数据被强效卷轴清除时调用——能力在这里清理自己注入到外部存储
     * （玩家 NBT、自定义文件等）的数据。内置状态由 AbilityStateStore 自动删除。
     */
    default void onDataWiped(EntityPlayer player) {
    }
}

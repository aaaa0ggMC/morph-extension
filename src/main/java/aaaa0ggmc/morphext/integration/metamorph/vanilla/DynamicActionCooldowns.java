package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import net.minecraft.entity.EntityLivingBase;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作内动态冷却表（非破坏查询 + 自过期）——修复消费型读取脆模式（CORE-17）。
 * <p>
 * 此前各动作用 {@code Map.remove} 读取冷却（读 = 消费），{@code getCooldownTicks} 的
 * 任何额外查询（UI 查询、门禁读取等）都会把 execute 刚写入的冷却吃掉，随后激活记 0 冷却。
 * 本表改为：查询只读，冷却值按服务端世界时间自动过期回收。
 */
public final class DynamicActionCooldowns {
    private static final class Entry {
        final int value;
        final long deadline;

        Entry(int value, long deadline) {
            this.value = value;
            this.deadline = deadline;
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    /** 记录本次动作的动态冷却（execute 内调用）。ticks <= 0 时清除。 */
    public void set(EntityLivingBase entity, int ticks) {
        if (entity == null || entity.world == null) {
            return;
        }
        if (ticks <= 0) {
            entries.remove(entity.getUniqueID());
            return;
        }
        entries.put(entity.getUniqueID(), new Entry(ticks, entity.world.getTotalWorldTime() + ticks));
    }

    /** 非破坏查询：未过期返回记录值，过期即回收并返回 0。 */
    public int get(EntityLivingBase entity) {
        if (entity == null || entity.world == null) {
            return 0;
        }
        UUID uuid = entity.getUniqueID();
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return 0;
        }
        if (entry.deadline <= entity.world.getTotalWorldTime()) {
            entries.remove(uuid);
            return 0;
        }
        return entry.value;
    }
}

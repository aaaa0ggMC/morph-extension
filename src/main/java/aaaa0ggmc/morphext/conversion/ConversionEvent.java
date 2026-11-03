package aaaa0ggmc.morphext.conversion;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * 转化成功事件（规则命中/感染/被动转化都发）。
 *
 * 监听方按需发放进度、任务、统计——核心只负责「转化发生了」这件事，
 * 不关心是谁的进度。发布在 MinecraftForge.EVENT_BUS。
 */
public class ConversionEvent extends Event {
    public final EntityPlayerMP player;
    /** 来源标识：规则 id（water_ritual…）或阶段自定义标识（infection…）。 */
    public final String sourceId;
    /** 目标形态 id。 */
    public final String targetForm;

    public ConversionEvent(EntityPlayerMP player, String sourceId, String targetForm) {
        this.player = player;
        this.sourceId = sourceId;
        this.targetForm = targetForm;
    }
}

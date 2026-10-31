package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntConsumer;

/**
 * 服务端动作任务调度器 (Server Action Scheduler)。
 * 用于为原版动作提供精准的 Tick 延迟回调、周期性粒子/音效追踪与着陆判定。
 */
public final class ServerActionScheduler {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final ServerActionScheduler INSTANCE = new ServerActionScheduler();
    private static final Queue<ScheduledTask> PENDING = new ConcurrentLinkedQueue<>();
    private static final List<ScheduledTask> RUNNING = new ArrayList<>();
    private static boolean registered = false;

    private ServerActionScheduler() {
    }

    public static synchronized void init() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(INSTANCE);
        }
    }

    /**
     * 安排一个延迟 delayTicks 后执行的任务。
     */
    public static void schedule(int delayTicks, Runnable onComplete) {
        if (delayTicks <= 0) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        PENDING.add(new ScheduledTask(delayTicks, null, onComplete));
    }

    /**
     * 安排一个总计 totalTicks 的带周期回调任务。
     *
     * @param totalTicks 总持续刻数
     * @param onTick 每 tick 回调，传入当前剩余刻数
     * @param onComplete 完成时回调
     */
    public static void scheduleTicker(int totalTicks, IntConsumer onTick, Runnable onComplete) {
        if (totalTicks <= 0) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        PENDING.add(new ScheduledTask(totalTicks, onTick, onComplete));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ScheduledTask incoming;
        while ((incoming = PENDING.poll()) != null) {
            RUNNING.add(incoming);
        }

        if (RUNNING.isEmpty()) {
            return;
        }

        Iterator<ScheduledTask> it = RUNNING.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            task.remainingTicks--;

            if (task.onTick != null) {
                try {
                    task.onTick.accept(task.remainingTicks);
                } catch (Throwable t) {
                    LOGGER.error("[morphext] Error in scheduled task onTick callback", t);
                }
            }

            if (task.remainingTicks <= 0) {
                it.remove();
                if (task.onComplete != null) {
                    try {
                        task.onComplete.run();
                    } catch (Throwable t) {
                        LOGGER.error("[morphext] Error in scheduled task onComplete callback", t);
                    }
                }
            }
        }
    }

    private static class ScheduledTask {
        int remainingTicks;
        final IntConsumer onTick;
        final Runnable onComplete;

        ScheduledTask(int ticks, IntConsumer onTick, Runnable onComplete) {
            this.remainingTicks = ticks;
            this.onTick = onTick;
            this.onComplete = onComplete;
        }
    }
}

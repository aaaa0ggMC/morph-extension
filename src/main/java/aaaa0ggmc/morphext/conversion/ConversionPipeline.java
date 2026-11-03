package aaaa0ggmc.morphext.conversion;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 死亡转化管线（通用机制）：LivingDeathEvent 的唯一入口。
 *
 * 结算顺序（优先级小者先）：
 *   10 感染类阶段（整合包内容，如安卡护符）
 *   20 转化规则阶段（{@link aaaa0ggmc.morphext.conversion.rule.ConversionRuleRegistry}，数据驱动）
 *   30 被动转化阶段（预留）
 *
 * 任一方返回 true 即停止；都没有处理则按原版正常死亡。
 * 单个阶段抛异常不会中断其它阶段（记错继续）。
 */
public final class ConversionPipeline {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final List<ConversionStage> STAGES = new CopyOnWriteArrayList<>();

    private ConversionPipeline() {
    }

    public static void register(ConversionStage stage) {
        if (stage == null) {
            return;
        }
        STAGES.add(stage);
        STAGES.sort(Comparator.comparingInt(ConversionStage::priority));
        LOGGER.info("[morphext] Conversion stage registered: {} (priority {})",
                stage.getClass().getSimpleName(), stage.priority());
    }

    /** 跑一遍管线；true = 已有阶段处理本次死亡。 */
    public static boolean run(DeathContext ctx) {
        for (ConversionStage stage : STAGES) {
            try {
                if (stage.handle(ctx)) {
                    return true;
                }
            } catch (Throwable t) {
                LOGGER.error("[morphext] Conversion stage {} threw", stage.getClass().getName(), t);
            }
        }
        return false;
    }

    /** 注册 Forge 事件监听（核心 init 期调用一次）。 */
    public static void registerEvents() {
        MinecraftForge.EVENT_BUS.register(new Listener());
    }

    public static final class Listener {
        private Listener() {
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onLivingDeath(LivingDeathEvent event) {
            if (event.isCanceled() || event.getEntityLiving().world.isRemote) {
                return;
            }
            if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
                return;
            }
            DeathContext ctx = new DeathContext((EntityPlayerMP) event.getEntityLiving(), event.getSource(), event);
            if (run(ctx)) {
                event.setCanceled(true);
            }
        }
    }
}

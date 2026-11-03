package aaaa0ggmc.morphext.follower;

import aaaa0ggmc.morphext.MorphExtension;
import aaaa0ggmc.morphext.MorphExtTuning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;

import java.util.UUID;

/**
 * 随从 AI 安装器：实体加入世界时，若带 {@link Followers#OWNER_TAG} 就装上跟随 AI。
 *
 * 通用机制：只认 NBT 标签，不认任何模组、实体类或召唤来源。
 *
 * 去重说明：AI 任务本身不随存档持久化，区块重载后实体会被重建、tasks 为空，
 * 因此以「tasks 里是否已有同主人的跟随 AI」作为去重依据；
 * {@link Followers#AI_FLAG_TAG} 作为已安装标记写入 NBT（供外部查询/调试）。
 */
@Mod.EventBusSubscriber(modid = MorphExtension.MODID)
public final class FollowerHandler {
    private FollowerHandler() {
    }

    /**
     * 显式接线入口（由 {@code MorphExtension.init} 调用）。
     *
     * 事件订阅本身由类上的 {@code @Mod.EventBusSubscriber(modid = MorphExtension.MODID)}
     * 在模组加载期自动注入 {@code MinecraftForge.EVENT_BUS}，这里只做启动确认，
     * 不重复 register（Forge 事件总线不去重，重复注册会导致事件被处理两次）。
     */
    public static void init() {
        LogManager.getLogger("morph-extension")
                .info("[morphext] Follower handler wired (EntityJoinWorldEvent via @EventBusSubscriber)");
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityLiving) || entity.world == null) {
            return;
        }
        EntityLiving living = (EntityLiving) entity;
        UUID owner = Followers.getOwnerUuid(living);
        if (owner == null) {
            return;
        }
        if (hasFollowAi(living, owner)) {
            // 已经装过：只补齐标记，不重复添加任务
            living.getEntityData().setBoolean(Followers.AI_FLAG_TAG, true);
            return;
        }
        living.tasks.addTask(MorphExtTuning.FOLLOWER_AI_PRIORITY, new EntityAIFollowOwner(living, owner));
        living.getEntityData().setBoolean(Followers.AI_FLAG_TAG, true);
    }

    /** tasks 里是否已有指向同一主人的跟随 AI（防重复，也覆盖区块重载后的重建）。 */
    private static boolean hasFollowAi(EntityLiving living, UUID owner) {
        for (EntityAITasks.EntityAITaskEntry entry : living.tasks.taskEntries) {
            if (entry.action instanceof EntityAIFollowOwner
                    && owner.equals(((EntityAIFollowOwner) entry.action).getOwner())) {
                return true;
            }
        }
        return false;
    }
}

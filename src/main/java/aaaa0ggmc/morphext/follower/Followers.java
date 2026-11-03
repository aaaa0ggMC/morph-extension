package aaaa0ggmc.morphext.follower;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.util.UUID;

/**
 * 通用随从框架：用实体 NBT 标记「这个实体跟着哪个玩家」。
 *
 * 完全通用——核心不认识任何模组、任何实体类、任何召唤来源。任何机制
 * （召唤物、支线任务、变身伴生……）只要在实体上写下 OWNER_TAG 即可获得跟随行为，
 * 由 {@link FollowerHandler} 自动安装 {@link EntityAIFollowOwner}。
 *
 * 标签存放在 {@code Entity#getEntityData()}（存档里的 ForgeData），
 * 因此随从关系跨存档持久化：
 * <ul>
 *   <li>{@link #OWNER_TAG}：主人 UUID 字符串；</li>
 *   <li>{@link #SINCE_TAG}：认主时的世界时间戳（long，tick）。</li>
 * </ul>
 */
public final class Followers {
    /** 主人 UUID 字符串标签。 */
    public static final String OWNER_TAG = "ulFollowOwner";
    /** 认主时的世界时间戳（tick）。 */
    public static final String SINCE_TAG = "ulFollowSince";
    /** 跟随 AI 已安装标志（见 FollowerHandler：AI 任务不持久化，重载后以任务扫描为准）。 */
    public static final String AI_FLAG_TAG = "ulFollowAi";

    private Followers() {
    }

    /** 给实体指定主人与认主时间（世界时间戳，tick）。 */
    public static void setOwner(EntityLivingBase entity, UUID owner, long sinceWorldTime) {
        if (entity == null || owner == null) {
            return;
        }
        entity.getEntityData().setString(OWNER_TAG, owner.toString());
        entity.getEntityData().setLong(SINCE_TAG, sinceWorldTime);
    }

    /** 实体的主人 UUID；未认主或标签损坏时返回 null。 */
    public static UUID getOwnerUuid(Entity entity) {
        if (entity == null || !entity.getEntityData().hasKey(OWNER_TAG)) {
            return null;
        }
        String raw = entity.getEntityData().getString(OWNER_TAG);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** 认主时的世界时间戳；未认主返回 0。 */
    public static long getSince(Entity entity) {
        return entity == null ? 0L : entity.getEntityData().getLong(SINCE_TAG);
    }

    /** 清除随从关系（同时清掉 AI 标志，下次加入世界会重新安装跟随 AI）。 */
    public static void clearOwner(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getEntityData().removeTag(OWNER_TAG);
        entity.getEntityData().removeTag(SINCE_TAG);
        entity.getEntityData().removeTag(AI_FLAG_TAG);
    }

    /** 该实体是否是某个玩家的随从（带有效主人标签）。 */
    public static boolean isFollower(Entity entity) {
        return getOwnerUuid(entity) != null;
    }
}

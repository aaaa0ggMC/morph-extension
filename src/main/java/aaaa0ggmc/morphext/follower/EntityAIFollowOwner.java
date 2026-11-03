package aaaa0ggmc.morphext.follower;

import aaaa0ggmc.morphext.MorphExtTuning;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 通用跟随 AI：让带 {@link Followers#OWNER_TAG} 的实体跟着主人跑。
 *
 * 行为（阈值全部来自 {@link MorphExtTuning}）：
 *  - 距离 &gt; startDistance：寻路走向主人；
 *  - 距离 &lt; stopDistance：停下待机；
 *  - 距离 &gt; teleportDistance（或暂时寻不到路）：直接传送到主人身旁；
 *  - 主人不在线 / 已死亡 / 不在同一维度：原地待机，清空路径。
 *
 * 通用机制：不引用任何具体实体类、形态或整合包逻辑。
 */
public final class EntityAIFollowOwner extends EntityAIBase {
    private final EntityLiving entity;
    private final World world;
    private final UUID owner;
    /** 重算路径的倒计时（tick），避免每 tick 都跑寻路。 */
    private int repathCooldown;
    /** 已警告过一次的维度/寻路异常，避免刷屏。 */
    private boolean warned;

    public EntityAIFollowOwner(EntityLiving entity, UUID owner) {
        this.entity = entity;
        this.world = entity.world;
        this.owner = owner;
        // 3 = MOVE | LOOK：与移动、朝向类 AI 互斥
        this.setMutexBits(3);
    }

    public UUID getOwner() {
        return owner;
    }

    @Override
    public boolean shouldExecute() {
        EntityPlayer player = findOwner();
        return player != null && this.entity.getDistanceSq(player) > square(MorphExtTuning.FOLLOWER_START_DISTANCE);
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityPlayer player = findOwner();
        return player != null && this.entity.getDistanceSq(player) > square(MorphExtTuning.FOLLOWER_STOP_DISTANCE);
    }

    @Override
    public void startExecuting() {
        this.repathCooldown = 0;
    }

    @Override
    public void resetTask() {
        PathNavigate navigator = this.entity.getNavigator();
        if (navigator != null) {
            navigator.clearPath();
        }
    }

    @Override
    public void updateTask() {
        EntityPlayer player = findOwner();
        if (player == null) {
            // 主人不在线 / 跨维度 / 已死亡：待机
            resetTask();
            return;
        }
        PathNavigate navigator = this.entity.getNavigator();
        if (navigator == null) {
            return;
        }
        double distanceSq = this.entity.getDistanceSq(player);
        if (distanceSq > square(MorphExtTuning.FOLLOWER_TELEPORT_DISTANCE)) {
            teleportNextTo(player);
            return;
        }
        if (distanceSq <= square(MorphExtTuning.FOLLOWER_STOP_DISTANCE)) {
            navigator.clearPath();
            return;
        }
        this.entity.getLookHelper().setLookPositionWithEntity(player, 10.0F, this.entity.getVerticalFaceSpeed());
        if (this.repathCooldown-- > 0 && !navigator.noPath()) {
            return;
        }
        this.repathCooldown = 10;
        boolean pathed = navigator.tryMoveToEntityLiving(player, MorphExtTuning.FOLLOWER_MOVE_SPEED);
        if (!pathed && !this.warned) {
            // 寻不到路（隔墙/虚空）：记录一次，随后靠超距传送兜底
            this.warned = true;
        } else if (pathed) {
            this.warned = false;
        }
    }

    /** 主人：同维度的在线存活玩家。 */
    private EntityPlayer findOwner() {
        if (this.owner == null) {
            return null;
        }
        EntityPlayer player = this.world.getPlayerEntityByUUID(this.owner);
        if (player == null || player.isDead || player.dimension != this.entity.dimension) {
            return null;
        }
        return player;
    }

    /** 直接落到主人身旁（保持一点偏移，避免叠在一起）。 */
    private void teleportNextTo(EntityPlayer player) {
        double offset = MorphExtTuning.FOLLOWER_TELEPORT_OFFSET;
        double x = player.posX + offset;
        double y = player.posY;
        double z = player.posZ + offset;
        this.entity.setPositionAndUpdate(x, y, z);
        resetTask();
    }

    private static double square(double value) {
        return value * value;
    }
}

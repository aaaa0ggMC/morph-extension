package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;


/**
 * 疾跑开关（状态型能力示范）。
 *
 * 状态语义由能力自己定义：布尔开关由服务端按玩家 UUID 持久化，客户端读取快照。
 * 开启后效果持续——每个 client tick 通过 {@link #onTick} 保持疾跑，
 * 直到再次激活关闭，或在状态总览页面点击关闭；切换选中其他能力不影响本状态。
 */
public class SprintAbility extends Ability implements IStatefulAbility {
    private boolean enabled() {
        JsonObject state = AbilityStateStore.get(getId());
        return CoreServerAbilities.isEnabled(state);
    }

    @Override
    public ExecutionSide getExecutionSide() { return ExecutionSide.SERVER; }

    public SprintAbility() {
        super("morphext:sprint", "Sprint");
        // 皮鞋语义正确，直接用原版图标
        this.icon(new ItemStack(Items.LEATHER_BOOTS));
    }

    @Override
    public String getDisplayName() {
        return "Sprint";
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        return enabled() ? "On" : "Off";
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return enabled();
    }

    @Override
    public void onTick(EntityPlayer player) {
        if (!enabled() || player == null) {
            return;
        }
        // 自动疾跑判定：仅在玩家有明确向前移动输入且未潜行、饥饿足够、未水平碰撞阻挡时保持疾跑
        // 玩家静止时绝不强行维持疾跑，避免原版在脚底每 tick 持续生成奔跑扬尘方块粒子
        if (player.world.isRemote && player instanceof net.minecraft.client.entity.EntityPlayerSP) {
            net.minecraft.client.entity.EntityPlayerSP sp = (net.minecraft.client.entity.EntityPlayerSP) player;
            if (sp.movementInput != null && sp.movementInput.moveForward > 0.8F && !sp.isSneaking()
                    && (sp.capabilities.isCreativeMode || sp.getFoodStats().getFoodLevel() > 6)
                    && !sp.collidedHorizontally) {
                if (!sp.isSprinting()) {
                    sp.setSprinting(true);
                }
            }
        }
    }

    @Override
    public void onActivate(EntityPlayer player) {
        if (player != null && player.world.isRemote) {
            aaaa0ggmc.morphext.client.ClientAbilityExecutor.activate(this);
        }
    }

    @Override
    public JsonObject saveState() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled());
        return json;
    }

    @Override
    public void loadState(JsonObject state) {
        // 状态直接从当前连接的服务端镜像读取；不再保存在共享实例字段中。
    }

    @Override
    public String getDescription() {
        return "疾跑锁定。释放一次开启，再次释放关闭；开启后持续维持疾跑，松开释放键、停止移动或切换能力都不会关闭。";
    }
}

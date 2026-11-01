package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;


/**
 * 疾跑开关（客户端本地功能）。
 *
 * 疾跑是纯客户端行为：开关状态保存在客户端本地，开启后每个 client tick 直接
 * 通过 {@link #onTick} 维持疾跑，不依赖服务端状态或网络往返。
 * 进入世界后首次读取时会从服务端镜像（旧版本的按存档状态）种子化一次，
 * 保证老存档的开关状态不丢；此后切换只作用于本地，重启游戏后需重新开启。
 */
public class SprintAbility extends Ability implements IStatefulAbility {
    /** null = 尚未从旧存档镜像种子化。 */
    private static Boolean localEnabled = null;

    private boolean enabled() {
        Boolean local = localEnabled;
        if (local == null) {
            // 只读种子：兼容旧版本存进服务端 states 的开关状态。
            local = CoreServerAbilities.isEnabled(AbilityStateStore.get(getId()));
            localEnabled = local;
        }
        return local;
    }

    /** 断线/退出世界时清除本地缓存，下次进入重新种子化。 */
    public static void resetLocal() {
        localEnabled = null;
    }

    @Override
    public ExecutionSide getExecutionSide() { return ExecutionSide.CLIENT; }

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
        return net.minecraft.util.text.translation.I18n.translateToLocal(
                enabled() ? "gui.morphext.state.on" : "gui.morphext.state.off");
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return enabled();
    }

    @Override
    public ActivationResult activate(EntityPlayer player) {
        if (player == null || !player.world.isRemote) {
            return ActivationResult.unavailable();
        }
        localEnabled = !enabled();
        return ActivationResult.success(0);
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
        // 客户端本地开关，无服务端往返。
        if (player != null && player.world.isRemote) {
            activate(player);
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
        // 本地开关状态；不依赖服务端状态存储。
    }

    @Override
    public String getDescription() {
        return "疾跑锁定（客户端开关）。释放一次开启，再次释放关闭；开启后持续维持疾跑，"
                + "松开释放键、停止移动或切换能力都不会关闭。";
    }
}

package aaaa0ggmc.morphext.ability;

import com.google.gson.JsonObject;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 身形规整 (Human Stature / Keep Human Size) 状态能力。
 *
 * 作用：变身期间控制玩家的外观模型与碰撞箱尺寸自适应，防止模型过大/过小导致视野遮挡或行动受限。
 * 包含三个轮转状态：
 *   1. 人类外观 (HUMAN_MODEL)：保留全部变身主动与被动技能，但直接使用原生玩家人类模型与标准碰撞箱；
 *   2. 等高缩放 (SMART_SCALE)：保留变身怪物模型外观，但智能将其高度缩放到标准人类等身高（约 1.8 格高）；
 *   3. 关闭 (OFF)：原生大小（默认）。
 */
public class HumanStatureAbility extends Ability implements IStatefulAbility {
    public static final String ID = "morphext:human_stature";

    public enum Mode {
        OFF(0, "Off", "关闭"),
        HUMAN_MODEL(1, "Human Model", "人类模型"),
        SMART_SCALE(2, "Smart Scale", "等高缩放");

        public final int id;
        public final String labelEn;
        public final String labelZh;

        Mode(int id, String labelEn, String labelZh) {
            this.id = id;
            this.labelEn = labelEn;
            this.labelZh = labelZh;
        }

        public static Mode fromId(int id) {
            for (Mode m : values()) {
                if (m.id == id) return m;
            }
            return OFF;
        }

        public Mode next() {
            int nextId = (this.id + 1) % values().length;
            return fromId(nextId);
        }
    }

    public HumanStatureAbility() {
        super(ID, "Human Stature");
        // 盔甲架作为人形体态的代表图标
        this.icon(new ItemStack(Items.ARMOR_STAND));
    }

    @Override
    public boolean isInnate() {
        // 固定给玩家的天生自带能力，无需卷轴解锁
        return true;
    }

    @Override
    public ExecutionSide getExecutionSide() {
        return ExecutionSide.SERVER;
    }

    public static Mode getClientMode() {
        JsonObject state = AbilityStateStore.get(ID);
        if (state.has("mode") && state.get("mode").isJsonPrimitive()) {
            return Mode.fromId(state.get("mode").getAsInt());
        }
        return Mode.OFF;
    }

    @Override
    public String getDisplayName() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            try {
                if (I18n.hasKey("ability.morphext.human_stature")) {
                    return I18n.format("ability.morphext.human_stature");
                }
            } catch (Throwable ignored) {
            }
        }
        return "身形规整";
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        Mode mode = getClientMode();
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            try {
                if (I18n.hasKey("morphext.stature.mode." + mode.id)) {
                    return I18n.format("morphext.stature.mode." + mode.id);
                }
            } catch (Throwable ignored) {
            }
        }
        return mode.labelZh;
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return getClientMode() != Mode.OFF;
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
        json.addProperty("mode", getClientMode().id);
        return json;
    }

    @Override
    public void loadState(JsonObject state) {
    }

    @Override
    public String getDescription() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            try {
                if (I18n.hasKey("ability.morphext.human_stature.desc")) {
                    return I18n.format("ability.morphext.human_stature.desc");
                }
            } catch (Throwable ignored) {
            }
        }
        return "变身身形与外观适配拟态：按键循环切换三个状态：\n1. [人类模型]：保留全部技能，但强制使用人类模型与碰撞箱；\n2. [等高缩放]：保留变身怪物外观，但智能将其高度缩放到标准人类等身高（约 1.8 格高）；\n3. [关闭]：自然大小（默认）。";
    }
}

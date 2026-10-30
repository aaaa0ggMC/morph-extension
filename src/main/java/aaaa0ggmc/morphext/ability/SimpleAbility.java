package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;

/** 回调式能力：一行代码注册一个能力，适合 demo / 简单能力。 */
public class SimpleAbility extends Ability {
    private final Consumer<EntityPlayer> action;
    private int cooldownTicks = 0;
    private String description = "";
    private ExecutionSide executionSide = ExecutionSide.CLIENT;

    public SimpleAbility executionSide(ExecutionSide side) {
        this.executionSide = java.util.Objects.requireNonNull(side);
        return this;
    }

    @Override
    public ExecutionSide getExecutionSide() { return this.executionSide; }

    public SimpleAbility(String id, String displayName, ItemStack icon, Consumer<EntityPlayer> action) {
        super(id, displayName);
        this.icon(icon);
        this.action = action;
    }

    /** 设置基础冷却（tick，20=1秒）。 */
    public SimpleAbility cooldown(int ticks) {
        this.cooldownTicks = Math.max(0, ticks);
        return this;
    }

    /** 设置详情文案（状态页右键查看）。 */
    public SimpleAbility description(String text) {
        this.description = text;
        return this;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public int getCooldownTicks(EntityPlayer player) {
        return this.cooldownTicks;
    }

    @Override
    public void onActivate(EntityPlayer player) {
        if (this.action != null) {
            this.action.accept(player);
        }
    }
}

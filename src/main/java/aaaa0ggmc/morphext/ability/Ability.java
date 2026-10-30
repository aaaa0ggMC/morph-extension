package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** 能力抽象基类：持有 id / 名称 / 图标。 */
public abstract class Ability implements IAbility {
    private final String id;
    private final String displayName;
    private ItemStack icon = ItemStack.EMPTY;

    protected Ability(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    public Ability icon(ItemStack icon) {
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        return this;
    }

    @Override
    public void onActivate(EntityPlayer player) {
    }
}

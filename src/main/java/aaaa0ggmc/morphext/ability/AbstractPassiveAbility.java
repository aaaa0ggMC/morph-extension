package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

/**
 * 被动能力抽象基类 (Abstract Passive Ability)。
 * 封装了基础的 id、本地化名称、图标与默认无操作生命周期。
 */
public abstract class AbstractPassiveAbility implements IPassiveAbility {
    private final String id;
    private final String nameKey;
    private final String descKey;
    private final ItemStack icon;

    public AbstractPassiveAbility(String id, String nameKey, String descKey, ItemStack icon) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon != null ? icon : ItemStack.EMPTY;
    }

    public AbstractPassiveAbility(String id, String nameKey, String descKey) {
        this(id, nameKey, descKey, ItemStack.EMPTY);
    }

    public AbstractPassiveAbility(String id) {
        this(id, "morphext.ability." + id.replace(':', '.') + ".name",
                "morphext.ability." + id.replace(':', '.') + ".desc", ItemStack.EMPTY);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        if (nameKey != null && I18n.canTranslate(nameKey)) {
            return I18n.translateToLocal(nameKey);
        }
        return id;
    }

    @Override
    public String getDescription() {
        if (descKey != null && I18n.canTranslate(descKey)) {
            return I18n.translateToLocal(descKey);
        }
        return "";
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Override
    public void onActivate(EntityPlayer player) {
        // 被动能力默认不可主动激活
    }
}

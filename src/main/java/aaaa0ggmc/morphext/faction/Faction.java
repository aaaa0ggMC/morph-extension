package aaaa0ggmc.morphext.faction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 细分阵营实体定义。
 */
public class Faction {
    private final String id;
    private final String nameKey;
    private final String descKey;
    private final ItemStack icon;
    private final List<Predicate<EntityLivingBase>> predicates = new ArrayList<>();

    public Faction(String id, String nameKey, String descKey, ItemStack icon) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon != null ? icon : ItemStack.EMPTY;
    }

    public Faction(String id, String nameKey, String descKey) {
        this(id, nameKey, descKey, ItemStack.EMPTY);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        if (nameKey != null && I18n.canTranslate(nameKey)) {
            return I18n.translateToLocal(nameKey);
        }
        return id;
    }

    public String getDescription() {
        if (descKey != null && I18n.canTranslate(descKey)) {
            return I18n.translateToLocal(descKey);
        }
        return "";
    }

    public ItemStack getIcon() {
        return icon;
    }

    public Faction addPredicate(Predicate<EntityLivingBase> predicate) {
        if (predicate != null) {
            this.predicates.add(predicate);
        }
        return this;
    }

    /**
     * 判断某个生物实体是否属于该阵营。
     */
    public boolean contains(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        for (Predicate<EntityLivingBase> p : predicates) {
            try {
                if (p.test(entity)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}

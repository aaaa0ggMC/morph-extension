package aaaa0ggmc.morphext.ability;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.translation.I18n;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动能力抽象基类 (Abstract Active Ability)。
 * 封装基础 ID、动态冷却管理、手持物检查与安全产出工具。
 */
public abstract class AbstractActiveAbility implements IActiveAbility {
    private final String id;
    private final String nameKey;
    private final String descKey;
    private final ItemStack icon;
    private final Map<UUID, Integer> dynamicCooldowns = new ConcurrentHashMap<>();

    public AbstractActiveAbility(String id, String nameKey, String descKey, ItemStack icon) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon != null ? icon : ItemStack.EMPTY;
    }

    public AbstractActiveAbility(String id, String nameKey, String descKey) {
        this(id, nameKey, descKey, ItemStack.EMPTY);
    }

    public AbstractActiveAbility(String id) {
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
        // 服务端统一走 activate(player, context)
    }

    public void setDynamicCooldown(EntityPlayer player, int ticks) {
        if (player != null) {
            dynamicCooldowns.put(player.getUniqueID(), ticks);
        }
    }

    @Override
    public int getCooldownTicks(EntityPlayer player) {
        if (player != null) {
            Integer cd = dynamicCooldowns.get(player.getUniqueID());
            if (cd != null) {
                return cd;
            }
        }
        return getDefaultCooldownTicks();
    }

    public int getDefaultCooldownTicks() {
        return 0;
    }

    /**
     * 工具方法：优先检查主手，其次检查副手
     */
    public static EnumHand findHeldItem(EntityPlayer player, net.minecraft.item.Item item) {
        if (player.getHeldItemMainhand().getItem() == item) return EnumHand.MAIN_HAND;
        if (player.getHeldItemOffhand().getItem() == item) return EnumHand.OFF_HAND;
        return null;
    }

    /**
     * 工具方法：安全消耗手持物品并返还产出物（支持创造模式免消耗与背包满溢安全掉落）
     */
    public static void consumeAndGive(EntityPlayer player, EnumHand hand, ItemStack result) {
        ItemStack held = player.getHeldItem(hand);
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        if (held.isEmpty()) {
            player.setHeldItem(hand, result);
        } else if (!player.inventory.addItemStackToInventory(result)) {
            player.dropItem(result, false);
        }
    }
}

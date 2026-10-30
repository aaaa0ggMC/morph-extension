package aaaa0ggmc.morphext.integration.metamorph;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** morph 图标：实体 morph 用对应怪蛋，其他用鸡蛋兜底。 */
public final class MorphIcons {
    private MorphIcons() {
    }

    public static ItemStack forMorph(String morphName) {
        if (morphName != null && morphName.contains(":")) {
            try {
                ItemStack egg = new ItemStack(Items.SPAWN_EGG);
                net.minecraft.item.ItemMonsterPlacer.applyEntityIdToItemStack(egg, new ResourceLocation(morphName));
                return egg;
            } catch (Throwable ignored) {
                // 非实体 id 或数据包缺失时走兜底
            }
        }
        return new ItemStack(Items.EGG);
    }
}

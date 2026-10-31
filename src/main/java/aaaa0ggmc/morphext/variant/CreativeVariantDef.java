package aaaa0ggmc.morphext.variant;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 创造模式变体形态定义载体 (Creative Variant Definition)。
 * 用于跨模组解耦传递变体形态的 ResourceLocation、显示名、目标分类以及特定的变体 NBT。
 */
public class CreativeVariantDef {
    private final String entityId;
    private final String displayName;
    private final String targetCategory;
    private final NBTTagCompound entityData;

    public CreativeVariantDef(String entityId, String displayName, String targetCategory, NBTTagCompound entityData) {
        this.entityId = entityId;
        this.displayName = displayName;
        this.targetCategory = targetCategory;
        this.entityData = entityData != null ? entityData : new NBTTagCompound();
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTargetCategory() {
        return targetCategory;
    }

    public NBTTagCompound getEntityData() {
        return entityData;
    }

    /**
     * 快速构建幼年体变体定义
     */
    public static CreativeVariantDef createBaby(String entityId, String zhName, String enName, String category) {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("IsBaby", true);
        data.setInteger("Age", -24000);
        data.setString("morphext_variant", "baby");
        String display = zhName + " (幼年) - Baby " + enName;
        return new CreativeVariantDef(entityId, display, category, data);
    }
}

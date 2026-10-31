package aaaa0ggmc.morphext.variant;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * 创造模式变体形态定义载体 (Creative Variant Definition)。
 * 用于跨模组解耦传递变体形态的 ResourceLocation、显示名、目标分类、缩放比例以及特定的变体 NBT。
 */
public class CreativeVariantDef {
    private final String entityId;
    private final String baseEntityId;
    private final String displayName;
    private final String targetCategory;
    private final NBTTagCompound entityData;
    private final float scale;

    public CreativeVariantDef(String entityId, String displayName, String targetCategory, NBTTagCompound entityData) {
        this(entityId, null, displayName, targetCategory, entityData, 1.0F);
    }

    public CreativeVariantDef(String entityId, String displayName, String targetCategory, NBTTagCompound entityData, float scale) {
        this(entityId, null, displayName, targetCategory, entityData, scale);
    }

    public CreativeVariantDef(String entityId, @Nullable String baseEntityId, String displayName, String targetCategory, NBTTagCompound entityData, float scale) {
        this.entityId = entityId;
        this.baseEntityId = baseEntityId;
        this.displayName = displayName;
        this.targetCategory = targetCategory;
        this.entityData = entityData != null ? entityData : new NBTTagCompound();
        this.scale = scale > 0.0F ? scale : 1.0F;

        // 统一在 NBT 中持久化缩放标记
        if (Math.abs(this.scale - 1.0F) > 0.001F) {
            this.entityData.setFloat("morphext_scale", this.scale);
        }
    }

    public String getEntityId() {
        return entityId;
    }

    @Nullable
    public String getBaseEntityId() {
        return baseEntityId;
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

    public float getScale() {
        return scale;
    }

    /**
     * 快速构建幼年体变体定义
     */
    public static CreativeVariantDef createBaby(String entityId, String zhName, String enName, String category) {
        return createBaby(entityId, null, zhName, enName, category);
    }

    public static CreativeVariantDef createBaby(String entityId, @Nullable String baseEntityId, String zhName, String enName, String category) {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("IsBaby", true);
        data.setInteger("Age", -24000);
        data.setString("morphext_variant", "baby");
        String display = zhName + " (幼年) - Baby " + enName;
        return new CreativeVariantDef(entityId, baseEntityId, display, category, data, 0.5F);
    }
}

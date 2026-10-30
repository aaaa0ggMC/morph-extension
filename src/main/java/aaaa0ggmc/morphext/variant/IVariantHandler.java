package aaaa0ggmc.morphext.variant;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 生物形态变种处理器接口。
 * 用于定义某类实体的变种检测、NBT 规范化、尺寸缩放与动态属性修改。
 */
public interface IVariantHandler {

    /**
     * 判断当前处理器是否适用于该生物形态名或实体类型。
     */
    boolean appliesTo(String morphName, EntityLivingBase entity);

    /**
     * 从实体及其 NBT 数据中提取变种唯一键（例如 "baby", "stage_1", "stage_5", "color_red"）。
     * 若为默认形态（无特殊变种），可返回空字符串或 null。
     */
    String getVariantKey(EntityLivingBase entity, NBTTagCompound entityData);

    /**
     * 变种的友好本地化显示后缀（例如 " (幼年)", " (第3阶段)"）。
     */
    String getVariantDisplayName(String variantKey, NBTTagCompound entityData);

    /**
     * 该变种的体型缩放系数（默认 1.0F，幼年通常为 0.5F，冰火龙阶段 0.3F ~ 3.5F）。
     */
    default float getScale(String variantKey, NBTTagCompound entityData) {
        return 1.0F;
    }

    /**
     * 该变种的动态最大生命值（返回 <= 0 则使用默认 morph settings）。
     */
    default float getMaxHealth(String variantKey, NBTTagCompound entityData, float defaultHealth) {
        return defaultHealth;
    }

    /**
     * 该变种的属性修改器（例如幼年生物 +30% 移速加成，成年巨龙额外击退抗性等）。
     */
    default Map<IAttribute, AttributeModifier> getAttributeModifiers(String variantKey, NBTTagCompound entityData) {
        return Collections.emptyMap();
    }

    /**
     * 变种专有/替代的 abilities 列表。若返回 null 则沿用基础形态配置。
     */
    default List<String> getVariantAbilities(String variantKey, NBTTagCompound entityData) {
        return null;
    }

    /**
     * 规范化清洗生物 NBT 数据，去除高频递变计时器（如 Age 每 tick 减少），保留稳定的变种属性。
     */
    default void sanitizeEntityData(NBTTagCompound entityData) {
    }
}

package aaaa0ggmc.morphext.variant;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Collections;
import java.util.Map;

/**
 * 形态变种辅助处理工具类。
 */
public final class MorphVariantHelper {
    public static final String VARIANT_DELIMITER = "#";

    private MorphVariantHelper() {
    }

    /**
     * 从 AbstractMorph 中提取其变种键（若无变种则返回空字符串 ""）。
     */
    public static String getVariantKey(AbstractMorph morph) {
        if (morph == null || morph.name == null) {
            return "";
        }
        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return "";
        }
        EntityLivingBase entity = null;
        NBTTagCompound entityData = null;
        if (morph instanceof EntityMorph) {
            EntityMorph em = (EntityMorph) morph;
            entity = em.getEntity();
            entityData = em.getEntityData();
        }
        String key = handler.getVariantKey(entity, entityData);
        return key != null ? key : "";
    }

    /**
     * 为给定的 morph 构造唯一的能力 ID（如 metamorph:morph:minecraft:zombie#baby）。
     */
    public static String buildMorphAbilityId(String prefix, AbstractMorph morph) {
        if (morph == null || morph.name == null) {
            return prefix;
        }
        String variantKey = getVariantKey(morph);
        if (variantKey.isEmpty()) {
            return prefix + morph.name;
        }
        return prefix + morph.name + VARIANT_DELIMITER + variantKey;
    }

    /**
     * 从能力 ID 中剥离变体后缀，获取原始 morph 名称。
     */
    public static String getBaseMorphName(String morphId) {
        if (morphId == null) {
            return "";
        }
        int hashIdx = morphId.indexOf(VARIANT_DELIMITER);
        return hashIdx != -1 ? morphId.substring(0, hashIdx) : morphId;
    }

    /**
     * 从能力 ID 中提取变种键（若无则返回空字符串）。
     */
    public static String extractVariantKey(String fullId) {
        if (fullId == null) {
            return "";
        }
        int hashIdx = fullId.indexOf(VARIANT_DELIMITER);
        return hashIdx != -1 ? fullId.substring(hashIdx + 1) : "";
    }

    /**
     * 获取形态的变种修饰完整显示名称（如 "僵尸 (幼年)"）。
     */
    public static String getDisplayNameWithVariant(AbstractMorph morph) {
        if (morph == null) {
            return "";
        }
        String baseName = morph.getDisplayName();
        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return baseName;
        }
        NBTTagCompound entityData = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = getVariantKey(morph);
        if (variantKey.isEmpty()) {
            return baseName;
        }
        String suffix = handler.getVariantDisplayName(variantKey, entityData);
        return baseName + (suffix != null ? suffix : "");
    }

    /**
     * 获取变种的体型缩放系数。
     */
    public static float getScale(AbstractMorph morph) {
        if (morph == null) {
            return 1.0F;
        }
        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return (morph instanceof EntityMorph) ? ((EntityMorph) morph).scale : 1.0F;
        }
        NBTTagCompound entityData = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = getVariantKey(morph);
        return handler.getScale(variantKey, entityData);
    }

    /**
     * 获取变种的动态最大生命值。
     */
    public static float getMaxHealth(AbstractMorph morph, float defaultHealth) {
        if (morph == null) {
            return defaultHealth;
        }
        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return defaultHealth;
        }
        NBTTagCompound entityData = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = getVariantKey(morph);
        return handler.getMaxHealth(variantKey, entityData, defaultHealth);
    }

    /**
     * 获取变种的属性修改器。
     */
    public static Map<IAttribute, AttributeModifier> getAttributeModifiers(AbstractMorph morph) {
        if (morph == null) {
            return Collections.emptyMap();
        }
        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return Collections.emptyMap();
        }
        NBTTagCompound entityData = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = getVariantKey(morph);
        return handler.getAttributeModifiers(variantKey, entityData);
    }

    /**
     * 判断一个已获得的 morph 是否精确匹配请求的带有变种后缀的 ID。
     */
    public static boolean matches(AbstractMorph morph, String morphPrefix, String requestedId) {
        if (morph == null || requestedId == null) {
            return false;
        }
        String idWithoutPrefix = requestedId.startsWith(morphPrefix) ? requestedId.substring(morphPrefix.length()) : requestedId;
        String reqBase = getBaseMorphName(idWithoutPrefix);
        String reqVariant = extractVariantKey(idWithoutPrefix);

        if (!morph.name.equals(reqBase)) {
            return false;
        }
        String morphVariant = getVariantKey(morph);
        return reqVariant.equalsIgnoreCase(morphVariant);
    }
}

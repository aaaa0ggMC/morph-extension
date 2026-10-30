package aaaa0ggmc.morphext.variant.vanilla;

import aaaa0ggmc.morphext.variant.IVariantHandler;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 原版及通用幼年生物处理器（支持 EntityAgeable 与 EntityZombie 系列）。
 */
public class AgeableVariantHandler implements IVariantHandler {
    public static final String VARIANT_BABY = "baby";
    public static final UUID BABY_SPEED_MODIFIER_UUID = UUID.fromString("b97f5a11-8071-4648-96ef-1cf1a7206123");
    private static final AttributeModifier BABY_SPEED_MODIFIER =
            new AttributeModifier(BABY_SPEED_MODIFIER_UUID, "Baby morph speed bonus", 0.30D, 1); // +30% 移速

    @Override
    public boolean appliesTo(String morphName, EntityLivingBase entity) {
        if (entity instanceof EntityAgeable || entity instanceof EntityZombie) {
            return true;
        }
        if (morphName != null) {
            String lower = morphName.toLowerCase();
            return lower.contains("zombie") || lower.contains("cow") || lower.contains("pig")
                    || lower.contains("sheep") || lower.contains("chicken") || lower.contains("wolf")
                    || lower.contains("ocelot") || lower.contains("villager") || lower.contains("horse");
        }
        return false;
    }

    @Override
    public String getVariantKey(EntityLivingBase entity, NBTTagCompound entityData) {
        if (entity instanceof EntityAgeable && ((EntityAgeable) entity).isChild()) {
            return VARIANT_BABY;
        }
        if (entity instanceof EntityZombie && ((EntityZombie) entity).isChild()) {
            return VARIANT_BABY;
        }
        if (entityData != null) {
            if (entityData.hasKey("IsBaby") && entityData.getBoolean("IsBaby")) {
                return VARIANT_BABY;
            }
            if (entityData.hasKey("Age") && entityData.getInteger("Age") < 0) {
                return VARIANT_BABY;
            }
        }
        return "";
    }

    @Override
    public String getVariantDisplayName(String variantKey, NBTTagCompound entityData) {
        if (VARIANT_BABY.equals(variantKey)) {
            if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
                try {
                    if (I18n.hasKey("morphext.variant.baby")) {
                        return " " + I18n.format("morphext.variant.baby");
                    }
                } catch (Throwable ignored) {
                }
            }
            return " (幼年)";
        }
        return "";
    }

    @Override
    public float getScale(String variantKey, NBTTagCompound entityData) {
        if (VARIANT_BABY.equals(variantKey)) {
            return 0.5F; // 幼年体型半额缩放
        }
        return 1.0F;
    }

    @Override
    public float getMaxHealth(String variantKey, NBTTagCompound entityData, float defaultHealth) {
        if (VARIANT_BABY.equals(variantKey)) {
            // 幼年生命值约为成年的 60%，最低保证 6 点（3颗心）
            return Math.max(6.0F, (float) Math.floor(defaultHealth * 0.6F));
        }
        return defaultHealth;
    }

    @Override
    public Map<IAttribute, AttributeModifier> getAttributeModifiers(String variantKey, NBTTagCompound entityData) {
        if (VARIANT_BABY.equals(variantKey)) {
            Map<IAttribute, AttributeModifier> modifiers = new HashMap<>();
            modifiers.put(SharedMonsterAttributes.MOVEMENT_SPEED, BABY_SPEED_MODIFIER);
            return modifiers;
        }
        return Collections.emptyMap();
    }

    @Override
    public void sanitizeEntityData(NBTTagCompound entityData) {
        if (entityData == null) {
            return;
        }
        if (entityData.hasKey("Age") && entityData.getInteger("Age") < 0) {
            // 锁定固定幼年值，消除不断倒计时的计时器污染
            entityData.setInteger("Age", -24000);
            entityData.removeTag("ForcedAge");
        }
        if (entityData.hasKey("IsBaby") && entityData.getBoolean("IsBaby")) {
            entityData.setByte("IsBaby", (byte) 1);
        }
        entityData.removeTag("InLove");
    }
}

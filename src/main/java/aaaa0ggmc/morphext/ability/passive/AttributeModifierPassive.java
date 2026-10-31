package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * 属性修正被动 (Attribute Modifier Passive)。
 * 变身时赋予属性加成（如铁傀儡 +击退抗性、巨龙 +护甲、豹猫 +移动速度等），解除变身时安全剥离。
 */
public class AttributeModifierPassive extends AbstractPassiveAbility {
    private final IAttribute attribute;
    private final UUID modifierId;
    private final String modifierName;
    private final double amount;
    private final int operation;

    public AttributeModifierPassive(String id, String nameKey, String descKey, ItemStack icon,
                                    IAttribute attribute, UUID modifierId, String modifierName, double amount, int operation) {
        super(id, nameKey, descKey, icon);
        this.attribute = attribute;
        this.modifierId = modifierId;
        this.modifierName = modifierName;
        this.amount = amount;
        this.operation = operation;
    }

    public AttributeModifierPassive(String id, IAttribute attribute, UUID modifierId, double amount, int operation) {
        this(id, "morphext.passive." + id + ".name", "morphext.passive." + id + ".desc", ItemStack.EMPTY,
                attribute, modifierId, id, amount, operation);
    }

    @Override
    public void onApplied(EntityPlayer player, MorphContext context) {
        if (player == null || attribute == null) return;
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        if (instance != null) {
            AttributeModifier mod = instance.getModifier(modifierId);
            if (mod != null) {
                instance.removeModifier(modifierId);
            }
            instance.applyModifier(new AttributeModifier(modifierId, modifierName, amount, operation));
        }
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (player == null || attribute == null) return;
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
        }
    }
}

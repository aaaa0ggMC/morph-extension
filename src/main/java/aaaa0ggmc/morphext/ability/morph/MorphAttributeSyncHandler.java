package aaaa0ggmc.morphext.ability.morph;

import aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition;
import aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 形态属性同步中心 (Morph Attribute Synchronization Engine)。
 *
 * 彻底解决玩家变身后攻击力 (ATTACK_DAMAGE)、护甲 (ARMOR)、韧性 (ARMOR_TOUGHNESS)、
 * 击退抗性 (KNOCKBACK_RESISTANCE) 未与对应生物同步的问题。
 *
 * 核心机制：
 * 1. 自动从被变身实体的 IAttributeInstance 中提取原生基础值；
 * 2. 支持通过 morph_abilities.json 显式自定义覆盖；
 * 3. 采用 Forge 标准 AttributeModifier (Operation 0) 累加注入，
 *    既能叠加玩家手持武器/穿戴装备与药水加成，又能在 demorph 时 100% 干净移除，绝不污染玩家原生存档属性。
 */
public final class MorphAttributeSyncHandler {
    private static final Logger LOGGER = LogManager.getLogger("morphext-attr-sync");

    public static final UUID MORPH_ATTACK_DAMAGE_UUID = UUID.fromString("c0ff3301-0000-4000-8000-000000000001");
    public static final UUID MORPH_ARMOR_UUID = UUID.fromString("c0ff3301-0000-4000-8000-000000000002");
    public static final UUID MORPH_TOUGHNESS_UUID = UUID.fromString("c0ff3301-0000-4000-8000-000000000003");
    public static final UUID MORPH_KNOCKBACK_UUID = UUID.fromString("c0ff3301-0000-4000-8000-000000000004");
    public static final UUID MORPH_CUSTOM_BASE_UUID = UUID.fromString("c0ff3301-0000-4000-8000-000000000010");

    private static final Map<String, UUID> DYNAMIC_ATTR_UUIDS = new ConcurrentHashMap<>();

    private MorphAttributeSyncHandler() {}

    /**
     * 当玩家发生形态更替或变身时应用属性同步
     */
    public static void applyAttributes(EntityPlayer player, AbstractMorph morph) {
        clearAttributes(player);
        if (player == null || morph == null) {
            return;
        }

        double attackDamage = 0.0D;
        double armor = 0.0D;
        double toughness = 0.0D;
        double knockbackRes = 0.0D;

        // 1. 从实体模型本身提取原生属性 (Mob Native Attributes)
        if (morph instanceof EntityMorph) {
            EntityMorph em = (EntityMorph) morph;
            EntityLivingBase mob = em.getEntity(player.world);
            if (mob != null) {
                try {
                    IAttributeInstance dmgInst = mob.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
                    if (dmgInst != null) {
                        attackDamage = dmgInst.getAttributeValue();
                    }
                } catch (Throwable ignored) {}

                try {
                    IAttributeInstance armorInst = mob.getEntityAttribute(SharedMonsterAttributes.ARMOR);
                    if (armorInst != null) {
                        armor = armorInst.getAttributeValue();
                    }
                } catch (Throwable ignored) {}

                // 护甲提取盲区修复：部分实体（如深渊 Boss Chagaroth）覆写 getTotalArmorValue()
                // 直接钉死护甲而不动 ARMOR 属性（属性值为 0），只读属性会让变身 0 甲。
                // 取两者较大值作为原生护甲；JSON 显式 armor 覆盖优先（下方 def.armor）。
                try {
                    armor = Math.max(armor, (double) mob.getTotalArmorValue());
                } catch (Throwable ignored) {}

                try {
                    IAttributeInstance toughInst = mob.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);
                    if (toughInst != null) {
                        toughness = toughInst.getAttributeValue();
                    }
                } catch (Throwable ignored) {}

                try {
                    IAttributeInstance kbInst = mob.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
                    if (kbInst != null) {
                        knockbackRes = kbInst.getAttributeValue();
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. 外部 JSON 显式配置优先覆盖 (morph_abilities.json)
        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        if (def != null) {
            if (def.attackDamage != null) attackDamage = def.attackDamage;
            if (def.armor != null) armor = def.armor;
            if (def.armorToughness != null) toughness = def.armorToughness;
            if (def.knockbackResistance != null) knockbackRes = def.knockbackResistance;

            if (def.attributes != null) {
                for (Map.Entry<String, Double> entry : def.attributes.entrySet()) {
                    applyCustomAttribute(player, entry.getKey(), entry.getValue());
                }
            }
        }

        // 3. 应用到玩家属性实例
        // 玩家默认空手攻击力为 1.0D，当生物攻击力 > 1.0 时补足差值 (例如铁傀儡 15.0 -> 补 14.0)
        if (attackDamage > 0.0D && attackDamage != 1.0D) {
            double delta = attackDamage - 1.0D;
            IAttributeInstance pDmg = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            if (pDmg != null) {
                pDmg.applyModifier(new AttributeModifier(MORPH_ATTACK_DAMAGE_UUID, "Morph Attack Damage", delta, 0));
                LOGGER.debug("[morphext-attr] Synced attack damage delta {} for player {} in morph {}", delta, player.getName(), morph.name);
            }
        }

        if (armor > 0.0D) {
            IAttributeInstance pArmor = player.getEntityAttribute(SharedMonsterAttributes.ARMOR);
            if (pArmor != null) {
                pArmor.applyModifier(new AttributeModifier(MORPH_ARMOR_UUID, "Morph Armor", armor, 0));
                LOGGER.debug("[morphext-attr] Synced armor {} for player {} in morph {}", armor, player.getName(), morph.name);
            }
        }

        if (toughness > 0.0D) {
            IAttributeInstance pTough = player.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);
            if (pTough != null) {
                pTough.applyModifier(new AttributeModifier(MORPH_TOUGHNESS_UUID, "Morph Toughness", toughness, 0));
                LOGGER.debug("[morphext-attr] Synced toughness {} for player {} in morph {}", toughness, player.getName(), morph.name);
            }
        }

        if (knockbackRes > 0.0D) {
            IAttributeInstance pKb = player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
            if (pKb != null) {
                pKb.applyModifier(new AttributeModifier(MORPH_KNOCKBACK_UUID, "Morph Knockback Resistance", knockbackRes, 0));
                LOGGER.debug("[morphext-attr] Synced knockback resistance {} for player {} in morph {}", knockbackRes, player.getName(), morph.name);
            }
        }
    }

    /**
     * 当解除变身 (demorph) 或切换形态时干净移除所有已注入的修改器
     */
    public static void clearAttributes(EntityPlayer player) {
        if (player == null) return;

        try {
            IAttributeInstance pDmg = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            if (pDmg != null) pDmg.removeModifier(MORPH_ATTACK_DAMAGE_UUID);
        } catch (Throwable ignored) {}

        try {
            IAttributeInstance pArmor = player.getEntityAttribute(SharedMonsterAttributes.ARMOR);
            if (pArmor != null) pArmor.removeModifier(MORPH_ARMOR_UUID);
        } catch (Throwable ignored) {}

        try {
            IAttributeInstance pTough = player.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);
            if (pTough != null) pTough.removeModifier(MORPH_TOUGHNESS_UUID);
        } catch (Throwable ignored) {}

        try {
            IAttributeInstance pKb = player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
            if (pKb != null) pKb.removeModifier(MORPH_KNOCKBACK_UUID);
        } catch (Throwable ignored) {}

        for (Map.Entry<String, UUID> entry : DYNAMIC_ATTR_UUIDS.entrySet()) {
            try {
                IAttributeInstance inst = getPlayerAttributeByName(player, entry.getKey());
                if (inst != null) {
                    inst.removeModifier(entry.getValue());
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void applyCustomAttribute(EntityPlayer player, String attrName, Double value) {
        if (player == null || attrName == null || value == null || value == 0.0D) return;
        try {
            IAttributeInstance inst = getPlayerAttributeByName(player, attrName);
            if (inst != null) {
                if (inst.getAttribute() == SharedMonsterAttributes.MAX_HEALTH) {
                    // maxHealth 语义为“绝对总血量覆盖”（不再是 Operation 0 增量）：
                    // 直接写 base 与 metamorph Morphing.update 的每 tick 写入同值收敛，
                    // 由 MorphAbilityManager 每 tick 权威收敛维持（含变体动态血量优先级）。
                    inst.setBaseValue(Math.round(value));
                    return;
                }
                UUID uuid = DYNAMIC_ATTR_UUIDS.computeIfAbsent(attrName, k -> UUID.nameUUIDFromBytes(k.getBytes()));
                inst.removeModifier(uuid);
                inst.applyModifier(new AttributeModifier(uuid, "Morph Custom Attr " + attrName, value, 0));
            }
        } catch (Throwable t) {
            LOGGER.warn("[morphext-attr] Failed to apply custom attribute {}: {}", attrName, t.getMessage());
        }
    }

    /**
     * 读取 JSON 声明的绝对最大生命覆盖值（{@code attributes.maxHealth} / {@code attributes.generic.maxHealth}）。
     *
     * <p>语义（自血量权威收敛起定死）：该值是<b>绝对总血量</b>，不是增量；
     * 未声明时返回 null，由变体动态血量或 {@code settings.health} 依次兜底。</p>
     */
    public static Double getMaxHealthOverride(MorphAbilityDefinition def) {
        if (def == null || def.attributes == null || def.attributes.isEmpty()) {
            return null;
        }
        Double value = def.attributes.get("maxHealth");
        if (value == null) {
            value = def.attributes.get("generic.maxHealth");
        }
        return (value != null && value > 0.0D) ? value : null;
    }

    private static IAttributeInstance getPlayerAttributeByName(EntityPlayer player, String attrName) {
        if ("generic.attackDamage".equals(attrName) || "attackDamage".equals(attrName)) {
            return player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        }
        if ("generic.armor".equals(attrName) || "armor".equals(attrName)) {
            return player.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        }
        if ("generic.armorToughness".equals(attrName) || "armorToughness".equals(attrName)) {
            return player.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);
        }
        if ("generic.knockbackResistance".equals(attrName) || "knockbackResistance".equals(attrName)) {
            return player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
        }
        if ("generic.movementSpeed".equals(attrName) || "movementSpeed".equals(attrName)) {
            return player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        }
        if ("generic.maxHealth".equals(attrName) || "maxHealth".equals(attrName)) {
            return player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        }
        return null;
    }
}

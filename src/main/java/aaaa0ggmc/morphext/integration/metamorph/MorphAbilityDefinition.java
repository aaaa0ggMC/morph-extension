package aaaa0ggmc.morphext.integration.metamorph;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个形态的数据驱动能力与免疫定义（对应 JSON 条目）。
 */
public class MorphAbilityDefinition {
    public List<String> abilities = new ArrayList<>();
    public String action = "";
    public List<String> actions = new ArrayList<>();
    public List<String> immunities = new ArrayList<>();
    public List<String> damageImmunities = new ArrayList<>();
    public java.util.Map<String, String> factions = new java.util.LinkedHashMap<>();
    public Boolean hands = null;
    public String rightHand = null;
    public String leftHand = null;
    public Float handScale = null;
    public Float handOffsetX = null;
    public Float handOffsetY = null;
    public Float handOffsetZ = null;
    public Float handRotationX = null;
    public Float handRotationY = null;
    public Float handRotationZ = null;
    public Boolean rotate180 = null;
    public Float yawOffset = null;
    public Float speed = null;
    public Double attackDamage = null;
    public Double armor = null;
    public Double armorToughness = null;
    public Double knockbackResistance = null;
    public java.util.Map<String, Double> attributes = new java.util.LinkedHashMap<>();
    public java.util.Map<String, Boolean> actionAnimation = new java.util.LinkedHashMap<>();
    public java.util.List<String> silentActions = new java.util.ArrayList<>();
    public Boolean animateSameMorph = null;

    public MorphAbilityDefinition() {
    }

    public MorphAbilityDefinition(List<String> abilities, String action, List<String> immunities) {
        if (abilities != null) this.abilities.addAll(abilities);
        this.action = action != null ? action : "";
        if (immunities != null) this.immunities.addAll(immunities);
    }
}

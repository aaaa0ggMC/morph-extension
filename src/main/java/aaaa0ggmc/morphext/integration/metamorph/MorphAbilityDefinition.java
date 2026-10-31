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
    /** 需要移除的 metamorph（morphs.json）原生授予能力 id，用于修复跨文件能力冲突（如 husk 的 sun_allergy）。 */
    public List<String> removeAbilities = new ArrayList<>();
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

    /**
     * 判断给定能力名是否命中 {@link #removeAbilities}（容忍 {@code morphext:} 前缀与命名空间差异）。
     */
    public boolean removesAbility(String name) {
        if (name == null || name.isEmpty() || this.removeAbilities == null || this.removeAbilities.isEmpty()) {
            return false;
        }
        String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        for (String rm : this.removeAbilities) {
            if (rm == null) continue;
            String r = rm.trim();
            if (r.isEmpty()) continue;
            if (r.equals(name) || r.equals(bare)) {
                return true;
            }
            String rb = r.contains(":") ? r.substring(r.indexOf(':') + 1) : r;
            if (rb.equals(name) || rb.equals(bare)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 metamorph 注册表反查能力实例的注册名（如 sun_allergy）；查不到时回退类名小写。
     * 纯注册表查询，不依赖任何客户端类，服务端可用。
     */
    public static String nameOfAbility(mchorse.metamorph.api.abilities.IAbility ability) {
        if (ability == null) {
            return "";
        }
        try {
            for (java.util.Map.Entry<String, mchorse.metamorph.api.abilities.IAbility> entry
                    : mchorse.metamorph.api.MorphManager.INSTANCE.abilities.entrySet()) {
                if (entry.getValue() == ability) {
                    return entry.getKey();
                }
            }
        } catch (Throwable ignored) {
        }
        return ability.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }
}

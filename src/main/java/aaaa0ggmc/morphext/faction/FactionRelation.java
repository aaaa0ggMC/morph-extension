package aaaa0ggmc.morphext.faction;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

/**
 * 阵营间关系层级 (Faction Relationship Level)。
 *
 * 用于描述任意两个阵营之间、或者两个生物之间的基础外交与战斗关系：
 *  - ENEMY: 仇敌。势不两立，主动追击与攻击。
 *  - MODERATE: 中立/观望。互不主动侵犯；若被击打则正常还击自卫。
 *  - MEMBER: 同族/成员。视为普通成员，同族不主动攻击；受到攻击会自卫。
 *  - RELATIVE: 亲友/同盟。亲密同盟，彼此不主动攻击；误伤半血以上宽容不还手，半血以下被迫自卫；受到外敌攻击时协同反击！
 *  - LORD: 领主/君主。绝对臣服；永不攻击；玩家攻击时周围同盟集火呼应。
 */
public enum FactionRelation {
    ENEMY(0, "enemy", "morphext.faction.relation.enemy", TextFormatting.RED),
    MODERATE(1, "moderate", "morphext.faction.relation.moderate", TextFormatting.GRAY),
    MEMBER(2, "member", "morphext.faction.relation.member", TextFormatting.GREEN),
    RELATIVE(3, "relative", "morphext.faction.relation.relative", TextFormatting.AQUA),
    LORD(4, "lord", "morphext.faction.relation.lord", TextFormatting.GOLD);

    private final int priority;
    private final String id;
    private final String nameKey;
    private final TextFormatting color;

    FactionRelation(int priority, String id, String nameKey, TextFormatting color) {
        this.priority = priority;
        this.id = id;
        this.nameKey = nameKey;
        this.color = color;
    }

    public int getPriority() {
        return priority;
    }

    public String getId() {
        return id;
    }

    public TextFormatting getColor() {
        return color;
    }

    public String getDisplayName() {
        if (nameKey != null && I18n.canTranslate(nameKey)) {
            return I18n.translateToLocal(nameKey);
        }
        return name();
    }

    public String getColoredLabel() {
        return color + "[" + getDisplayName() + "]" + TextFormatting.RESET;
    }

    /**
     * 是否属于和平友善关系（不主动发起攻击）。
     */
    public boolean isPeaceful() {
        return this == MEMBER || this == RELATIVE || this == LORD;
    }

    /**
     * 是否对误伤攻击具有宽容性（半血以上不还手）。
     */
    public boolean isTolerant() {
        return this == RELATIVE || this == LORD;
    }

    /**
     * 是否绝对臣服、永不反击。
     */
    public boolean isAbsoluteObedient() {
        return this == LORD;
    }

    public static FactionRelation fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return MODERATE;
        }
        String clean = name.trim().toUpperCase();
        for (FactionRelation r : values()) {
            if (r.name().equals(clean) || r.id.equalsIgnoreCase(clean)) {
                return r;
            }
        }
        if (clean.contains("ENEMY") || clean.contains("HOSTILE")) return ENEMY;
        if (clean.contains("LORD") || clean.contains("MASTER")) return LORD;
        if (clean.contains("RELATIVE") || clean.contains("ALLY")) return RELATIVE;
        if (clean.contains("MEMBER") || clean.contains("FRIENDLY")) return MEMBER;
        return MODERATE;
    }
}

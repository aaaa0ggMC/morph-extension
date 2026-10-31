package aaaa0ggmc.morphext.faction;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

/**
 * 阵营关系层级：
 *  - MEMBER: 普通成员。阵营生物不会主动攻击玩家；若玩家主动攻击则会反击。
 *  - RELATIVE: 亲友/同伴。阵营生物不会主动攻击玩家；若玩家主动攻击，半血以上宽容不还手，仅半血以下才被迫自卫。
 *  - LORD: 领主/君主。阵营生物对玩家绝对臣服，永不反击！玩家攻击异族目标时，周围同族协同集火进攻！
 */
public enum FactionRank {
    MEMBER(1, "member", "morphext.faction.rank.member", TextFormatting.GREEN),
    RELATIVE(2, "relative", "morphext.faction.rank.relative", TextFormatting.AQUA),
    LORD(3, "lord", "morphext.faction.rank.lord", TextFormatting.GOLD);

    private final int level;
    private final String id;
    private final String nameKey;
    private final TextFormatting color;

    FactionRank(int level, String id, String nameKey, TextFormatting color) {
        this.level = level;
        this.id = id;
        this.nameKey = nameKey;
        this.color = color;
    }

    public int getLevel() {
        return level;
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

    public boolean isHigherThan(FactionRank other) {
        return other == null || this.level > other.level;
    }

    public static FactionRank fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return MEMBER;
        }
        String clean = name.trim().toLowerCase();
        if (clean.endsWith("_lord") || clean.endsWith(":lord") || clean.equals("lord")) {
            return LORD;
        }
        if (clean.endsWith("_relative") || clean.endsWith(":relative") || clean.equals("relative")) {
            return RELATIVE;
        }
        return MEMBER;
    }
}

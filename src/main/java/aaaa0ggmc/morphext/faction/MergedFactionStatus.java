package aaaa0ggmc.morphext.faction;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 当生物拥有多个细分阵营时，在 Status 状态界面中进行智能合并展示的聚合条目。
 */
public class MergedFactionStatus extends AbstractPassiveAbility implements IStatefulAbility {
    private final List<FactionPassive> passives;

    public MergedFactionStatus(List<FactionPassive> passives) {
        super("faction_merged_status", "morphext.faction.merged.name", "morphext.faction.merged.desc",
                new ItemStack(Items.BANNER, 1, 15));
        this.passives = passives != null ? passives : new ArrayList<>();
    }

    public List<FactionPassive> getPassives() {
        return passives;
    }

    @Override
    public String getDisplayName() {
        if (I18n.canTranslate("morphext.faction.merged.name")) {
            return I18n.translateToLocal("morphext.faction.merged.name");
        }
        return "阵营归属 (Faction Affiliations)";
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        Map<Faction, FactionRank> factions = FactionManager.getActiveFactions(player);
        if (factions.isEmpty()) {
            return TextFormatting.GRAY + "[无阵营]";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<Faction, FactionRank> entry : factions.entrySet()) {
            if (count > 0) sb.append(TextFormatting.DARK_GRAY).append(" · ");
            sb.append(TextFormatting.WHITE).append(entry.getKey().getDisplayName())
              .append(" ").append(entry.getValue().getColoredLabel());
            count++;
            if (count >= 3 && factions.size() > 3) {
                sb.append(TextFormatting.GRAY).append(" 等").append(factions.size()).append("个");
                break;
            }
        }
        return sb.toString();
    }

    @Override
    public List<String> getStateInfo(EntityPlayer player) {
        List<String> list = new ArrayList<>();
        Map<Faction, FactionRank> factions = FactionManager.getActiveFactions(player);
        for (Map.Entry<Faction, FactionRank> entry : factions.entrySet()) {
            Faction f = entry.getKey();
            FactionRank r = entry.getValue();
            String perk = r == FactionRank.LORD ? "永不攻击 · 号令集火" : (r == FactionRank.RELATIVE ? "半血宽容不还手" : "中立同行");
            list.add(TextFormatting.GOLD + "• " + f.getDisplayName() + " " + r.getColoredLabel() + TextFormatting.GRAY + " (" + perk + ")");
        }
        return list;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.YELLOW).append("【多阵营统御面板】\n").append(TextFormatting.GRAY);
        sb.append("当前形态融合了多个细分阵营生态，具体关系层级如下：\n\n");
        for (FactionPassive fp : passives) {
            Faction f = fp.getFaction();
            FactionRank r = fp.getRank();
            if (f != null) {
                sb.append(TextFormatting.WHITE).append("▶ ").append(f.getDisplayName()).append("：").append(r.getColoredLabel()).append("\n");
                if (r == FactionRank.LORD) {
                    sb.append(TextFormatting.DARK_GREEN).append("   - 领主特权：同族绝对臣服不攻击；你攻击异族时召集同族集火进攻！\n");
                } else if (r == FactionRank.RELATIVE) {
                    sb.append(TextFormatting.DARK_AQUA).append("   - 亲友特权：同族不主动攻击；你误击时半血以上不还手！\n");
                } else {
                    sb.append(TextFormatting.GRAY).append("   - 成员特权：同族不主动攻击；受到攻击正常自卫。\n");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return true;
    }
}

package aaaa0ggmc.morphext.faction;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 宿主变身形态细分阵营归属在 Status 状态界面中的聚合展示条目。
 *
 * 1. 唯一真实数据源：直接从 FactionManager.getActiveFactions(player) 获取当前变身生效的阵营与 Rank。
 * 2. 杜绝重复与全量列出：每个阵营仅出现一次，仅展示当前形态实际激活的关系。
 * 3. 完整双语本地化 (I18n)：随客户端语言即时切换，无硬编码字符串。
 */
public class MergedFactionStatus extends AbstractPassiveAbility implements IStatefulAbility {

    public MergedFactionStatus() {
        super("faction_merged_status", "morphext.faction.merged.name", "morphext.faction.merged.desc",
                new ItemStack(Items.BANNER, 1, 15));
    }

    public MergedFactionStatus(List<FactionPassive> ignored) {
        this();
    }

    private static String loc(String key, Object... args) {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            if (I18n.hasKey(key)) {
                return I18n.format(key, args);
            }
        }
        if (net.minecraft.util.text.translation.I18n.canTranslate(key)) {
            return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(key, args);
        }
        return key;
    }

    @Override
    public String getDisplayName() {
        return loc("morphext.faction.merged.name");
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        Map<Faction, FactionRank> factions = FactionManager.getActiveFactions(player);
        if (factions.isEmpty()) {
            return TextFormatting.GRAY + "[" + loc("morphext.faction.rank.none") + "]";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<Faction, FactionRank> entry : factions.entrySet()) {
            if (count > 0) sb.append(TextFormatting.DARK_GRAY).append(" · ");
            sb.append(TextFormatting.WHITE).append(entry.getKey().getDisplayName())
              .append(" ").append(entry.getValue().getColoredLabel());
            count++;
            if (count >= 2 && factions.size() > 2) {
                sb.append(TextFormatting.GRAY).append(" (+").append(factions.size() - 2).append(")");
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
            String perkKey = r == FactionRank.LORD ? "morphext.faction.perk.short.lord"
                    : (r == FactionRank.RELATIVE ? "morphext.faction.perk.short.relative" : "morphext.faction.perk.short.member");
            list.add(TextFormatting.GOLD + "• " + f.getDisplayName() + " " + r.getColoredLabel()
                    + TextFormatting.GRAY + " (" + loc(perkKey) + ")");
        }
        return list;
    }

    @Override
    public String getDescription() {
        EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().player;
        Map<Faction, FactionRank> factions = FactionManager.getActiveFactions(player);
        if (factions.isEmpty()) {
            return TextFormatting.GRAY + loc("morphext.faction.merged.empty");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.YELLOW).append(loc("morphext.faction.merged.header")).append("\n")
          .append(TextFormatting.GRAY).append(loc("morphext.faction.merged.subtitle")).append("\n\n");

        for (Map.Entry<Faction, FactionRank> entry : factions.entrySet()) {
            Faction f = entry.getKey();
            FactionRank r = entry.getValue();
            sb.append(TextFormatting.WHITE).append("▶ ").append(f.getDisplayName()).append(": ")
              .append(r.getColoredLabel()).append("\n");

            if (r == FactionRank.LORD) {
                sb.append(TextFormatting.DARK_GREEN).append("   - ").append(loc("morphext.faction.perk.lord")).append("\n");
            } else if (r == FactionRank.RELATIVE) {
                sb.append(TextFormatting.DARK_AQUA).append("   - ").append(loc("morphext.faction.perk.relative")).append("\n");
            } else {
                sb.append(TextFormatting.GRAY).append("   - ").append(loc("morphext.faction.perk.member")).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return !FactionManager.getActiveFactions(player).isEmpty();
    }
}

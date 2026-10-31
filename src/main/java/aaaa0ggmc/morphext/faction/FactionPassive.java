package aaaa0ggmc.morphext.faction;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 阵营归属被动能力。
 * 每个阵营为一个独立的 Passive，支持有状态的层级 (MEMBER / RELATIVE / LORD)。
 */
public class FactionPassive extends AbstractPassiveAbility implements IStatefulAbility {
    private final Faction faction;
    private FactionRank rank;

    public FactionPassive(String id, FactionRank rank, Faction faction) {
        super(id, "morphext.passive." + id + ".name", "morphext.passive." + id + ".desc",
                faction != null ? faction.getIcon() : ItemStack.EMPTY);
        this.faction = faction;
        this.rank = rank != null ? rank : FactionRank.MEMBER;
    }

    public Faction getFaction() {
        return faction;
    }

    public FactionRank getRank() {
        return rank;
    }

    public void setRank(FactionRank rank) {
        if (rank != null) {
            this.rank = rank;
        }
    }

    @Override
    public String getDisplayName() {
        if (faction != null) {
            return faction.getDisplayName() + "阵营 " + rank.getColoredLabel();
        }
        return super.getDisplayName();
    }

    @Override
    public String getDescription() {
        if (faction != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(TextFormatting.YELLOW).append(faction.getDisplayName()).append("阵营\n").append(TextFormatting.GRAY);
            sb.append("当前身份关系：").append(rank.getColoredLabel()).append("\n");
            if (rank == FactionRank.LORD) {
                sb.append("• 【领主统御】：该阵营全部生物对你绝对臣服，永不反击！\n");
                sb.append("• 【号令集火】：当你攻击异族目标时，周围同族协同集火进攻！");
            } else if (rank == FactionRank.RELATIVE) {
                sb.append("• 【血亲庇护】：该阵营生物不会主动攻击你。\n");
                sb.append("• 【失误宽容】：受到你攻击时半血以上保持宽容不还手，仅半血以下自卫反击。");
            } else {
                sb.append("• 【中立同行】：该阵营生物不会主动攻击你。\n");
                sb.append("• 【正当防卫】：若受到你的攻击会正常进行反击。");
            }
            return sb.toString();
        }
        return super.getDescription();
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        return rank.getColoredLabel();
    }

    @Override
    public List<String> getStateInfo(EntityPlayer player) {
        List<String> lines = new ArrayList<>();
        if (faction != null) {
            lines.add(TextFormatting.GOLD + "阵营: " + TextFormatting.WHITE + faction.getDisplayName());
            lines.add(TextFormatting.YELLOW + "关系等级: " + rank.getColoredLabel());
            if (rank == FactionRank.LORD) {
                lines.add(TextFormatting.GREEN + "特权: 绝对臣服 · 攻击时号令同族协同进攻");
            } else if (rank == FactionRank.RELATIVE) {
                lines.add(TextFormatting.AQUA + "特权: 误击宽容 · 生物半血以上不还手");
            } else {
                lines.add(TextFormatting.GRAY + "特权: 同行友好 · 不主动攻击玩家");
            }
        }
        return lines;
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        return true;
    }

    @Override
    public void onAttack(EntityPlayer player, EntityLivingBase target, DamageSource source, float amount, MorphContext context) {
        if (rank == FactionRank.LORD && faction != null && target != null && !player.world.isRemote) {
            // 领主攻击非本阵营目标时号令周围同伴集火
            FactionManager.rallyAllies(player, faction, target);
        }
    }
}

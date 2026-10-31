package aaaa0ggmc.morphext.command;

import aaaa0ggmc.morphext.debug.RelationDebugExporter;
import aaaa0ggmc.morphext.faction.Faction;
import aaaa0ggmc.morphext.faction.FactionGraphEngine;
import aaaa0ggmc.morphext.faction.FactionJsonConfig;
import aaaa0ggmc.morphext.faction.FactionManager;
import aaaa0ggmc.morphext.faction.FactionRank;
import aaaa0ggmc.morphext.faction.FactionRelation;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MorphExtension 整合包与数据驱动管理指令。
 *
 * 用法：
 *  - /morphext reload                     重新加载全局配置与当前存档数据包的阵营关系图谱
 *  - /morphext-debug relation missing     导出当前缺失（回退到 MODERATE）的阵营关系与未映射生物
 *  - /morphext-debug relation graph       导出当前阵营两两关系的 SVG 关系图
 *  - /morphext-debug relation all         同时导出以上两项
 *
 * 导出目录：实例目录下的 {@code aaaa0ggmc_bench/morphext}（与 modpack profiler 的归档目录一致）。
 */
public class CommandMorphExt extends CommandBase {
    @Override
    public String getName() {
        return "morphext";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("morphext-debug");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/morphext reload | /morphext-debug relation <missing|graph|all|query>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            FactionJsonConfig.reload();
            if (server.getEntityWorld() != null) {
                FactionJsonConfig.onWorldLoad(server.getEntityWorld());
            }
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                    + "[MorphExtension] 阵营拓扑关系图谱与数据包已成功重新加载！"));
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("relation")) {
            String mode = args[1].toLowerCase();
            File gameDir = server.getDataDirectory();
            if ("query".equals(mode)) {
                this.queryLookingEntity(sender);
                return;
            }
            try {
                List<File> exported = new ArrayList<>();
                if ("missing".equals(mode) || "all".equals(mode)) {
                    exported.addAll(RelationDebugExporter.exportMissing(gameDir));
                }
                if ("graph".equals(mode) || "all".equals(mode)) {
                    exported.addAll(RelationDebugExporter.exportGraph(gameDir));
                }
                if (exported.isEmpty()) {
                    sendHelp(sender);
                    return;
                }
                sender.sendMessage(new TextComponentString(TextFormatting.GOLD
                        + "[MorphExtension] 阵营关系调试导出完成："));
                for (File file : exported) {
                    sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " - "
                            + TextFormatting.WHITE + file.getName()));
                }
                sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "位于: "
                        + RelationDebugExporter.outputDirectory(gameDir).getAbsolutePath()));
                return;
            } catch (IOException | RuntimeException error) {
                throw new CommandException("导出阵营关系调试文件失败: " + error.getMessage());
            }
        }
        sendHelp(sender);
    }

    /** 查看准星前方实体的阵营、玩家身份等级、关系与索敌状态，用于排查“同阵营不反击”。 */
    private void queryLookingEntity(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        EntityLivingBase target = this.findLookTarget(player, 32.0D);
        if (target == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW
                    + "[MorphExtension] 准星前方 32 格内没有活体目标。"));
            return;
        }

        FactionGraphEngine graph = FactionGraphEngine.getInstance();
        java.util.Set<String> targetFactions = graph.resolveEntityFactions(target);
        java.util.Map<Faction, FactionRank> playerRanks = FactionManager.getActiveFactions(player);
        FactionRelation toPlayer = graph.getRelation(target, player);
        FactionRelation fromPlayer = graph.getRelation(player, target);
        boolean canAttack = target instanceof EntityLiving
                && FactionManager.canAttackPlayer((EntityLiving) target, player);
        String dummyOwner = target.getEntityData().getString("morphext_morph_owner");

        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== morphext relation query ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "Target: "
                + TextFormatting.WHITE + target.getName() + TextFormatting.GRAY + " (" + target.getClass().getName() + ")"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "Target factions: "
                + TextFormatting.WHITE + new java.util.TreeSet<>(targetFactions)));
        StringBuilder ranks = new StringBuilder();
        for (java.util.Map.Entry<Faction, FactionRank> entry : playerRanks.entrySet()) {
            if (ranks.length() > 0) ranks.append(", ");
            ranks.append(entry.getKey().getId()).append('=').append(entry.getValue().name());
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "Player ranks: "
                + TextFormatting.WHITE + (ranks.length() == 0 ? "<none>" : ranks.toString())));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "Target -> Player: "
                + TextFormatting.WHITE + toPlayer.name()
                + TextFormatting.GRAY + " | Player -> Target: " + fromPlayer.name()));
        EntityLivingBase attackTarget = target instanceof EntityLiving
                ? ((EntityLiving) target).getAttackTarget() : null;
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "Can attack player: "
                + (canAttack ? TextFormatting.GREEN + "YES" : TextFormatting.RED + "NO")
                + TextFormatting.GRAY + " (revenge=" + nameOf(target.getRevengeTarget())
                + ", attackTarget=" + nameOf(attackTarget) + ")"));
        if (dummyOwner != null && !dummyOwner.isEmpty()) {
            sender.sendMessage(new TextComponentString(TextFormatting.LIGHT_PURPLE
                    + "Morph dummy owner tag: " + dummyOwner));
        }
        // 通用 owner 元数据：方便确认目标是否是玩家自己的召唤物（召唤物不会攻击主人是预期行为）。
        try {
            for (String key : target.getEntityData().getKeySet()) {
                if (key.toLowerCase(java.util.Locale.ROOT).contains("owner")) {
                    String value = target.getEntityData().getString(key);
                    boolean yours = player.getUniqueID().toString().equalsIgnoreCase(value);
                    sender.sendMessage(new TextComponentString(TextFormatting.LIGHT_PURPLE
                            + "Owner tag " + key + "=" + value + (yours ? " (YOUR summon)" : "")));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String nameOf(net.minecraft.entity.Entity entity) {
        return entity == null ? "null" : entity.getName();
    }

    private EntityLivingBase findLookTarget(EntityPlayer player, double distance) {
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d end = eye.add(player.getLook(1.0F).scale(distance));
        RayTraceResult blockHit = player.world.rayTraceBlocks(eye, end, false, true, false);
        if (blockHit != null && blockHit.hitVec != null) {
            end = blockHit.hitVec;
        }
        AxisAlignedBB box = new AxisAlignedBB(
                Math.min(eye.x, end.x) - 1.0D, Math.min(eye.y, end.y) - 1.0D, Math.min(eye.z, end.z) - 1.0D,
                Math.max(eye.x, end.x) + 1.0D, Math.max(eye.y, end.y) + 1.0D, Math.max(eye.z, end.z) + 1.0D);
        EntityLivingBase best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EntityLivingBase candidate : player.world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (candidate == player || !candidate.isEntityAlive()) {
                continue;
            }
            RayTraceResult hit = candidate.getEntityBoundingBox().grow(0.35D).calculateIntercept(eye, end);
            if (hit == null || hit.hitVec == null) {
                continue;
            }
            double d = eye.squareDistanceTo(hit.hitVec);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    private void sendHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Morphext 调试指令 ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext reload"
                + TextFormatting.GRAY + " - 重载阵营配置与数据包"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext-debug relation missing"
                + TextFormatting.GRAY + " - 导出缺失关系（回退到 MODERATE）与未映射生物"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext-debug relation graph"
                + TextFormatting.GRAY + " - 导出阵营两两关系的 SVG 关系图"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext-debug relation all"
                + TextFormatting.GRAY + " - 同时导出以上两项"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext-debug relation query"
                + TextFormatting.GRAY + " - 查看准星目标阵营/关系/索敌状态"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "输出目录: <实例>/aaaa0ggmc_bench/morphext"));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "relation", "reload");
        }
        if (args.length == 2 && "relation".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "missing", "graph", "all", "query");
        }
        return Collections.emptyList();
    }
}

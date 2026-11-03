package aaaa0ggmc.morphext.command;

import aaaa0ggmc.morphext.debug.RelationDebugExporter;
import aaaa0ggmc.morphext.faction.Faction;
import aaaa0ggmc.morphext.faction.FactionGraphEngine;
import aaaa0ggmc.morphext.faction.FactionJsonConfig;
import aaaa0ggmc.morphext.faction.FactionManager;
import aaaa0ggmc.morphext.faction.FactionRank;
import aaaa0ggmc.morphext.faction.FactionRelation;
import aaaa0ggmc.morphext.skill.SkillDefinition;
import aaaa0ggmc.morphext.skill.SkillRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
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
import java.util.Locale;
import java.util.Set;

/**
 * MorphExtension 整合包与数据驱动管理指令。
 *
 * 用法：
 *  - /morphext reload                     重新加载全局配置与当前存档数据包的阵营关系图谱
 *  - /morphext skill give <player> <id>    授予技能
 *  - /morphext skill revoke <player> <id>  撤销技能
 *  - /morphext skill list [player]         列出技能与玩家已获得情况
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
        return "/morphext reload | /morphext skill <give|revoke|list> [player] [id]"
                + " | /morphext-debug relation <missing|graph|all|query>";
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("skill")) {
            this.handleSkill(server, sender, args);
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

    /** 技能子命令：/morphext skill <give|revoke|list> [player] [id]。 */
    private void handleSkill(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            this.sendSkillHelp(sender);
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(mode)) {
            EntityPlayerMP player = this.resolveSkillPlayer(server, sender, args, 2);
            this.listSkills(sender, player);
            return;
        }
        if ("give".equals(mode) || "revoke".equals(mode)) {
            EntityPlayerMP player = this.resolveSkillPlayer(server, sender, args, 2);
            if (args.length < 4) {
                throw new CommandException("用法: /morphext skill " + mode + " <player> <id>");
            }
            String id = args[3].trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                throw new CommandException("技能 id 不能为空");
            }
            if ("give".equals(mode)) {
                SkillRegistry.grant(player, id);
                if (SkillRegistry.byId(id) == null) {
                    sender.sendMessage(new TextComponentString(TextFormatting.YELLOW
                            + "[MorphExtension] 警告: 没有 id 为 " + id + " 的技能定义（配置未加载或已拼错）。"));
                }
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                        + "[MorphExtension] 已授予 " + player.getName() + " 技能 " + id));
            } else {
                SkillRegistry.revoke(player, id);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                        + "[MorphExtension] 已撤销 " + player.getName() + " 技能 " + id));
            }
            return;
        }
        this.sendSkillHelp(sender);
    }

    /** 取技能子命令作用的玩家；省略参数时用指令发送者自己。 */
    private EntityPlayerMP resolveSkillPlayer(MinecraftServer server, ICommandSender sender, String[] args, int index)
            throws CommandException {
        if (args.length > index && args[index] != null && !args[index].isEmpty()) {
            try {
                return CommandBase.getPlayer(server, sender, args[index]);
            } catch (PlayerNotFoundException notFound) {
                throw new CommandException("找不到玩家 " + args[index]);
            }
        }
        return CommandBase.getCommandSenderAsPlayer(sender);
    }

    private void listSkills(ICommandSender sender, EntityPlayerMP player) throws CommandException {
        List<SkillDefinition> definitions = SkillRegistry.all();
        Set<String> owned = SkillRegistry.ownedSnapshot(player);
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== morphext skills ("
                + TextFormatting.WHITE + player.getName() + TextFormatting.GOLD + ") ==="));
        if (definitions.isEmpty()) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                    + "没有加载任何技能定义（assets/<modid>/morphext/skills.json 或 config/morphext/skills.json）"));
        }
        for (SkillDefinition definition : definitions) {
            boolean has = owned.contains(definition.id);
            boolean usable = SkillRegistry.isUsable(player, definition.id);
            String type = definition.grant == null || definition.grant.type == null
                    ? "manual"
                    : definition.grant.type;
            sender.sendMessage(new TextComponentString(
                    (has ? TextFormatting.GREEN : TextFormatting.GRAY) + " - " + definition.id
                            + TextFormatting.GRAY + " [line=" + definition.line + " grant=" + type + "] "
                            + (has ? TextFormatting.GREEN + "已获得" : TextFormatting.DARK_GRAY + "未获得")
                            + TextFormatting.GRAY + " / "
                            + (usable ? TextFormatting.GREEN + "形态可用" : TextFormatting.DARK_GRAY + "形态不符")));
        }
        for (String id : owned) {
            if (SkillRegistry.byId(id) == null) {
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + " - " + id
                        + TextFormatting.GRAY + " [无定义] 已获得"));
            }
        }
    }

    private void sendSkillHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Morphext 技能指令 ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext skill give <player> <id>"
                + TextFormatting.GRAY + " - 授予技能"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext skill revoke <player> <id>"
                + TextFormatting.GRAY + " - 撤销技能"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext skill list [player]"
                + TextFormatting.GRAY + " - 列出技能与已获得情况"));
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
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext skill give <player> <id>"
                + TextFormatting.GRAY + " - 授予技能"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext skill revoke <player> <id>"
                + TextFormatting.GRAY + " - 撤销技能"));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + " /morphext skill list [player]"
                + TextFormatting.GRAY + " - 列出技能与已获得情况"));
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
            return getListOfStringsMatchingLastWord(args, "relation", "reload", "skill");
        }
        if (args.length == 2 && "relation".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "missing", "graph", "all", "query");
        }
        if (args.length == 2 && "skill".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "give", "revoke", "list");
        }
        if (args.length == 3 && "skill".equalsIgnoreCase(args[0])
                && ("give".equalsIgnoreCase(args[1]) || "revoke".equalsIgnoreCase(args[1]))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 4 && "skill".equalsIgnoreCase(args[0])
                && ("give".equalsIgnoreCase(args[1]) || "revoke".equalsIgnoreCase(args[1]))) {
            List<String> ids = new ArrayList<>();
            for (SkillDefinition definition : SkillRegistry.all()) {
                ids.add(definition.id);
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[0]));
        }
        return Collections.emptyList();
    }
}

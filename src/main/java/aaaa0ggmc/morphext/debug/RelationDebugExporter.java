package aaaa0ggmc.morphext.debug;

import aaaa0ggmc.morphext.faction.FactionGraphEngine;
import aaaa0ggmc.morphext.faction.FactionRelation;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 阵营关系图调试导出器。
 *
 * <p>输出目录固定为实例目录下的 {@code aaaa0ggmc_bench/morphext}，与 modpack profiler 的
 * {@code aaaa0ggmc_bench} 归档目录保持一致。文件只包含运行时推导结果，不修改任何配置。</p>
 */
public final class RelationDebugExporter {
    private static final String[] RELATION_ORDER = {"ENEMY", "MODERATE", "MEMBER", "RELATIVE", "LORD"};

    private RelationDebugExporter() {
    }

    public static File outputDirectory(File gameDirectory) {
        File base = gameDirectory == null ? new File(".") : gameDirectory;
        File dir = new File(base, "aaaa0ggmc_bench/morphext");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static final class PairInfo {
        final String from;
        final String to;
        final FactionRelation relation;
        final String source;

        PairInfo(String from, String to, FactionRelation relation, String source) {
            this.from = from;
            this.to = to;
            this.relation = relation;
            this.source = source;
        }

        boolean missing() {
            return "default".equals(source);
        }
    }

    private static final class Analysis {
        final List<String> factions;
        final List<PairInfo> pairs = new ArrayList<>();
        final Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        final List<String> unmappedEntities;

        Analysis(List<String> factions, List<String> unmappedEntities) {
            this.factions = factions;
            this.unmappedEntities = unmappedEntities;
            sourceCounts.put("explicit", 0);
            sourceCounts.put("inherited", 0);
            sourceCounts.put("same_faction", 0);
            sourceCounts.put("default", 0);
        }

        List<PairInfo> missingPairs() {
            List<PairInfo> out = new ArrayList<>();
            for (PairInfo pair : pairs) {
                if (pair.missing()) {
                    out.add(pair);
                }
            }
            return out;
        }
    }

    private static Analysis analyze() {
        FactionGraphEngine engine = FactionGraphEngine.getInstance();
        List<String> factions = new ArrayList<>(engine.getKnownFactions());
        Collections.sort(factions);

        Set<String> unmapped = new TreeSet<>();
        for (ResourceLocation id : EntityList.getEntityNameList()) {
            if (id == null || "minecraft:player".equals(id.toString())) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = EntityList.getClass(id);
            } catch (Throwable ignored) {
                clazz = null;
            }
            if (clazz == null || !EntityLivingBase.class.isAssignableFrom(clazz)) {
                continue;
            }
            if (engine.getFactionsForEntityId(id.toString()).isEmpty()
                    && engine.getFactionsForEntityId(id.getPath()).isEmpty()) {
                unmapped.add(id.toString());
            }
        }

        Analysis analysis = new Analysis(factions, new ArrayList<>(unmapped));
        for (String from : factions) {
            for (String to : factions) {
                FactionRelation relation = engine.resolveFactionSetsRelation(
                        Collections.singleton(from), Collections.singleton(to));
                String source = classify(engine, from, to);
                analysis.pairs.add(new PairInfo(from, to, relation, source));
                Integer count = analysis.sourceCounts.get(source);
                analysis.sourceCounts.put(source, count == null ? 1 : count + 1);
            }
        }
        return analysis;
    }

    /**
     * 判断关系来源：
     *  - same_faction: 同一阵营（解析器默认至少 RELATIVE）
     *  - explicit: 祖先集合之间存在显式关系边
     *  - inherited: 双方继承到同一个祖先阵营（默认 MEMBER）
     *  - default: 完全没有声明，落到引擎默认的 MODERATE
     */
    private static String classify(FactionGraphEngine engine, String from, String to) {
        if (from.equals(to)) {
            return engine.getDirectRelation(from, to) != null ? "explicit" : "same_faction";
        }
        Set<String> fromAncestors = engine.getFactionWithAncestors(from);
        Set<String> toAncestors = engine.getFactionWithAncestors(to);
        for (String a : fromAncestors) {
            for (String b : toAncestors) {
                if (engine.getDirectRelation(a, b) != null) {
                    return "explicit";
                }
            }
        }
        for (String a : fromAncestors) {
            if (toAncestors.contains(a)) {
                return "inherited";
            }
        }
        return "default";
    }

    public static List<File> exportMissing(File gameDirectory) throws IOException {
        File dir = outputDirectory(gameDirectory);
        Analysis analysis = analyze();
        String stamp = timestamp();

        File jsonFile = new File(dir, "relation_missing_" + stamp + ".json");
        File mdFile = new File(dir, "relation_missing_" + stamp + ".md");
        File templateFile = new File(dir, "relation_missing_" + stamp + ".template.json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generatedAt\": \"").append(escapeJson(now())).append("\",\n");
        json.append("  \"defaultFallback\": \"MODERATE\",\n");
        json.append("  \"factionCount\": ").append(analysis.factions.size()).append(",\n");
        json.append("  \"sourceCounts\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : analysis.sourceCounts.entrySet()) {
            if (!first) json.append(", ");
            json.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
            first = false;
        }
        json.append("},\n");
        json.append("  \"factions\": ").append(stringArray(analysis.factions)).append(",\n");
        json.append("  \"missingRelations\": [");
        List<PairInfo> missing = analysis.missingPairs();
        for (int i = 0; i < missing.size(); i++) {
            PairInfo pair = missing.get(i);
            if (i > 0) json.append(",");
            json.append("\n    {\"from\": \"").append(escapeJson(pair.from))
                    .append("\", \"to\": \"").append(escapeJson(pair.to))
                    .append("\", \"current\": \"").append(pair.relation.name()).append("\"}");
        }
        json.append("\n  ],\n");
        json.append("  \"unmappedLivingEntities\": ").append(stringArray(analysis.unmappedEntities)).append("\n");
        json.append("}\n");
        Files.write(jsonFile.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder md = new StringBuilder();
        md.append("# Morphext faction relation audit\n\n");
        md.append("- Generated: ").append(now()).append("\n");
        md.append("- Registered factions: ").append(analysis.factions.size()).append("\n");
        md.append("- Default fallback: `MODERATE` (neither hostile nor friendly)\n\n");
        md.append("## Relation source counts\n\n");
        md.append("| Source | Ordered pairs |\n| :--- | ---: |\n");
        for (Map.Entry<String, Integer> entry : analysis.sourceCounts.entrySet()) {
            md.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
        }
        md.append("\n## Missing relations (falling back to MODERATE)\n\n");
        md.append("These ordered faction pairs have no explicit edge and no shared ancestor. ");
        md.append("The generated `*.template.json` only contains pairs whose both ends are directly ");
        md.append("mapped factions; parent-faction pairs are listed here for inspection but omitted ");
        md.append("from the template because a generic edge may override more specific child rules.\n\n");
        if (missing.isEmpty()) {
            md.append("_No missing relations._\n");
        } else {
            md.append("| From | To | Current |\n| :--- | :--- | :--- |\n");
            int limit = Math.min(missing.size(), 400);
            for (int i = 0; i < limit; i++) {
                PairInfo pair = missing.get(i);
                md.append("| `").append(pair.from).append("` | `").append(pair.to)
                        .append("` | ").append(pair.relation.name()).append(" |\n");
            }
            if (missing.size() > limit) {
                md.append("\n_... ").append(missing.size() - limit).append(" more in the JSON file._\n");
            }
        }
        md.append("\n## Living entities without any faction mapping\n\n");
        md.append("Note: class-based `FactionRegistry` predicates are not evaluated by this audit; ");
        md.append("an entity may still be covered at runtime even if listed below.\n\n");
        if (analysis.unmappedEntities.isEmpty()) {
            md.append("_All registered living entities are covered by JSON mappings or class predicates._\n");
        } else {
            md.append("Total: ").append(analysis.unmappedEntities.size()).append("\n\n");
            int limit = Math.min(analysis.unmappedEntities.size(), 400);
            for (int i = 0; i < limit; i++) {
                md.append("- `").append(analysis.unmappedEntities.get(i)).append("`\n");
            }
            if (analysis.unmappedEntities.size() > limit) {
                md.append("- _... ").append(analysis.unmappedEntities.size() - limit).append(" more_\n");
            }
        }
        Files.write(mdFile.toPath(), md.toString().getBytes(StandardCharsets.UTF_8));

        // 可直接粘贴的 relations 模板：只包含“两端都是实际映射阵营”的缺失方向。
        // 父级阵营（如 monster/undead）的关系更抽象，贸然补 MODERATE 可能盖住子阵营的
        // ENEMY 等更具体规则，因此只列在报告里，不自动放进模板。
        Set<String> mappedFactions = new TreeSet<>();
        for (Set<String> mapped : FactionGraphEngine.getInstance().snapshotEntityMappings().values()) {
            mappedFactions.addAll(mapped);
        }
        Map<String, PairInfo> unique = new LinkedHashMap<>();
        for (PairInfo pair : missing) {
            if (!mappedFactions.contains(pair.from) || !mappedFactions.contains(pair.to)) {
                continue;
            }
            String key = pair.from.compareTo(pair.to) <= 0
                    ? pair.from + "->" + pair.to
                    : pair.to + "->" + pair.from;
            PairInfo existing = unique.get(key);
            if (existing == null) {
                unique.put(key, pair);
            } else if (!existing.from.equals(pair.from)) {
                // 两个方向都缺失：合并成一条 symmetric 边
                unique.put(key, new PairInfo(
                        existing.from.compareTo(existing.to) <= 0 ? existing.from : existing.to,
                        existing.from.compareTo(existing.to) <= 0 ? existing.to : existing.from,
                        existing.relation, "both"));
            }
        }
        StringBuilder template = new StringBuilder();
        template.append("{\n  \"relations\": [\n");
        boolean firstRelation = true;
        for (PairInfo pair : unique.values()) {
            if (!firstRelation) template.append(",\n");
            template.append("    {\"from\": \"").append(escapeJson(pair.from))
                    .append("\", \"to\": \"").append(escapeJson(pair.to))
                    .append("\", \"relation\": \"MODERATE\"");
            if ("both".equals(pair.source)) {
                template.append(", \"symmetric\": true");
            } else {
                template.append(", \"symmetric\": false");
            }
            template.append("}");
            firstRelation = false;
        }
        template.append("\n  ]\n}\n");
        Files.write(templateFile.toPath(), template.toString().getBytes(StandardCharsets.UTF_8));

        List<File> files = new ArrayList<>();
        files.add(mdFile);
        files.add(jsonFile);
        files.add(templateFile);
        return files;
    }

    public static List<File> exportGraph(File gameDirectory) throws IOException {
        File dir = outputDirectory(gameDirectory);
        FactionGraphEngine engine = FactionGraphEngine.getInstance();
        List<String> factions = new ArrayList<>(engine.getKnownFactions());
        Collections.sort(factions);
        Map<String, Set<String>> parents = engine.snapshotParentHierarchy();
        Map<String, FactionRelation> relations = engine.snapshotRelations();

        int n = Math.max(1, factions.size());
        double radius = Math.max(280.0D, n * 18.0D);
        double padding = 150.0D;
        double size = (radius + padding) * 2.0D;
        double cx = size / 2.0D;
        double cy = size / 2.0D;

        Map<String, double[]> positions = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2.0D + (2.0D * Math.PI * i / n);
            positions.put(factions.get(i), new double[]{
                    cx + Math.cos(angle) * radius,
                    cy + Math.sin(angle) * radius
            });
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append((int) size)
                .append("\" height=\"").append((int) size).append("\" viewBox=\"0 0 ")
                .append((int) size).append(" ").append((int) size).append("\">\n");
        svg.append("<defs>\n");
        for (String rel : RELATION_ORDER) {
            svg.append("  <marker id=\"arrow-").append(rel)
                    .append("\" markerWidth=\"10\" markerHeight=\"10\" refX=\"9\" refY=\"3\" orient=\"auto\">\n");
            svg.append("    <path d=\"M0,0 L9,3 L0,6 Z\" fill=\"").append(color(rel)).append("\"/>\n");
            svg.append("  </marker>\n");
        }
        svg.append("</defs>\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#12141a\"/>\n");

        // 继承树：浅灰虚线
        for (Map.Entry<String, Set<String>> entry : parents.entrySet()) {
            double[] from = positions.get(entry.getKey());
            if (from == null) continue;
            for (String parent : entry.getValue()) {
                double[] to = positions.get(parent);
                if (to == null) continue;
                svg.append("<line x1=\"").append(fmt(from[0])).append("\" y1=\"").append(fmt(from[1]))
                        .append("\" x2=\"").append(fmt(to[0])).append("\" y2=\"").append(fmt(to[1]))
                        .append("\" stroke=\"#4b5563\" stroke-width=\"1\" stroke-dasharray=\"4 4\" opacity=\"0.55\"/>\n");
            }
        }

        // 显式关系边：使用二次曲线避免双向边完全重叠
        Set<String> drawn = new TreeSet<>();
        for (Map.Entry<String, FactionRelation> entry : relations.entrySet()) {
            String[] split = entry.getKey().split("->", 2);
            if (split.length != 2) continue;
            String from = split[0];
            String to = split[1];
            double[] a = positions.get(from);
            double[] b = positions.get(to);
            if (a == null || b == null) continue;
            FactionRelation forward = entry.getValue();
            FactionRelation backward = relations.get(to + "->" + from);
            boolean symmetric = backward == forward;
            String relation = forward.name();
            String color = color(relation);

            if (from.equals(to)) {
                svg.append("<circle cx=\"").append(fmt(a[0] + 34)).append("\" cy=\"").append(fmt(a[1] - 30))
                        .append("\" r=\"20\" fill=\"none\" stroke=\"").append(color)
                        .append("\" stroke-width=\"2\"/>\n");
                continue;
            }

            if (symmetric) {
                String key = from.compareTo(to) < 0 ? from + "|" + to : to + "|" + from;
                if (!drawn.add(key)) continue;
                svg.append("<line x1=\"").append(fmt(a[0])).append("\" y1=\"").append(fmt(a[1]))
                        .append("\" x2=\"").append(fmt(b[0])).append("\" y2=\"").append(fmt(b[1]))
                        .append("\" stroke=\"").append(color).append("\" stroke-width=\"2.2\"")
                        .append(" opacity=\"0.85\"><title>").append(escapeXml(from)).append(" ↔ ")
                        .append(escapeXml(to)).append(" : ").append(relation).append("</title></line>\n");
            } else {
                // 定向边：中点向法线方向偏移，反向边偏移到另一侧
                double mx = (a[0] + b[0]) / 2.0D;
                double my = (a[1] + b[1]) / 2.0D;
                double dx = b[0] - a[0];
                double dy = b[1] - a[1];
                double len = Math.max(1.0D, Math.sqrt(dx * dx + dy * dy));
                double offset = 36.0D;
                double qx = mx - dy / len * offset;
                double qy = my + dx / len * offset;
                svg.append("<path d=\"M").append(fmt(a[0])).append(",").append(fmt(a[1]))
                        .append(" Q").append(fmt(qx)).append(",").append(fmt(qy)).append(" ")
                        .append(fmt(b[0])).append(",").append(fmt(b[1]))
                        .append("\" fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"2.2\"")
                        .append(" marker-end=\"url(#arrow-").append(relation).append(")\">")
                        .append("<title>").append(escapeXml(from)).append(" → ").append(escapeXml(to))
                        .append(" : ").append(relation).append("</title></path>\n");
            }
        }

        // 节点
        for (String faction : factions) {
            double[] pos = positions.get(faction);
            svg.append("<circle cx=\"").append(fmt(pos[0])).append("\" cy=\"").append(fmt(pos[1]))
                    .append("\" r=\"15\" fill=\"#1f2937\" stroke=\"#93c5fd\" stroke-width=\"2\"/>\n");
            double angle = Math.atan2(pos[1] - cy, pos[0] - cx);
            double lx = pos[0] + Math.cos(angle) * 34.0D;
            double ly = pos[1] + Math.sin(angle) * 34.0D;
            String anchor = Math.cos(angle) > 0.35D ? "start" : Math.cos(angle) < -0.35D ? "end" : "middle";
            double dy = Math.sin(angle) > 0.35D ? 12.0D : Math.sin(angle) < -0.35D ? -4.0D : 4.0D;
            svg.append("<text x=\"").append(fmt(lx)).append("\" y=\"").append(fmt(ly + dy))
                    .append("\" fill=\"#e5e7eb\" font-size=\"11\" font-family=\"sans-serif\" text-anchor=\"")
                    .append(anchor).append("\">").append(escapeXml(faction)).append("</text>\n");
        }

        // 图例
        svg.append("<g transform=\"translate(18,18)\" font-family=\"sans-serif\" font-size=\"12\">\n");
        svg.append("  <rect x=\"0\" y=\"0\" width=\"250\" height=\"").append(56 + RELATION_ORDER.length * 20)
                .append("\" rx=\"6\" fill=\"#0b0d12\" fill-opacity=\"0.85\" stroke=\"#374151\"/>\n");
        svg.append("  <text x=\"12\" y=\"22\" fill=\"#f3f4f6\" font-size=\"13\">Morphext Faction Relation Graph</text>\n");
        for (int i = 0; i < RELATION_ORDER.length; i++) {
            String rel = RELATION_ORDER[i];
            int y = 42 + i * 20;
            svg.append("  <line x1=\"12\" y1=\"").append(y).append("\" x2=\"42\" y2=\"").append(y)
                    .append("\" stroke=\"").append(color(rel)).append("\" stroke-width=\"3\"/>\n");
            svg.append("  <text x=\"50\" y=\"").append(y + 4).append("\" fill=\"#d1d5db\">")
                    .append(rel).append("</text>\n");
        }
        svg.append("  <text x=\"12\" y=\"").append(36 + RELATION_ORDER.length * 20 + 16)
                .append("\" fill=\"#6b7280\" font-size=\"10\">dashed grey = faction inheritance</text>\n");
        svg.append("</g>\n");

        svg.append("<text x=\"18\" y=\"").append((int) size - 14)
                .append("\" fill=\"#6b7280\" font-size=\"11\" font-family=\"sans-serif\">generated ")
                .append(escapeXml(now())).append(" | factions: ").append(factions.size())
                .append(" | explicit edges: ").append(relations.size()).append("</text>\n");
        svg.append("</svg>\n");

        String stamp = timestamp();
        File svgFile = new File(dir, "relation_graph_" + stamp + ".svg");
        File jsonFile = new File(dir, "relation_graph_" + stamp + ".json");
        Files.write(svgFile.toPath(), svg.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"generatedAt\": \"").append(escapeJson(now())).append("\",\n");
        json.append("  \"factions\": ").append(stringArray(factions)).append(",\n");
        json.append("  \"parents\": {");
        boolean first = true;
        for (Map.Entry<String, Set<String>> entry : parents.entrySet()) {
            if (!first) json.append(",");
            json.append("\n    \"").append(escapeJson(entry.getKey())).append("\": ").append(stringArray(new ArrayList<>(entry.getValue())));
            first = false;
        }
        json.append("\n  },\n  \"relations\": {");
        first = true;
        for (Map.Entry<String, FactionRelation> entry : relations.entrySet()) {
            if (!first) json.append(",");
            json.append("\n    \"").append(escapeJson(entry.getKey())).append("\": \"")
                    .append(entry.getValue().name()).append("\"");
            first = false;
        }
        json.append("\n  }\n}\n");
        Files.write(jsonFile.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));

        List<File> files = new ArrayList<>();
        files.add(svgFile);
        files.add(jsonFile);
        return files;
    }

    private static String stringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJson(values.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private static String color(String relation) {
        switch (relation) {
            case "ENEMY": return "#d9534f";
            case "MEMBER": return "#5cb85c";
            case "RELATIVE": return "#4aa3df";
            case "LORD": return "#f0ad4e";
            default: return "#9e9e9e";
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

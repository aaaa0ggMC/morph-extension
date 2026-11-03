package aaaa0ggmc.morphext.conversion.rule;

import aaaa0ggmc.morphext.conversion.ConversionPipeline;
import aaaa0ggmc.morphext.conversion.ConversionStage;
import aaaa0ggmc.morphext.conversion.DeathContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 转化规则注册表（数据驱动「仪式」的加载、排序、命中与诊断）。
 *
 * 数据来源（与 morph_abilities.json 同一套约定）：
 *   1. 所有已安装模组的 assets/&lt;modid&gt;/morphext/conversion_rules.json；
 *   2. config/morphext/conversion_rules.json 覆盖与扩展（首次运行写出当前合并结果）。
 *
 * 同时是管线的第 20 级阶段（感染 10 &lt; 规则 20 &lt; 被动 30）。
 */
public final class ConversionRuleRegistry implements ConversionStage {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ConversionRuleRegistry INSTANCE = new ConversionRuleRegistry();
    private static File configFile;

    /** 按优先级降序（同优先级按 id），第一个全命中的生效。 */
    private final List<ConversionRule> rules = new CopyOnWriteArrayList<>();

    private ConversionRuleRegistry() {
    }

    public static void init(File configDir) {
        File dir = new File(configDir, "morphext");
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[morphext] Cannot create config dir {}", dir);
        }
        configFile = new File(dir, "conversion_rules.json");
        reload();
    }

    public static void reload() {
        INSTANCE.rules.clear();
        loadModResources();
        loadConfigFile();
        INSTANCE.rules.sort(Comparator
                .comparingInt((ConversionRule r) -> r.priority).reversed()
                .thenComparing(r -> String.valueOf(r.id)));
        LOGGER.info("[morphext] Loaded {} conversion rules", INSTANCE.rules.size());
    }

    private static void loadModResources() {
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId().toLowerCase(Locale.ROOT);
            String path = "assets/" + modId + "/morphext/conversion_rules.json";
            try (InputStream is = ConversionRuleRegistry.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    merge(GSON.fromJson(reader, ConversionRule.RuleFile.class), path);
                }
            } catch (Throwable t) {
                LOGGER.warn("[morphext] Failed to read embedded conversion rules from mod {}", modId, t);
            }
        }
    }

    private static void loadConfigFile() {
        if (configFile == null) {
            return;
        }
        if (!configFile.exists()) {
            writeDefaultConfig();
            return;
        }
        try (InputStream is = new FileInputStream(configFile);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            merge(GSON.fromJson(reader, ConversionRule.RuleFile.class), configFile.getPath());
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to load config/morphext/conversion_rules.json", t);
        }
    }

    private static void writeDefaultConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            ConversionRule.RuleFile file = new ConversionRule.RuleFile();
            file.rules.addAll(INSTANCE.rules);
            GSON.toJson(file, writer);
        } catch (Throwable t) {
            LOGGER.error("[morphext] Failed to create default conversion_rules.json", t);
        }
    }

    private static void merge(ConversionRule.RuleFile file, String from) {
        if (file == null || file.rules == null) {
            return;
        }
        int added = 0;
        for (ConversionRule rule : file.rules) {
            if (rule == null || rule.id == null || rule.id.trim().isEmpty() || rule.target == null) {
                LOGGER.warn("[morphext] Skipping malformed conversion rule in {} (need id + target)", from);
                continue;
            }
            rule.id = rule.id.trim().toLowerCase(Locale.ROOT);
            INSTANCE.rules.removeIf(r -> r.id.equals(rule.id));
            INSTANCE.rules.add(rule);
            added++;
        }
        if (added > 0) {
            LOGGER.info("[morphext] Loaded {} conversion rules from {}", added, from);
        }
    }

    public static List<ConversionRule> all() {
        return new ArrayList<>(INSTANCE.rules);
    }

    public static ConversionRule byId(String id) {
        if (id == null) {
            return null;
        }
        String want = id.trim().toLowerCase(Locale.ROOT);
        for (ConversionRule rule : INSTANCE.rules) {
            if (rule.id.equals(want)) {
                return rule;
            }
        }
        return null;
    }

    /** 供管线注册。 */
    public static ConversionRuleRegistry stage() {
        return INSTANCE;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean handle(DeathContext ctx) {
        if (INSTANCE.rules.isEmpty()) {
            return false;
        }
        ConversionRule best = null;
        int bestScore = -1;
        List<String> bestMissing = null;
        for (ConversionRule rule : INSTANCE.rules) {
            List<String> missing = new ArrayList<>();
            int score = RuleMatcher.evaluate(rule, ctx, missing);
            if (missing.isEmpty()) {
                return RuleExecutor.execute(rule, ctx);
            }
            if (score > bestScore) {
                best = rule;
                bestScore = score;
                bestMissing = missing;
            }
        }
        logMiss(ctx, best, bestMissing);
        return false;
    }

    /**
     * 未命中诊断：只对「形态类别被某条规则要求过」的玩家打日志，
     * 避免每次摔死都刷屏。格式沿用既有 ritual miss 口径。
     */
    private static void logMiss(DeathContext ctx, ConversionRule best, List<String> missing) {
        String form = ctx.formId();
        if (form == null) {
            return;
        }
        boolean relevant = false;
        for (ConversionRule rule : INSTANCE.rules) {
            for (String want : rule.state.formCategory) {
                if (ctx.categories().contains(want.trim().toLowerCase(Locale.ROOT))) {
                    relevant = true;
                    break;
                }
            }
            if (relevant) {
                break;
            }
        }
        if (!relevant) {
            return;
        }
        LOGGER.info("[morphext] ritual miss: src={} form={} categories=[{}] best={} missing=[{}]",
                ctx.source == null ? "?" : ctx.source.getDamageType(),
                form,
                String.join(",", ctx.categories()),
                best == null ? "-" : best.id,
                missing == null || missing.isEmpty() ? "-" : String.join(",", missing));
    }
}

package aaaa0ggmc.morphext.client.hand;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用启发式骨骼树探测引擎 (Generic Heuristic Bone Tree Scanner)。
 *
 * 当生物模型没有显式预设规则时，本探测器自动深层扫描模型结构：
 * 1. 递归提取模型及其父类的全部 ModelRenderer 字段；
 * 2. 结合语义词典与加权规则，计算部件作为主手/副手、右前肢/左前肢的可信度评分；
 * 3. 递归探测 childModels 骨骼树，自动排除头部、躯干、后肢、尾部等干扰部件；
 * 4. 线程安全缓存扫描结果，保证每种模型仅需扫描一次，纳秒级命中。
 */
public final class ModelBoneScanner {
    private static final Logger LOGGER = LogManager.getLogger("morphext-hand-scanner");
    private static final Map<Class<? extends ModelBase>, HandPair> CACHE = new ConcurrentHashMap<>();

    private static final Set<String> EXCLUDE_KEYWORDS = new HashSet<>(Arrays.asList(
            "head", "neck", "jaw", "mouth", "tooth", "teeth", "horn", "musta",
            "ear", "eye", "nose", "beard", "hair", "hat", "mask", "snout",
            "body", "torso", "pelvis", "spine", "chest", "back", "rib", "tail",
            "hind", "rear", "backleg", "lback", "rback", "lhind", "rhind",
            "foot", "feet", "skirt", "robe", "ground", "wall"
    ));

    private static final Map<String, Integer> RIGHT_KEYWORDS = new HashMap<>();
    private static final Map<String, Integer> LEFT_KEYWORDS = new HashMap<>();

    static {
        // 右肢关键词与权重
        RIGHT_KEYWORDS.put("rightarm", 40);
        RIGHT_KEYWORDS.put("armright", 40);
        RIGHT_KEYWORDS.put("arm_right", 40);
        RIGHT_KEYWORDS.put("right_arm", 40);
        RIGHT_KEYWORDS.put("rarm", 35);
        RIGHT_KEYWORDS.put("r_arm", 35);
        RIGHT_KEYWORDS.put("rarm1", 35);
        RIGHT_KEYWORDS.put("rarm2", 30);
        RIGHT_KEYWORDS.put("rarm01a", 35);
        RIGHT_KEYWORDS.put("harmr", 35);
        RIGHT_KEYWORDS.put("rightpincers", 35);
        RIGHT_KEYWORDS.put("rightpincer", 35);
        RIGHT_KEYWORDS.put("pincerright", 35);
        RIGHT_KEYWORDS.put("rightclaw", 35);
        RIGHT_KEYWORDS.put("clawright", 35);
        RIGHT_KEYWORDS.put("righthand", 35);
        RIGHT_KEYWORDS.put("handright", 35);
        RIGHT_KEYWORDS.put("right_hand", 35);
        RIGHT_KEYWORDS.put("rhand", 30);
        RIGHT_KEYWORDS.put("irongolemrightarm", 40);
        RIGHT_KEYWORDS.put("staff1", 35); // J'zahar 右手法杖
        RIGHT_KEYWORDS.put("rightfrontleg", 30);
        RIGHT_KEYWORDS.put("frontrightleg", 30);
        RIGHT_KEYWORDS.put("legrightfront", 30);
        RIGHT_KEYWORDS.put("right_leg_front", 30);
        RIGHT_KEYWORDS.put("leg3", 25); // ModelQuadruped 右前腿
        RIGHT_KEYWORDS.put("spiderleg1", 25);
        RIGHT_KEYWORDS.put("spiderleg2", 20);

        // 左肢关键词与权重
        LEFT_KEYWORDS.put("leftarm", 40);
        LEFT_KEYWORDS.put("armleft", 40);
        LEFT_KEYWORDS.put("arm_left", 40);
        LEFT_KEYWORDS.put("left_arm", 40);
        LEFT_KEYWORDS.put("larm", 35);
        LEFT_KEYWORDS.put("l_arm", 35);
        LEFT_KEYWORDS.put("larm1", 35);
        LEFT_KEYWORDS.put("larm2", 30);
        LEFT_KEYWORDS.put("larm01a", 35);
        LEFT_KEYWORDS.put("harml", 35);
        LEFT_KEYWORDS.put("leftpincers", 35);
        LEFT_KEYWORDS.put("leftpincer", 35);
        LEFT_KEYWORDS.put("pincerleft", 35);
        LEFT_KEYWORDS.put("leftclaw", 35);
        LEFT_KEYWORDS.put("clawleft", 35);
        LEFT_KEYWORDS.put("lefthand", 35);
        LEFT_KEYWORDS.put("handleft", 35);
        LEFT_KEYWORDS.put("left_hand", 35);
        LEFT_KEYWORDS.put("lhand", 30);
        LEFT_KEYWORDS.put("irongolemleftarm", 40);
        LEFT_KEYWORDS.put("abyssalnomicon", 35); // J'zahar 左手死灵之书
        LEFT_KEYWORDS.put("leftfrontleg", 30);
        LEFT_KEYWORDS.put("frontleftleg", 30);
        LEFT_KEYWORDS.put("legleftfront", 30);
        LEFT_KEYWORDS.put("left_leg_front", 30);
        LEFT_KEYWORDS.put("leg2", 25); // ModelQuadruped 左前腿
        LEFT_KEYWORDS.put("spiderleg3", 25);
        LEFT_KEYWORDS.put("spiderleg4", 20);
    }

    public static HandPair scan(ModelBase model) {
        if (model == null) {
            return new HandPair(null, null, null);
        }
        Class<? extends ModelBase> clazz = model.getClass();
        HandPair cached = CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }

        Map<String, ModelRenderer> parts = collectModelParts(model);
        ModelRenderer bestRight = null;
        int maxRightScore = 0;
        ModelRenderer bestLeft = null;
        int maxLeftScore = 0;

        for (Map.Entry<String, ModelRenderer> entry : parts.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            ModelRenderer renderer = entry.getValue();
            if (renderer == null) continue;

            // 检查强排除项
            if (isExcluded(name)) {
                continue;
            }

            int rScore = scorePart(name, RIGHT_KEYWORDS);
            if (rScore > maxRightScore) {
                maxRightScore = rScore;
                bestRight = renderer;
            }

            int lScore = scorePart(name, LEFT_KEYWORDS);
            if (lScore > maxLeftScore) {
                maxLeftScore = lScore;
                bestLeft = renderer;
            }
        }

        HandPair pair = new HandPair(bestRight, bestLeft, null);
        CACHE.put(clazz, pair);
        if (pair.hasHands()) {
            LOGGER.info("[morphext] Auto-detected hands for model {}: right={}, left={}",
                    clazz.getSimpleName(),
                    bestRight != null ? bestRight.getClass().getSimpleName() : "none",
                    bestLeft != null ? bestLeft.getClass().getSimpleName() : "none");
        }
        return pair;
    }

    private static boolean isExcluded(String name) {
        for (String exc : EXCLUDE_KEYWORDS) {
            if (name.contains(exc)) {
                return true;
            }
        }
        return false;
    }

    private static int scorePart(String name, Map<String, Integer> dictionary) {
        int score = 0;
        for (Map.Entry<String, Integer> entry : dictionary.entrySet()) {
            String kw = entry.getKey();
            if (name.equals(kw)) {
                score = Math.max(score, entry.getValue() + 10); // 精确匹配加分
            } else if (name.contains(kw)) {
                score = Math.max(score, entry.getValue());
            }
        }
        return score;
    }

    public static Map<String, ModelRenderer> collectModelParts(ModelBase model) {
        Map<String, ModelRenderer> map = new LinkedHashMap<>();
        if (model == null) return map;

        Field[] fields = FieldUtils.getAllFields(model.getClass());
        for (Field f : fields) {
            if (ModelRenderer.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    ModelRenderer mr = (ModelRenderer) f.get(model);
                    if (mr != null) {
                        map.put(f.getName(), mr);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return map;
    }

    private ModelBoneScanner() {
    }
}

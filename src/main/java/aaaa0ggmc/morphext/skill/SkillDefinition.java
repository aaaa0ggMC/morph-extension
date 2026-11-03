package aaaa0ggmc.morphext.skill;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条技能定义（纯数据 POJO，由 gson 反序列化）。
 *
 * 核心不认识任何具体技能：id、归属线、授予条件、形态类别要求、附带能力、进度键
 * 全部由整合包/支线模组以 JSON 提供。
 *
 * JSON 示例：
 * <pre>{
 *   "id": "ul:bone_armor",
 *   "line": "ul:skeleton",
 *   "grant": { "type": "forms_seen_all", "forms": ["minecraft:skeleton", "minecraft:stray"] },
 *   "requiresFormCategory": ["ul:bone"],
 *   "abilities": ["morphext:bone_shield"],
 *   "advancement": "ul:skill/bone_armor"
 * }</pre>
 */
public final class SkillDefinition {
    /** 技能 id（稳定唯一键，小写归一）。 */
    public String id;
    /** 归属线/章节 id，仅作命名空间与调试用。 */
    public String line = "";
    /** 自动授予条件；null = 仅手动授予。 */
    public GrantSpec grant = new GrantSpec();
    /** 当前形态必须属于的类别（任一命中才「可用」）；空 = 不限形态。 */
    public List<String> requiresFormCategory = new ArrayList<>();
    /** 附带能力 id 列表，授予时由调用方按需解锁（核心只存数据，不解释）。 */
    public List<String> abilities = new ArrayList<>();
    /** 进度/状态键；null = 不记录。授予时写入带 tick 的状态对象。 */
    public String advancement = null;

    /** 授予条件。 */
    public static final class GrantSpec {
        /**
         * 条件类型：
         *  - forms_seen_all：{@link #forms} 全部出现在玩家 forms_seen 中；
         *  - boss_defeated：玩家状态键 {@code boss_defeated:<skillId>} 存在；
         *  - advancement：玩家状态键 {@link SkillDefinition#advancement} 存在；
         *  - item_used：玩家状态键 {@code item_used:<skillId>} 存在；
         *  - manual：不自动授予（默认）。
         */
        public String type = "manual";
        /** forms_seen_all 要求全部见过的形态 id 列表（归一化后比较）。 */
        public List<String> forms = new ArrayList<>();
    }
}

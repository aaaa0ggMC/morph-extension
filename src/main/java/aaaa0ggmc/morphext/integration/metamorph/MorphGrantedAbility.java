package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.ability.Ability;
import aaaa0ggmc.morphext.ability.IStatefulAbility;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.abilities.IAbility;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Metamorph morph 授予的能力（变身期间存在）：
 *  - 被动型：包装 Metamorph 的 IAbility，每 tick 调 update()，只进状态栏被动区
 *  - 主动型（mob act）：包装 Metamorph 的 IAction，可绑轮盘、按释放键执行——
 *    morph 期间“暂时常驻”，demorph 即移除
 */
public class MorphGrantedAbility extends Ability implements IStatefulAbility {
    private final String sourceMorph;
    private final String metamName;
    private final IAbility passive;
    private final IAction action;
    private final boolean passiveType;
    private final ItemStack icon;

    public MorphGrantedAbility(String sourceMorph, String metamName, IAbility passive, IAction action, ItemStack icon) {
        super(MetamorphIntegration.GRANTED_PREFIX + sourceMorph + ":" + metamName, prettify(metamName));
        this.sourceMorph = sourceMorph;
        this.metamName = metamName;
        this.passive = passive;
        this.action = action;
        this.passiveType = action == null;
        this.icon = icon;
    }

    public String getSourceMorph() {
        return this.sourceMorph;
    }

    @Override
    public String getStatusGroup() {
        return this.sourceMorph;
    }

    @Override
    public String getDescription() {
        String text = MetamorphAbilityDescriptions.get(this.metamName);
        if (text == null || text.isEmpty()) {
            text = "Metamorph " + (this.passiveType ? "ability" : "action") + ": " + this.metamName;
        }
        return text;
    }

    /* ---- 外部冷却：Metamorph Max 动作自带 MOB_ABILITY_COOLDOWN 药效 ---- */

    private static net.minecraft.potion.Potion cooldownPotion;
    private static boolean cooldownPotionLookedUp = false;

    /** 反射取 Metamorph Max 的 MOB_ABILITY_COOLDOWN 药效（软依赖：没装 Max 返回 null）。 */
    private static net.minecraft.potion.Potion cooldownPotion() {
        if (!cooldownPotionLookedUp) {
            cooldownPotionLookedUp = true;
            try {
                Class<?> clazz = Class.forName("jones.metamorphmax.init.PotionInit");
                cooldownPotion = (net.minecraft.potion.Potion)
                        clazz.getField("MOB_ABILITY_COOLDOWN").get(null);
            } catch (Throwable ignored) {
            }
        }
        return cooldownPotion;
    }

    @Override
    public int getExternalCooldownTicks(EntityPlayer player) {
        /* 只有主动型（action / mob act）有 Metamorph 自带冷却 */
        if (player == null || this.passiveType) {
            return 0;
        }
        int ext = externalCooldown(player);
        if (this.action instanceof ICooldownAction) {
            ext = Math.max(ext, ((ICooldownAction) this.action).getCooldownTicks(player));
        }
        return ext;
    }

    private int externalCooldown(EntityPlayer player) {
        net.minecraft.potion.Potion potion = cooldownPotion();
        if (potion == null) {
            return 0;
        }
        net.minecraft.potion.PotionEffect effect = player.getActivePotionEffect(potion);
        return effect == null ? 0 : effect.getDuration();
    }

    /** "night_vision" -> "Night Vision" */
    public static String prettify(String key) {
        if (key == null || key.isEmpty()) {
            return "?";
        }
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    public boolean isPassive() {
        return this.passiveType;
    }

    /** 状态总览页里主动型（mob act）也列出，让玩家看到“主动能力已从 Metamorph 获取”。 */
    @Override
    public boolean isStatusVisible() {
        return true;
    }

    /** 主动型是“释放”而非“开关”，状态页点击不应直接触发。 */
    @Override
    public boolean isStateToggleable() {
        return this.passiveType;
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    @Override
    public String getStateLabel(EntityPlayer player) {
        return this.passiveType ? "Passive" : "Castable";
    }

    @Override
    public List<String> getStateInfo(EntityPlayer player) {
        if (this.passiveType) {
            return Collections.singletonList("From morph: " + this.sourceMorph);
        }
        /* 键位显示名运行时取，不写死（玩家可能绑键盘或鼠标任意键） */
        String key = "the cast key";
        try {
            key = aaaa0ggmc.morphext.client.KeyHandler.castKeyName();
        } catch (Throwable ignored) {
        }
        return Collections.singletonList("Bind it in the wheel, then press " + key + " to cast");
    }

    @Override
    public boolean isActive(EntityPlayer player) {
        // 注册即授予（demorph 时注销），因此恒 true
        return true;
    }

    @Override
    public void onTick(EntityPlayer player) {
        // 展示型包装：被动的 update/onMorph/onDemorph 由 Metamorph 自身管理，不能重复执行。
    }

    @Override
    public aaaa0ggmc.morphext.ability.ExecutionSide getExecutionSide() {
        return aaaa0ggmc.morphext.ability.ExecutionSide.SERVER;
    }

    @Override
    public void onActivate(EntityPlayer player) {
        if (this.action == null || player == null) {
            return;
        }
        if (player.world.isRemote) {
            // 兼容尚未迁移的直接调用者，仍必须走统一请求入口。
            aaaa0ggmc.morphext.client.ClientAbilityExecutor.activate(this);
        } else {
            throw new IllegalStateException("Resolve server actions through MetamorphServerAbilities");
        }
    }
}

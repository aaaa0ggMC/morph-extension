package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.abilities.IAbility;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * metamorph 原版被动能力（Jumping / StepUp / Glide）的 morphext 适配器。
 * <p>
 * 这三个 id 只注册在 metamorph 的 {@code MorphManager.INSTANCE.abilities}，
 * UAPA 装配路径（{@code MorphAbilityManager.resolveAbilitiesForMorph} → {@code AbilityRegistry.get}）
 * 取不到而被静默丢弃（inventory §5 死引用）。本适配器<strong>只补 morphext 侧的解析缺口</strong>：
 * <ul>
 *   <li>不改动、不覆盖 metamorph 注册表里的原版能力实例与行为；</li>
 *   <li>当前 morph 的 morphs.json 已授予同名原生能力时（metamorph 自身会驱动其
 *       {@code update/onMorph/onDemorph}，如 slime/guardian 的 jumping），适配器不重复执行，
 *       避免双重叠加（Jumping 双倍跳跃冲量、Glide 双重缓降）；</li>
 *   <li>仅 {@code morph_abilities.json} 引用时，把生命周期原样委托给 metamorph 原版实现。</li>
 * </ul>
 */
public class MetamorphAbilityAdapter extends AbstractPassiveAbility {
    private final IAbility delegate;

    public MetamorphAbilityAdapter(String id, IAbility delegate, String nameKey, String descKey, ItemStack icon) {
        super(id, nameKey, descKey, icon);
        this.delegate = delegate;
    }

    /** 当前 morph 的 metamorph 原生能力列表里已有同类能力（morphs.json 授予，metamorph 自己维护）。 */
    private boolean metamorphOwns(MorphContext context) {
        if (context == null) {
            return false;
        }
        AbstractMorph morph = context.getMorph();
        if (morph == null) {
            return false;
        }
        MorphSettings settings = morph.getSettings();
        if (settings == null || settings.abilities == null) {
            return false;
        }
        for (IAbility ability : settings.abilities) {
            if (ability != null && ability.getClass() == this.delegate.getClass()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onApplied(EntityPlayer player, MorphContext context) {
        if (player == null || metamorphOwns(context)) {
            return;
        }
        this.delegate.onMorph(player);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || metamorphOwns(context)) {
            return;
        }
        this.delegate.update(player);
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (player == null) {
            return;
        }
        // onDemorph 归位是幂等的（如 StepUp 重置 stepHeight），metamorph 自身通道重复执行无害
        this.delegate.onDemorph(player);
    }
}

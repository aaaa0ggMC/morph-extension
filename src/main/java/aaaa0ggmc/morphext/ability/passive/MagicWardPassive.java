package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * 奥术屏障被动 (Magic Ward Passive)。
 * <p>
 * 灾厄施法者（唤魔者/幻术师）的法术素养：受到魔法与即死效果伤害时减免三成，
 * 是女巫魔抗（85%）的弱化法师版，强度克制。
 */
public class MagicWardPassive extends AbstractPassiveAbility {
    private static final float MULTIPLIER = 0.7F;

    public MagicWardPassive(String id, String nameKey, String descKey, ItemStack icon) {
        super(id, nameKey, descKey, icon);
    }

    public MagicWardPassive(String id) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc", ItemStack.EMPTY);
    }

    @Override
    public float onHurt(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source != null && source.isMagicDamage()) {
            return amount * MULTIPLIER;
        }
        return amount;
    }
}

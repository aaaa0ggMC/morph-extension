package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * 低血应激被动 (Low-Health Effect Passive)。
 * <p>
 * 生命值低于阈值时获得持续应激增益，回血后自动解除（不叠加、不残留）：
 * 狼群狂怒（低血力量 I，对应 EntityWolf 愤怒机制）、村民怯懦疾走（低血速度 I）等。
 */
public class LowHealthEffectPassive extends AbstractPassiveAbility {
    private final Potion potion;
    private final int amplifier;
    private final float threshold;

    public LowHealthEffectPassive(String id, String nameKey, String descKey, ItemStack icon,
                                  Potion potion, int amplifier, float threshold) {
        super(id, nameKey, descKey, icon);
        this.potion = potion;
        this.amplifier = amplifier;
        this.threshold = threshold;
    }

    public LowHealthEffectPassive(String id, Potion potion, int amplifier, float threshold) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc",
                ItemStack.EMPTY, potion, amplifier, threshold);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead || potion == null) {
            return;
        }
        if (player.ticksExisted % 10 != 0) {
            return;
        }
        if (player.getHealth() <= player.getMaxHealth() * threshold) {
            player.addPotionEffect(new PotionEffect(potion, 100, amplifier, false, true));
        }
        // 回血到阈值以上时不强制清除：短时效自然过期，避免误删玩家自身药水效果
    }
}

package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 坠落伤害豁免与缓降滑翔被动 (Prevent Fall Passive)。
 * 适用于鸡、蝙蝠、豹猫、铁傀儡、雪傀儡、反物质鸡等生物。
 */
public class PreventFallPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:prevent_fall";
    private final boolean slowFall;

    public PreventFallPassive(String id, boolean slowFall) {
        super(id);
        this.slowFall = slowFall;
    }

    public PreventFallPassive() {
        this(ID, true);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        // 重置坠落距离，彻底根除摔伤
        player.fallDistance = 0.0F;

        // 如果具备鸡/蝙蝠特性的滑翔缓降：
        if (slowFall && !player.onGround && player.motionY < -0.08D && !player.isSneaking()) {
            player.motionY *= 0.65D;
        }
    }

    @Override
    public boolean onAttacked(EntityPlayer player, net.minecraft.util.DamageSource source, float amount, MorphContext context) {
        return source == net.minecraft.util.DamageSource.FALL;
    }
}

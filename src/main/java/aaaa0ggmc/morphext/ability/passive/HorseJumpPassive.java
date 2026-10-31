package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/**
 * 马属强跃被动 (Horse Leap Passive)。
 * <p>
 * 依据 {@code AbstractHorse} 注册的 JUMP_STRENGTH（0.4–1.0）——跳跃是马科生物的核心属性：
 * 1. 【常驻强跃】持续保持跳跃提升 II（在分析建议的 I~II 区间内取高段，突出"强跃"）；
 * 2. 【落地缓冲】坠落伤害减半，还原马匹高跃落地的稳健姿态（"落地减小硬直"）。
 */
public class HorseJumpPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:horse_jump";

    public HorseJumpPassive(String id) {
        super(id, "morphext.passive.morphext.horse_jump.name", "morphext.passive.morphext.horse_jump.desc",
                new ItemStack(Items.SADDLE));
    }

    public HorseJumpPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }
        // 常驻跳跃提升 II（amplifier 1），短时长高频续期，换形态/离场即自然消失
        player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 100, 1, false, true));
    }

    @Override
    public float onHurt(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == DamageSource.FALL) {
            return amount * 0.5F;
        }
        return amount;
    }
}

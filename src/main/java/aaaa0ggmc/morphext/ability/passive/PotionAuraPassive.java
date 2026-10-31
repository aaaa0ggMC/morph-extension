package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;

/**
 * 光环施效被动 (Potion Aura Passive)。
 * <p>
 * 周期性为周围敌人施加一小段负面状态（半径小、强度低，只做轻度干扰）：
 * 远古诅咒光环（挖掘疲劳）、凋零气场（凋零 I）、饥渴光环（饥饿 I）等。
 */
public class PotionAuraPassive extends AbstractPassiveAbility {
    private final Potion potion;
    private final int amplifier;
    private final double radius;
    private final int intervalTicks;
    private final int durationTicks;

    public PotionAuraPassive(String id, String nameKey, String descKey, ItemStack icon,
                             Potion potion, int amplifier, double radius, int intervalTicks, int durationTicks) {
        super(id, nameKey, descKey, icon);
        this.potion = potion;
        this.amplifier = amplifier;
        this.radius = radius;
        this.intervalTicks = Math.max(1, intervalTicks);
        this.durationTicks = durationTicks;
    }

    public PotionAuraPassive(String id, Potion potion, int amplifier, double radius, int intervalTicks, int durationTicks) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc",
                ItemStack.EMPTY, potion, amplifier, radius, intervalTicks, durationTicks);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead || potion == null) {
            return;
        }
        if (player.ticksExisted % intervalTicks != 0) {
            return;
        }
        AxisAlignedBB box = player.getEntityBoundingBox().grow(radius, radius * 0.5D, radius);
        List<EntityLivingBase> nearby = player.world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != player && e.isEntityAlive() && !e.isOnSameTeam(player));
        for (EntityLivingBase target : nearby) {
            // 预检目标免疫：被 DENY 的目标直接跳过（镜像局光环对冲互废时不再空转加-撕）
            PotionEffect effect = new PotionEffect(potion, durationTicks, amplifier, false, true);
            if (target.isPotionApplicable(effect)) {
                target.addPotionEffect(effect);
            }
        }
    }
}

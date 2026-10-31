package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 不死/亡灵体质被动 (Undead Nature Passive)。
 * 赋予僵尸、骷髅等亡灵生物免疫饥饿消耗 (Hungerless) 的原生生物特征。
 */
public class UndeadPassive extends AbstractPassiveAbility {
    public UndeadPassive(String id) {
        super(id, "morphext.passive.undead.name", "morphext.passive.undead.desc", ItemStack.EMPTY);
    }

    public UndeadPassive() {
        this("hungerless");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (!player.world.isRemote && player.ticksExisted % 40 == 0) {
            // 锁定饱食度不随时间流逝而衰减
            if (player.getFoodStats().getFoodLevel() < 20) {
                player.getFoodStats().setFoodLevel(20);
            }
        }
    }
}

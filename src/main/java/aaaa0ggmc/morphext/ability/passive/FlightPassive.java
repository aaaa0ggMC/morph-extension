package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 原生飞行被动 (Flight Passive)。
 * 适用于末影龙、凋灵、烈焰人、蝙蝠、恶魂等飞行生物。
 */
public class FlightPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:fly";

    public FlightPassive(String id) {
        super(id);
    }

    public FlightPassive() {
        this(ID);
    }

    @Override
    public void onApplied(EntityPlayer player, MorphContext context) {
        player.capabilities.allowFlying = true;
        player.sendPlayerAbilities();
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (!player.isCreative() && !player.isSpectator()) {
            player.capabilities.allowFlying = false;
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
        }
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (!player.capabilities.allowFlying) {
            player.capabilities.allowFlying = true;
            player.sendPlayerAbilities();
        }
    }
}

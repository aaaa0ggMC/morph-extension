package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 声明式伤害免疫被动 (Declarative Damage Immunity Passive)。
 * 支持同时免疫多种指定伤害类型（如 fall, drown, inFire, onFire, lava, cactus, acid, antimatter, dread 等）。
 */
public class DamageImmunityPassive extends AbstractPassiveAbility {
    private final Set<String> immuneTypes = new HashSet<>();
    private final boolean playDeflectSound;

    public DamageImmunityPassive(String id, String nameKey, String descKey, ItemStack icon, boolean playDeflectSound, String... types) {
        super(id, nameKey, descKey, icon);
        this.playDeflectSound = playDeflectSound;
        if (types != null) {
            for (String t : types) {
                if (t != null && !t.trim().isEmpty()) {
                    immuneTypes.add(t.trim().toLowerCase());
                }
            }
        }
    }

    public DamageImmunityPassive(String id, boolean playDeflectSound, String... types) {
        this(id, "morphext.passive." + id + ".name", "morphext.passive." + id + ".desc", ItemStack.EMPTY, playDeflectSound, types);
    }

    public Set<String> getImmuneTypes() {
        return Collections.unmodifiableSet(immuneTypes);
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null || source.damageType == null) {
            return false;
        }
        String type = source.damageType.toLowerCase();
        if (isImmune(type)) {
            // 火焰相关伤害同时扑灭身体燃烧状态
            if (type.contains("fire") || type.contains("lava") || type.contains("flame")) {
                player.extinguish();
            }
            if (playDeflectSound && player.world.isRemote && player.ticksExisted % 10 == 0) {
                player.world.playSound(player.posX, player.posY, player.posZ,
                        SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.6F, 1.2F, false);
            }
            return true;
        }
        return false;
    }

    private boolean isImmune(String type) {
        if (immuneTypes.contains(type)) {
            return true;
        }
        // 通用别名模糊容错
        for (String target : immuneTypes) {
            if (type.contains(target) || target.contains(type)) {
                return true;
            }
        }
        return false;
    }
}

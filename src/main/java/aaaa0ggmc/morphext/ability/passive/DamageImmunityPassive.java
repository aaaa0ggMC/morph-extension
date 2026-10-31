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
        // lang 键统一用点号形态（morphext.passive.morphext.xxx.name），冒号 id 归一化
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc", ItemStack.EMPTY, playDeflectSound, types);
    }

    public Set<String> getImmuneTypes() {
        return Collections.unmodifiableSet(immuneTypes);
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null || source.damageType == null) {
            return false;
        }
        if (isImmune(source)) {
            // 火焰相关伤害同时扑灭身体燃烧状态
            if (source.isFireDamage() || immuneTypes.contains("infire") || immuneTypes.contains("lava")) {
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

    private boolean isImmune(DamageSource source) {
        String type = source.damageType != null ? source.damageType.toLowerCase().trim() : "";
        if (immuneTypes.contains(type)) {
            return true;
        }
        // 语义化标准防御判定（杜绝粗糙的字符串子串模糊匹配）
        if (source.isFireDamage() && (immuneTypes.contains("fire") || immuneTypes.contains("infire")
                || immuneTypes.contains("onfire") || immuneTypes.contains("lava") || immuneTypes.contains("hotfloor"))) {
            return true;
        }
        if (source.isExplosion() && (immuneTypes.contains("explosion") || immuneTypes.contains("explosion.player"))) {
            return true;
        }
        if (source.isMagicDamage() && immuneTypes.contains("magic")) {
            return true;
        }
        if (source.isProjectile() && immuneTypes.contains("projectile")) {
            return true;
        }
        if (source == DamageSource.FALL && immuneTypes.contains("fall")) {
            return true;
        }
        if (source == DamageSource.DROWN && immuneTypes.contains("drown")) {
            return true;
        }
        if (source == DamageSource.CACTUS && immuneTypes.contains("cactus")) {
            return true;
        }
        if (source == DamageSource.WITHER && immuneTypes.contains("wither")) {
            return true;
        }
        if (source == DamageSource.IN_WALL && immuneTypes.contains("inwall")) {
            return true;
        }
        return false;
    }
}

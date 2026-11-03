package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

import java.util.*;

/**
 * 声明式药水/负面效果免疫被动 (Declarative Potion Immunity Passive)。
 * 不仅在附加时拦截，还在 onUpdate 中每 10 ticks 自动清除残留的对应药水效果。
 */
public class PotionImmunityPassive extends AbstractPassiveAbility {
    private final Set<String> immunePotionNames = new HashSet<>();

    public PotionImmunityPassive(String id, String nameKey, String descKey, ItemStack icon, String... potionNames) {
        super(id, nameKey, descKey, icon);
        if (potionNames != null) {
            for (String p : potionNames) {
                if (p != null && !p.trim().isEmpty()) {
                    // Locale.ROOT：与 MorphAbilityManager.normalizeNames 同规格，避免默认 locale 小写漂移
                    immunePotionNames.add(p.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    public PotionImmunityPassive(String id, String... potionNames) {
        // lang 键统一用点号形态（morphext.passive.morphext.xxx.name），冒号 id 归一化
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc", ItemStack.EMPTY, potionNames);
    }

    public Set<String> getImmunePotionNames() {
        return Collections.unmodifiableSet(immunePotionNames);
    }

    @Override
    public boolean onPotionApplicable(EntityPlayer player, PotionEffect effect, MorphContext context) {
        if (effect == null || effect.getPotion() == null) {
            return false;
        }
        return matches(effect.getPotion());
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        // 每 10 tick 检查并清除残留的不良效果
        if (player.ticksExisted % 10 == 0) {
            Collection<PotionEffect> active = new ArrayList<>(player.getActivePotionEffects());
            for (PotionEffect effect : active) {
                if (matches(effect.getPotion())) {
                    player.removePotionEffect(effect.getPotion());
                }
            }
        }
    }

    private boolean matches(Potion potion) {
        ResourceLocation rl = potion.getRegistryName();
        String regName = rl != null ? rl.toString().toLowerCase(java.util.Locale.ROOT) : "";
        String path = rl != null ? rl.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        String unloc = potion.getName().toLowerCase(java.util.Locale.ROOT);
        String unlocRaw = unloc.startsWith("potion.") ? unloc.substring(7) : unloc;

        for (String imm : immunePotionNames) {
            if (imm.equals(regName) || imm.equals(path) || imm.equals(unloc) || imm.equals(unlocRaw)) {
                return true;
            }
        }
        return false;
    }
}

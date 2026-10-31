package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 水生陆地应激被动 (Aquatic Land Stress Passive)。
 * <p>
 * 还原 {@code EntityWaterMob} 系水生生物离水后的挣扎弱点（1.12.2 鱿鱼离水会按空气条机制掉血溺毙）：
 * 1. 【陆地应激】离开水体后获得缓慢 I 与虚弱 I，行动艰难、四肢无力；
 * 2. 【入水解除】重新入水后立即解除应激状态，恢复灵活游动。
 */
public class LandStressPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:land_stress";

    /** 各玩家当前是否处于本被动施加的陆地应激状态（入水时只解除自己施加的效果）。 */
    private static final Map<UUID, Boolean> APPLIED = new ConcurrentHashMap<>();

    public LandStressPassive(String id) {
        super(id, "morphext.passive.morphext.land_stress.name", "morphext.passive.morphext.land_stress.desc",
                new ItemStack(Items.WATER_BUCKET));
    }

    public LandStressPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }
        UUID uuid = player.getUniqueID();
        if (player.isInWater()) {
            // 入水解除应激
            if (Boolean.TRUE.equals(APPLIED.get(uuid))) {
                player.removePotionEffect(MobEffects.SLOWNESS);
                player.removePotionEffect(MobEffects.WEAKNESS);
                APPLIED.put(uuid, Boolean.FALSE);
            }
            return;
        }
        // 陆地应激：缓慢 I + 虚弱 I（短时长续期，入水/离场自然消失）
        player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, true));
        APPLIED.put(uuid, Boolean.TRUE);
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (player != null) {
            APPLIED.remove(player.getUniqueID());
        }
    }
}

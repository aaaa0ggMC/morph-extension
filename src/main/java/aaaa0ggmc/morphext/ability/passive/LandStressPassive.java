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
 * <p>
 * 施效幂等（与 ac_photophobia 同模式）：已存在同类效果时不重复 addPotionEffect，
 * 且只清自己施加的 0 级效果；效果命中自身免疫表时直接跳过（自免疫预检），
 * 保证“自施效源 + 免疫剥离器”共存时不会形成每 tick 加-撕的属性泵动。
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
            // 入水解除应激：只清自己施加的 0 级缓慢/虚弱（保留其他来源的高级同类效果）
            if (Boolean.TRUE.equals(APPLIED.get(uuid))) {
                clearLandStress(player);
                APPLIED.put(uuid, Boolean.FALSE);
            }
            return;
        }
        // 自免疫预检 + 幂等施加：效果命中自身免疫表时直接跳过（避免与免疫剥离器形成加-撕泵动）；
        // 已存在同类效果时不重复 addPotionEffect（与其他清残留机制互不“加-撕”）。
        if (!player.isPotionActive(MobEffects.SLOWNESS)) {
            PotionEffect slowness = new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true);
            if (player.isPotionApplicable(slowness)) {
                player.addPotionEffect(slowness);
            }
        }
        if (!player.isPotionActive(MobEffects.WEAKNESS)) {
            PotionEffect weakness = new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, true);
            if (player.isPotionApplicable(weakness)) {
                player.addPotionEffect(weakness);
            }
        }
        APPLIED.put(uuid, Boolean.TRUE);
    }

    /** 清除本被动自加的 0 级虚弱/缓慢（入水解除或解除变身时调用）。 */
    private static void clearLandStress(EntityPlayer player) {
        PotionEffect weakness = player.getActivePotionEffect(MobEffects.WEAKNESS);
        if (weakness != null && weakness.getAmplifier() == 0) {
            player.removePotionEffect(MobEffects.WEAKNESS);
        }
        PotionEffect slowness = player.getActivePotionEffect(MobEffects.SLOWNESS);
        if (slowness != null && slowness.getAmplifier() == 0) {
            player.removePotionEffect(MobEffects.SLOWNESS);
        }
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (player != null) {
            // 解除变身时清掉自加的陆地应激残留
            if (Boolean.TRUE.equals(APPLIED.get(player.getUniqueID()))) {
                clearLandStress(player);
            }
            APPLIED.remove(player.getUniqueID());
        }
    }
}

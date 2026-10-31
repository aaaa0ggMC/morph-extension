package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蜘蛛日光中立被动 (Spider Sunlight Neutrality Passive)。
 * <p>
 * 严谨对应 {@code EntitySpider} 的日光中立性格（AISpiderTarget：亮度 ≥ 0.5 不索敌；
 * AISpiderAttack：高亮下概率脱战）：
 * 1. 【日光中立】处于明亮光照（亮度 ≥ 0.5）时，周围敌对生物不再主动锁定该形态为目标；
 * 2. 【惹怒反击仍生效】被该玩家主动攻击过的生物保留仇恨（对应原版 EntityAIHurtByTarget 逻辑），
 *    不因日光豁免而变成打不还手。
 */
public class SpiderSunPassive extends AbstractPassiveAbility {
    public static final String ID = "spider_sun_passive";

    /** 玩家主动攻击过的生物（entityId → 上次攻击的 world 时间），10 秒内保留其仇恨。 */
    private static final Map<UUID, Map<Integer, Long>> PROVOKED = new ConcurrentHashMap<>();
    private static final long PROVOKE_WINDOW = 200L;

    public SpiderSunPassive(String id) {
        super(id, "morphext.passive.spider_sun_passive.name", "morphext.passive.spider_sun_passive.desc",
                new ItemStack(Items.SPIDER_EYE));
    }

    public SpiderSunPassive() {
        this(ID);
    }

    @Override
    public void onAttack(EntityPlayer player, EntityLivingBase target, DamageSource source, float amount, MorphContext context) {
        if (player == null || target == null) {
            return;
        }
        PROVOKED.computeIfAbsent(player.getUniqueID(), k -> new ConcurrentHashMap<>())
                .put(target.getEntityId(), player.world.getTotalWorldTime());
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }
        // 对应 EntitySpider.AISpiderTarget：亮度 >= 0.5 时不再索敌
        if (player.getBrightness() < 0.5F) {
            return;
        }
        if (player.ticksExisted % 10 != 0) {
            return;
        }

        long now = player.world.getTotalWorldTime();
        Map<Integer, Long> provoked = PROVOKED.get(player.getUniqueID());
        AxisAlignedBB box = player.getEntityBoundingBox().grow(16.0D, 8.0D, 16.0D);
        List<EntityLivingBase> nearby = player.world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != player && e.isEntityAlive());

        for (EntityLivingBase mob : nearby) {
            boolean recentlyProvoked = provoked != null
                    && provoked.getOrDefault(mob.getEntityId(), Long.MIN_VALUE) + PROVOKE_WINDOW > now;
            if (recentlyProvoked) {
                continue;
            }
            if (mob.getRevengeTarget() == player) {
                mob.setRevengeTarget(null);
            }
            if (mob instanceof net.minecraft.entity.EntityLiving
                    && ((net.minecraft.entity.EntityLiving) mob).getAttackTarget() == player) {
                ((net.minecraft.entity.EntityLiving) mob).setAttackTarget(null);
            }
        }
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (player != null) {
            PROVOKED.remove(player.getUniqueID());
        }
    }
}

package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 末影螨天敌仇恨吸引被动 (Endermite Bait Passive)。
 * <p>
 * 深度还原原版 {@link net.minecraft.entity.monster.EntityEnderman} 与 {@link net.minecraft.entity.monster.EntityEndermite}
 * 之间根深蒂固的天敌仇恨机制：
 * 1. 【末影天敌吸引】：末影螨微小躯体不断散发高频次空间振荡与异界信息素；
 * 2. 【强力聚怪嘲讽】：每秒自动搜寻周围 24 格内的所有末影人，令其瞬间被激怒并将仇恨锁定至自身；
 * 3. 【战术功能】：在多人配合中充当拉怪聚怪 T 位，或用于末地刷怪场与探险中的怪群牵引。
 */
public class EndermiteBaitPassive extends AbstractPassiveAbility {
    public static final String ID = "endermite_bait";

    public EndermiteBaitPassive(String id) {
        super(id, "morphext.passive.endermite_bait.name", "morphext.passive.endermite_bait.desc", ItemStack.EMPTY);
    }

    public EndermiteBaitPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead || player.capabilities.isCreativeMode) {
            return;
        }

        // 空间振动微粒
        if (player.ticksExisted % 12 == 0 && player.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) player.world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, player.posX, player.posY + 0.2D, player.posZ,
                    4, 0.25D, 0.1D, 0.25D, 0.05D);
        }

        // 每秒 (20 ticks) 扫描吸引周围末影人仇恨
        if (player.ticksExisted % 20 == 0) {
            double range = 24.0D;
            AxisAlignedBB box = player.getEntityBoundingBox().grow(range, 8.0D, range);
            List<EntityEnderman> endermen = player.world.getEntitiesWithinAABB(EntityEnderman.class, box,
                    e -> e != null && e.isEntityAlive() && !e.isOnSameTeam(player));

            boolean attracted = false;
            for (EntityEnderman enderman : endermen) {
                if (enderman.getAttackTarget() != player) {
                    enderman.setAttackTarget(player);
                    attracted = true;
                }
            }

            if (attracted && player.ticksExisted % 60 == 0) {
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_ENDERMITE_AMBIENT, SoundCategory.HOSTILE, 0.6F, 1.4F);
            }
        }
    }
}

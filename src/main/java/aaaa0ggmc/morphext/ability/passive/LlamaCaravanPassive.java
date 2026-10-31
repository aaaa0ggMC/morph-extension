package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.UUID;

/**
 * 羊驼商队领头被动 (Llama Caravan Passive)。
 * 源码级还原原版 EntityAILlamaFollowCaravan 商队行进机制：
 * 变身羊驼的玩家作为头驼引领周围羊驼行进，周围 16 格内的野生或驯服羊驼自动紧随队伍，
 * 并为头驼及随行羊驼赋予 15% 行进移速提升。
 */
public class LlamaCaravanPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:llama_caravan";

    private static final UUID CARAVAN_LEADER_SPEED_UUID = UUID.fromString("4d5e6f7a-8b9c-0d1e-2f3a-4b5c6d7e8f90");
    private static final AttributeModifier LEADER_SPEED_MODIFIER =
            new AttributeModifier(CARAVAN_LEADER_SPEED_UUID, "Llama Caravan Leader Speed", 0.15D, 2); // +15% 移速

    public LlamaCaravanPassive(String id) {
        super(id, "morphext.passive.llama_caravan.name", "morphext.passive.llama_caravan.desc", ItemStack.EMPTY);
    }

    public LlamaCaravanPassive() {
        this("llama_caravan");
    }

    @Override
    public void onApplied(EntityPlayer player, MorphContext context) {
        if (player == null) return;
        IAttributeInstance speedAttr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            if (speedAttr.getModifier(CARAVAN_LEADER_SPEED_UUID) != null) {
                speedAttr.removeModifier(CARAVAN_LEADER_SPEED_UUID);
            }
            speedAttr.applyModifier(LEADER_SPEED_MODIFIER);
        }
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        if (player == null) return;
        IAttributeInstance speedAttr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(CARAVAN_LEADER_SPEED_UUID);
        }
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.ticksExisted % 10 != 0) {
            return;
        }

        List<EntityLlama> llamas = player.world.getEntitiesWithinAABB(EntityLlama.class,
                player.getEntityBoundingBox().grow(16.0D, 8.0D, 16.0D));

        for (EntityLlama llama : llamas) {
            if (llama == null || !llama.isEntityAlive() || llama.getLeashed()) {
                continue;
            }

            double distSq = llama.getDistanceSq(player);
            // 距离大于 3 格且在 16 格范围内时自动组队跟随
            if (distSq > 9.0D && distSq < 256.0D) {
                double dist = Math.sqrt(distSq);
                Vec3d dir = new Vec3d(player.posX - llama.posX, player.posY - llama.posY, player.posZ - llama.posZ).normalize();
                Vec3d target = dir.scale(Math.max(dist - 2.5D, 0.0D));

                llama.getNavigator().tryMoveToXYZ(llama.posX + target.x, llama.posY + target.y, llama.posZ + target.z, 1.25D);
                // 提升 15% 行进移速 (速度 I 缓冲)
                llama.addPotionEffect(new PotionEffect(MobEffects.SPEED, 40, 0, false, false));
                llama.getLookHelper().setLookPositionWithEntity(player, 10.0F, (float) llama.getVerticalFaceSpeed());

                // 队伍温馨微粒
                if (player.ticksExisted % 40 == 0 && player.world instanceof WorldServer) {
                    ((WorldServer) player.world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                            llama.posX, llama.posY + llama.height + 0.25D, llama.posZ,
                            2, 0.2D, 0.15D, 0.2D, 0.0D);
                }
            }
        }
    }
}

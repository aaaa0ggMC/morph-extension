package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/**
 * 铁傀儡百炼钢铁与巨力挑飞被动 (Iron Golem Titanic Strength & Knockback Resistance Passive)。
 * 深度还原原版 EntityIronGolem 底层数值与战斗机制：
 * 1. 100% 击退抗性：常驻抗击退 1.0D (KNOCKBACK_RESISTANCE)；
 * 2. 坠落免疫、仙人掌免疫与溺水免疫 (decreaseAirSupply return air)；
 * 3. 巨力挑飞空掷：原版 EntityIronGolem.attackEntityAsMob 机制，普通攻击将敌人挑飞至半空 (motionY += 0.4000000059604645D)，
 *    伴随原版铁傀儡挥臂暴击音效与碎屑粒子。
 */
public class IronGolemPassive extends AbstractPassiveAbility {
    public static final String ID = "iron_golem_nature";
    private static final UUID KNOCKBACK_RES_UUID = UUID.fromString("1f2e3d4c-5b6a-7f8e-9d0c-1b2a3c4d5e6f");
    private static final AttributeModifier KNOCKBACK_MODIFIER =
            new AttributeModifier(KNOCKBACK_RES_UUID, "Iron Golem Knockback Resistance", 1.0D, 0).setSaved(false);

    public IronGolemPassive(String id) {
        super(id, "morphext.passive.iron_golem_nature.name", "morphext.passive.iron_golem_nature.desc", ItemStack.EMPTY);
    }

    public IronGolemPassive() {
        this(ID);
    }

    @Override
    public void onApplied(EntityPlayer player, MorphContext context) {
        IAttributeInstance kb = player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
        if (kb != null && !kb.hasModifier(KNOCKBACK_MODIFIER)) {
            kb.applyModifier(KNOCKBACK_MODIFIER);
        }
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        IAttributeInstance kb = player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
        if (kb != null && kb.hasModifier(KNOCKBACK_MODIFIER)) {
            kb.removeModifier(KNOCKBACK_MODIFIER);
        }
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == DamageSource.FALL || source == DamageSource.CACTUS || source == DamageSource.DROWN) {
            return true;
        }
        return false;
    }

    @Override
    public void onAttack(EntityPlayer player, EntityLivingBase target, DamageSource source, float amount, MorphContext context) {
        if (target != null && target.isEntityAlive()) {
            // 原版 EntityIronGolem.attackEntityAsMob 挑飞高度
            target.motionY += 0.4000000059604645D;
            target.velocityChanged = true;

            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.4F, 1.0F);

            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.CRIT,
                        target.posX, target.posY + target.height * 0.5D, target.posZ,
                        10, 0.3D, 0.3D, 0.3D, 0.1D);
            }
        }
    }
}

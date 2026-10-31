package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

/**
 * 海晶尖刺反伤被动 (Thorns / Spikes Passive)。
 * 深度还原原版 EntityGuardian.attackEntityFrom 尖刺防御机制：
 * 受到直接近战伤害时（非魔法伤害且非爆炸），自动向直接攻击者反射海晶尖刺反伤，
 * 伴随荆棘刺裂音效、守卫者受击音效与海晶魔法爆裂粒子。
 */
public class ThornsPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:thorns";

    public ThornsPassive(String id) {
        super(id, "morphext.passive.thorns.name", "morphext.passive.thorns.desc", ItemStack.EMPTY);
    }

    public ThornsPassive() {
        this(ID);
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null || source.isMagicDamage() || source.isExplosion()) {
            return false;
        }

        Entity attacker = source.getImmediateSource();
        if (attacker instanceof EntityLivingBase && attacker != player) {
            EntityLivingBase livingAttacker = (EntityLivingBase) attacker;
            if (player.getDistanceSq(livingAttacker) <= 25.0D) {
                // 原版守卫者尖刺反射真实伤害：基础 2.0F + 依据受击伤害浮动
                float reflectAmount = Math.max(2.0F, amount * 0.4F);
                DamageSource thornsDamage = DamageSource.causeThornsDamage(player);
                livingAttacker.attackEntityFrom(thornsDamage, reflectAmount);

                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENCHANT_THORNS_HIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_GUARDIAN_HURT, SoundCategory.PLAYERS, 0.8F, 1.2F);

                if (player.world instanceof WorldServer) {
                    WorldServer ws = (WorldServer) player.world;
                    ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                            livingAttacker.posX, livingAttacker.posY + livingAttacker.height * 0.5D, livingAttacker.posZ,
                            12, 0.3D, 0.3D, 0.3D, 0.1D);
                    ws.spawnParticle(EnumParticleTypes.WATER_SPLASH,
                            player.posX, player.posY + player.height * 0.5D, player.posZ,
                            10, 0.4D, 0.4D, 0.4D, 0.15D);
                }
            }
        }
        return false;
    }
}

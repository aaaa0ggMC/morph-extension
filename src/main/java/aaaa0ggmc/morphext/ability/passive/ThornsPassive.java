package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;

/**
 * 海晶尖刺反伤被动 (Thorns Passive)。
 * 守卫者/远古守卫者专属：受到近战攻击时向直接攻击者反射 2-4 点真实伤害。
 */
public class ThornsPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:thorns";

    public ThornsPassive() {
        super(ID);
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null) return false;
        Entity attacker = source.getTrueSource();
        if (attacker instanceof EntityLivingBase && attacker != player) {
            EntityLivingBase livingAttacker = (EntityLivingBase) attacker;
            if (player.getDistanceSq(livingAttacker) <= 16.0D) {
                // 近战反弹
                DamageSource thornsDamage = DamageSource.causeThornsDamage(player);
                float reflectAmount = Math.max(2.0F, amount * 0.35F);
                livingAttacker.attackEntityFrom(thornsDamage, reflectAmount);

                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENCHANT_THORNS_HIT, SoundCategory.PLAYERS, 0.8F, 1.0F);

                if (player.world.isRemote) {
                    for (int i = 0; i < 8; i++) {
                        player.world.spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                                livingAttacker.posX + (player.world.rand.nextDouble() - 0.5D) * livingAttacker.width,
                                livingAttacker.posY + player.world.rand.nextDouble() * livingAttacker.height,
                                livingAttacker.posZ + (player.world.rand.nextDouble() - 0.5D) * livingAttacker.width,
                                0, 0, 0);
                    }
                }
            }
        }
        return false;
    }
}

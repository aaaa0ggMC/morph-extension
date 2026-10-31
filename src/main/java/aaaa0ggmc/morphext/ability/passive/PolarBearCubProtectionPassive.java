package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 幼崽守护狂怒被动 (Polar Bear Cub Protection Passive)。
 * 检测周围 16 格内是否存在幼年北极熊或变身为小北极熊的友方玩家；
 * 若存在幼崽，激发生物母性狂怒守护本能，攻击力提升 100%（力量 II）并获得持续生命恢复与抗性提升。
 */
public class PolarBearCubProtectionPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:polar_bear_cub_protection";

    public PolarBearCubProtectionPassive(String id) {
        super(id, "morphext.passive.polar_bear_cub_protection.name",
                "morphext.passive.polar_bear_cub_protection.desc", ItemStack.EMPTY);
    }

    public PolarBearCubProtectionPassive() {
        this("polar_bear_cub_protection");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.ticksExisted % 10 != 0) {
            return;
        }

        List<EntityLivingBase> list = player.world.getEntitiesWithinAABB(EntityLivingBase.class,
                player.getEntityBoundingBox().grow(16.0D),
                e -> e != null && e != player && e.isEntityAlive());

        boolean hasCub = false;
        for (EntityLivingBase entity : list) {
            // 1. 原生实体幼年北极熊判定
            if (entity instanceof EntityPolarBear) {
                if (((EntityPolarBear) entity).isChild()) {
                    hasCub = true;
                    break;
                }
            } else if (entity instanceof EntityPlayer) {
                // 2. 变身为幼年北极熊的玩家判定
                EntityPlayer other = (EntityPlayer) entity;
                if (player.isOnSameTeam(other) || !other.isSpectator()) {
                    IMorphing morphing = Morphing.get(other);
                    if (morphing != null && morphing.isMorphed()) {
                        AbstractMorph morph = morphing.getCurrentMorph();
                        if (morph instanceof EntityMorph) {
                            try {
                                EntityLivingBase dummy = ((EntityMorph) morph).getEntity(other.world);
                                if (dummy instanceof EntityPolarBear && ((EntityPolarBear) dummy).isChild()) {
                                    hasCub = true;
                                    break;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        }

        if (hasCub) {
            // 激发母性狂怒：力量 II（攻击力提升 100%）、生命恢复 I 与抗性提升 I
            player.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 40, 1, false, true));
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 40, 0, false, true));
            player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 40, 0, false, true));

            if (player.ticksExisted % 20 == 0 && player.world instanceof WorldServer) {
                WorldServer ws = (WorldServer) player.world;
                ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                        player.posX, player.posY + player.height * 0.9D, player.posZ,
                        2, 0.3D, 0.2D, 0.3D, 0.0D);
                ws.spawnParticle(EnumParticleTypes.CRIT,
                        player.posX, player.posY + player.height * 0.6D, player.posZ,
                        4, 0.35D, 0.25D, 0.35D, 0.1D);
            }
        }
    }
}

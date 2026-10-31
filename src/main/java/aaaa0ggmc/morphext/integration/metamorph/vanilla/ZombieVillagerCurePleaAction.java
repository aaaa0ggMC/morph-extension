package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 僵尸村民治愈祈愿 (Zombie Villager Cure Plea Action):
 * 还原原版虚弱状态喂食金苹果净化转化为正常村民的机制。
 * 手持金苹果时消耗 1 枚金苹果，驱散虚弱与诅咒，爆发圣洁金光并获得 30 秒生命恢复 II、抗性提升 II 与力量 I！
 */
public class ZombieVillagerCurePleaAction implements IAction {

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        if (!(entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;

        ItemStack mainHand = player.getHeldItem(EnumHand.MAIN_HAND);
        ItemStack offHand = player.getHeldItem(EnumHand.OFF_HAND);
        boolean hasGoldenApple = false;
        boolean isOffHand = false;

        if (mainHand.getItem() == Items.GOLDEN_APPLE) {
            hasGoldenApple = true;
        } else if (offHand.getItem() == Items.GOLDEN_APPLE) {
            hasGoldenApple = true;
            isOffHand = true;
        }

        if (hasGoldenApple) {
            if (!player.isCreative()) {
                if (isOffHand) offHand.shrink(1);
                else mainHand.shrink(1);
            }

            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.PLAYERS, 1.5F, 1.0F);
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8F, 1.2F);

            if (!world.isRemote) {
                player.removePotionEffect(MobEffects.WEAKNESS);
                player.removePotionEffect(MobEffects.HUNGER);
                player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 600, 1));
                player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 600, 1));
                player.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 600, 0));

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.TOTEM,
                            player.posX, player.posY + 1.0D, player.posZ, 40, 0.6D, 0.8D, 0.6D, 0.1D);
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                            player.posX, player.posY + 1.0D, player.posZ, 20, 0.5D, 0.5D, 0.5D, 0.05D);
                }
            }
        } else {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT, SoundCategory.PLAYERS, 1.2F, 0.9F);
            if (!world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("morphext.action.zombie_villager_cure.need_apple"), true);
            }
        }
    }
}

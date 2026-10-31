package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 牛专属动作：产奶与反刍 (Cow Milk & Rumination)。
 * 手持空铁桶可挤出鲜牛奶；未手持空桶时触发反刍，恢复饥饿度与饱和度并解除自身一个负面状态。
 */
public class CowMilkAction implements IAction, ICooldownAction {
    public static final String ID = "cow_milk";
    public static final int COOLDOWN_MILK = 100; // 5 秒
    public static final int COOLDOWN_RUMINATE = 300; // 15 秒

    private final Map<UUID, Integer> dynamicCooldowns = new ConcurrentHashMap<>();

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        if (entity != null) {
            Integer cd = this.dynamicCooldowns.remove(entity.getUniqueID());
            if (cd != null) {
                return cd;
            }
        }
        return 0;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        World world = player.world;

        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();

        EnumHand bucketHand = null;
        if (!main.isEmpty() && main.getItem() == Items.BUCKET) {
            bucketHand = EnumHand.MAIN_HAND;
        } else if (!off.isEmpty() && off.getItem() == Items.BUCKET) {
            bucketHand = EnumHand.OFF_HAND;
        }

        if (bucketHand != null) {
            // 手持空桶挤奶
            if (!player.isCreative()) {
                ItemStack stack = player.getHeldItem(bucketHand);
                stack.shrink(1);
            }

            ItemStack milkBucket = new ItemStack(Items.MILK_BUCKET);
            if (player.getHeldItemMainhand().isEmpty()) {
                player.setHeldItem(EnumHand.MAIN_HAND, milkBucket);
            } else if (!player.inventory.addItemStackToInventory(milkBucket)) {
                player.dropItem(milkBucket, false);
            }
            if (player.inventoryContainer != null) {
                player.inventoryContainer.detectAndSendChanges();
            }

            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_COW_MILK, SoundCategory.PLAYERS, 1.0F, 1.0F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.WATER_SPLASH,
                        player.posX, player.posY + 0.6D, player.posZ,
                        15, 0.3D, 0.3D, 0.3D, 0.05D);
            }

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_MILK);
        } else {
            // 未手持空桶：触发反刍
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_COW_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);

            // 恢复 1 点饥饿度与 1.0F 饱和度
            player.getFoodStats().addStats(1, 0.5F);

            // 解除自身 1 个负面药水效果
            PotionEffect badEffect = null;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect != null && effect.getPotion() != null && effect.getPotion().isBadEffect()) {
                    badEffect = effect;
                    break;
                }
            }
            if (badEffect != null) {
                player.removePotionEffect(badEffect.getPotion());
            }

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                        player.posX, player.posY + 0.8D, player.posZ,
                        10, 0.4D, 0.4D, 0.4D, 0.1D);
            }

            player.sendStatusMessage(new TextComponentTranslation("morphext.action.cow_milk.prompt"), true);

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_RUMINATE);
        }
    }
}

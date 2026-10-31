package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 村民以物易物 (Villager Barter)。
 * <p>
 * 依据 {@code EntityVillager} 的 IMerchant / {@code EntityAITradePlayer} 交易身份——
 * 交易是村民的第一身份。手持绿宝石时消耗 1 枚，换取随机村庄常见物资
 * （面包 / 纸 / 小麦 / 低级附魔书），还原走街串巷的以物易物。
 */
public class VillagerBarterAction implements IAction, ICooldownAction {
    public static final String ID = "morphext:villager_barter";
    public static final String ALIAS_ID = "villager_barter";
    public static final int COOLDOWN = 200; // 10 秒

    private static final Enchantment[] BOOK_ENCHANTS = {
            Enchantments.UNBREAKING, Enchantments.EFFICIENCY, Enchantments.PROTECTION,
            Enchantments.POWER, Enchantments.LUCK_OF_THE_SEA, Enchantments.FORTUNE
    };

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
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
        World world = entity.world;

        // 手持绿宝石才可交易
        ItemStack mainHand = player.getHeldItem(EnumHand.MAIN_HAND);
        ItemStack offHand = player.getHeldItem(EnumHand.OFF_HAND);
        boolean isOffHand = false;
        if (mainHand.getItem() == Items.EMERALD) {
            // 主手优先
        } else if (offHand.getItem() == Items.EMERALD) {
            isOffHand = true;
        } else {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0F, 0.9F);
            player.sendStatusMessage(new TextComponentTranslation("morphext.action.villager_barter.need_emerald"), true);
            return;
        }

        if (!player.isCreative()) {
            if (isOffHand) {
                offHand.shrink(1);
            } else {
                mainHand.shrink(1);
            }
        }

        // 随机村庄常见物资（面包 / 纸 / 小麦 / 低级附魔书）
        ItemStack goods;
        switch (world.rand.nextInt(4)) {
            case 0:
                goods = new ItemStack(Items.BREAD, 3 + world.rand.nextInt(4));
                break;
            case 1:
                goods = new ItemStack(Items.PAPER, 4 + world.rand.nextInt(5));
                break;
            case 2:
                goods = new ItemStack(Items.WHEAT, 3 + world.rand.nextInt(4));
                break;
            default:
            {
                goods = new ItemStack(Items.ENCHANTED_BOOK);
                Enchantment enchantment = BOOK_ENCHANTS[world.rand.nextInt(BOOK_ENCHANTS.length)];
                ItemEnchantedBook.addEnchantment(goods, new EnchantmentData(enchantment, 1));
                break;
            }
        }

        if (!player.addItemStackToInventory(goods)) {
            player.dropItem(goods, false);
        }

        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 1.1F, 0.9F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6F, 1.2F);

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                    player.posX, player.posY + 1.0D, player.posZ, 16, 0.5D, 0.6D, 0.5D, 0.02D);
        }
    }
}

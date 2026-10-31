package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
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
 * 哞菇专属动作：生态采撷 (Mooshroom Harvest)。
 * 手持碗可盛取蘑菇煲；手持空桶可挤牛奶；手持剪刀可采摘背部红蘑菇。
 */
public class MooshroomHarvestAction implements IAction, ICooldownAction {
    public static final String ID = "mooshroom_harvest";
    public static final int COOLDOWN_STEW = 60; // 3 秒
    public static final int COOLDOWN_MILK = 100; // 5 秒
    public static final int COOLDOWN_SHEAR = 1200; // 60 秒
    public static final int COOLDOWN_PROMPT = 20; // 1 秒防连点提示

    private final DynamicActionCooldowns dynamicCooldowns = new DynamicActionCooldowns();

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        // 非破坏查询（CORE-17）：任何额外查询不再消费 execute 写入的冷却值
        return this.dynamicCooldowns.get(entity);
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

        EnumHand useHand = null;
        int actionType = 0; // 1: 碗(蘑菇煲), 2: 铁桶(牛奶), 3: 剪刀(采红蘑菇)

        // 优先检查主手，其次检查副手
        if (!main.isEmpty()) {
            if (main.getItem() == Items.BOWL) {
                useHand = EnumHand.MAIN_HAND;
                actionType = 1;
            } else if (main.getItem() == Items.BUCKET) {
                useHand = EnumHand.MAIN_HAND;
                actionType = 2;
            } else if (main.getItem() == Items.SHEARS) {
                useHand = EnumHand.MAIN_HAND;
                actionType = 3;
            }
        }

        if (actionType == 0 && !off.isEmpty()) {
            if (off.getItem() == Items.BOWL) {
                useHand = EnumHand.OFF_HAND;
                actionType = 1;
            } else if (off.getItem() == Items.BUCKET) {
                useHand = EnumHand.OFF_HAND;
                actionType = 2;
            } else if (off.getItem() == Items.SHEARS) {
                useHand = EnumHand.OFF_HAND;
                actionType = 3;
            }
        }

        if (actionType == 1) {
            // 手持碗盛蘑菇煲
            if (!player.isCreative()) {
                player.getHeldItem(useHand).shrink(1);
            }

            ItemStack stew = new ItemStack(Items.MUSHROOM_STEW);
            if (player.getHeldItemMainhand().isEmpty()) {
                player.setHeldItem(EnumHand.MAIN_HAND, stew);
            } else if (!player.inventory.addItemStackToInventory(stew)) {
                player.dropItem(stew, false);
            }
            if (player.inventoryContainer != null) {
                player.inventoryContainer.detectAndSendChanges();
            }

            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.0F, 1.0F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.HEART,
                        player.posX, player.posY + 1.2D, player.posZ,
                        3, 0.3D, 0.3D, 0.3D, 0.1D);
            }

            this.dynamicCooldowns.set(player, COOLDOWN_STEW);
        } else if (actionType == 2) {
            // 手持空桶挤奶
            if (!player.isCreative()) {
                player.getHeldItem(useHand).shrink(1);
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

            this.dynamicCooldowns.set(player, COOLDOWN_MILK);
        } else if (actionType == 3) {
            // 手持剪刀剪蘑菇
            if (!player.isCreative()) {
                player.getHeldItem(useHand).damageItem(1, player);
            }

            player.dropItem(new ItemStack(Blocks.RED_MUSHROOM, 5), false);

            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_MOOSHROOM_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.0F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                        player.posX, player.posY + 1.0D, player.posZ,
                        25, 0.4D, 0.4D, 0.4D, 0.1D, Block.getStateId(Blocks.RED_MUSHROOM.getDefaultState()));
            }

            this.dynamicCooldowns.set(player, COOLDOWN_SHEAR);
        } else {
            // 皆非：播放叫声并在 Action Bar 提示
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_COW_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);

            player.sendStatusMessage(new TextComponentTranslation("morphext.action.mooshroom_harvest.prompt"), true);

            this.dynamicCooldowns.set(player, COOLDOWN_PROMPT);
        }
    }
}

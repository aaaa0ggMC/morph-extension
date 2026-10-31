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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 羊专属动作：啃食啃草与剪毛 (Sheep Graze & Shear)。
 * 检查脚下或正前方草方块，低头吃草将其转化为泥土并恢复饥饿度与饱和度；手持剪刀可剪下 1-3 团白色羊毛。
 */
public class SheepGrazeAction implements IAction, ICooldownAction {
    public static final String ID = "sheep_graze";
    public static final int COOLDOWN_GRAZE = 400; // 20 秒
    public static final int COOLDOWN_SHEAR = 900; // 45 秒
    public static final int COOLDOWN_PROMPT = 20; // 1 秒防连点提示

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

        EnumHand shearsHand = null;
        if (!main.isEmpty() && main.getItem() == Items.SHEARS) {
            shearsHand = EnumHand.MAIN_HAND;
        } else if (!off.isEmpty() && off.getItem() == Items.SHEARS) {
            shearsHand = EnumHand.OFF_HAND;
        }

        if (shearsHand != null) {
            // 手持剪刀剪毛
            if (!player.isCreative()) {
                player.getHeldItem(shearsHand).damageItem(1, player);
            }

            int count = 1 + world.rand.nextInt(3); // 1-3 团羊毛
            player.dropItem(new ItemStack(Blocks.WOOL, count, 0), false);

            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.0F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
                        player.posX, player.posY + 0.6D, player.posZ,
                        20, 0.3D, 0.3D, 0.3D, 0.05D, Item.getIdFromItem(Item.getItemFromBlock(Blocks.WOOL)));
            }

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_SHEAR);
            return;
        }

        // 未持剪刀：检查吃草（脚下或正前方方块）
        BlockPos under = player.getPosition().down();
        Vec3d look = player.getLookVec();
        BlockPos front = new BlockPos(player.posX + look.x, player.posY, player.posZ + look.z);
        BlockPos frontDown = front.down();
        BlockPos current = player.getPosition();

        BlockPos grassPos = null;
        if (world.getBlockState(under).getBlock() == Blocks.GRASS) {
            grassPos = under;
        } else if (world.getBlockState(frontDown).getBlock() == Blocks.GRASS) {
            grassPos = frontDown;
        } else if (world.getBlockState(front).getBlock() == Blocks.GRASS) {
            grassPos = front;
        } else if (world.getBlockState(current).getBlock() == Blocks.GRASS) {
            grassPos = current;
        }

        if (grassPos != null) {
            world.playEvent(2001, grassPos, Block.getStateId(Blocks.GRASS.getDefaultState()));
            world.setBlockState(grassPos, Blocks.DIRT.getDefaultState(), 3);

            world.playSound(null, grassPos.getX() + 0.5D, grassPos.getY() + 0.5D, grassPos.getZ() + 0.5D,
                    SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);

            // 恢复 2 点饥饿度与 2.0F 饱和度
            player.getFoodStats().addStats(2, 0.5F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                        grassPos.getX() + 0.5D, grassPos.getY() + 1.0D, grassPos.getZ() + 0.5D,
                        25, 0.3D, 0.2D, 0.3D, 0.1D, Block.getStateId(Blocks.GRASS.getDefaultState()));
            }

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_GRAZE);
        } else {
            // 无草方块且未持剪刀
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);

            player.sendStatusMessage(new TextComponentTranslation("morphext.action.sheep_graze.prompt"), true);

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_PROMPT);
        }
    }
}

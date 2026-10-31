package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.HashSet;
import java.util.Set;

/**
 * 村民专属动作：农耕丰收与播种 (Villager Harvest & Sowing)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.passive.EntityVillager} 与 {@link net.minecraft.entity.ai.EntityAIHarvestFarmland} 农耕机制：
 * 1. 【3x3 范围智能收割】：面向正前方扇区 3x3 范围成熟农作物（小麦、胡萝卜、马铃薯、甜菜根）一键成熟丰收；
 * 2. 【掉落物自动纳仓】：作物掉落物直接收拢收纳至玩家背包（背包满则在脚边生成掉落物）；
 * 3. 【自动原位回补补种】：收割瞬间自动扣除 1 份种子将耕地 (Blocks.FARMLAND) 重新栽种复原为初始生长状态；
 * 4. 【空耕地补种】：若 3x3 内存在裸露耕地且背包持有种子，自动播种填补；
 * 5. 【音效交互】：丰收爆发村民欢喜与草木清脆音效；若无成熟作物则播放经典村民点头回应音效。
 */
public class VillagerHarvestAction implements IAction, ICooldownAction {
    public static final String ID = "villager_harvest";
    public static final String ALIAS_ID = "morphext:villager_harvest";
    public static final int COOLDOWN = 100; // 5 秒冷却 (100 ticks)

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
        World world = player.world;
        EnumFacing facing = player.getHorizontalFacing();
        EnumFacing side = facing.rotateY();

        BlockPos playerPos = new BlockPos(player);
        BlockPos center = playerPos.offset(facing, 2);

        int harvestedCount = 0;
        int plantedCount = 0;
        Set<BlockPos> processed = new HashSet<>();

        // 遍历面向正前方 3x3 区域 (深度 1~3 格，宽度 3 格)
        for (int f = -1; f <= 1; f++) {
            for (int s = -1; s <= 1; s++) {
                BlockPos colPos = center.offset(facing, f).offset(side, s);

                // 纵向检测 [-1, 0, 1] 适应高低梯田
                for (int y = 1; y >= -1; y--) {
                    BlockPos checkPos = colPos.up(y);
                    if (processed.contains(checkPos)) {
                        continue;
                    }

                    IBlockState state = world.getBlockState(checkPos);
                    Block block = state.getBlock();

                    if (block instanceof BlockCrops) {
                        BlockCrops crop = (BlockCrops) block;
                        if (crop.isMaxAge(state)) {
                            processed.add(checkPos);

                            // 1. 获取作物掉落物
                            NonNullList<ItemStack> drops = NonNullList.create();
                            crop.getDrops(drops, world, checkPos, state, 0);

                            // 2. 检查下方是否为耕地，执行自动补种
                            BlockPos soilPos = checkPos.down();
                            IBlockState soilState = world.getBlockState(soilPos);
                            boolean replanted = false;

                            if (soilState.getBlock() == Blocks.FARMLAND) {
                                // 扣除 1 个种子作为播种消耗
                                for (int i = 0; i < drops.size(); i++) {
                                    ItemStack drop = drops.get(i);
                                    if (!drop.isEmpty()) {
                                        Item item = drop.getItem();
                                        if ((item == Items.WHEAT_SEEDS && block == Blocks.WHEAT)
                                                || (item == Items.CARROT && block == Blocks.CARROTS)
                                                || (item == Items.POTATO && block == Blocks.POTATOES)
                                                || (item == Items.BEETROOT_SEEDS && block == Blocks.BEETROOTS)) {
                                            drop.shrink(1);
                                            if (drop.isEmpty()) {
                                                drops.remove(i);
                                            }
                                            replanted = true;
                                            break;
                                        }
                                    }
                                }

                                // 即使概率没掉种子，村民神技依然保底补种初始作物
                                world.setBlockState(checkPos, crop.withAge(0), 3);
                                replanted = true;
                            } else {
                                world.setBlockToAir(checkPos);
                            }

                            // 3. 将产物直接收拢收纳给玩家背包
                            for (ItemStack drop : drops) {
                                if (!drop.isEmpty()) {
                                    if (!player.inventory.addItemStackToInventory(drop)) {
                                        player.dropItem(drop, false);
                                    }
                                }
                            }

                            harvestedCount++;

                            // 丰收微粒
                            if (world instanceof WorldServer) {
                                ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                                        checkPos.getX() + 0.5D, checkPos.getY() + 0.5D, checkPos.getZ() + 0.5D,
                                        8, 0.3D, 0.2D, 0.3D, 0.05D);
                            }
                            break;
                        }
                    } else if (block == Blocks.FARMLAND) {
                        // 发现裸露耕地，尝试从玩家背包取种子播种
                        BlockPos above = checkPos.up();
                        if (world.isAirBlock(above)) {
                            for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                                ItemStack invStack = player.inventory.getStackInSlot(slot);
                                if (!invStack.isEmpty()) {
                                    Item item = invStack.getItem();
                                    IBlockState plantState = null;
                                    if (item == Items.WHEAT_SEEDS) plantState = Blocks.WHEAT.getDefaultState();
                                    else if (item == Items.CARROT) plantState = Blocks.CARROTS.getDefaultState();
                                    else if (item == Items.POTATO) plantState = Blocks.POTATOES.getDefaultState();
                                    else if (item == Items.BEETROOT_SEEDS) plantState = Blocks.BEETROOTS.getDefaultState();

                                    if (plantState != null) {
                                        world.setBlockState(above, plantState, 3);
                                        if (!player.isCreative()) {
                                            invStack.shrink(1);
                                        }
                                        plantedCount++;
                                        processed.add(above);
                                        if (world instanceof WorldServer) {
                                            ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                                                    above.getX() + 0.5D, above.getY() + 0.2D, above.getZ() + 0.5D,
                                                    4, 0.2D, 0.1D, 0.2D, 0.05D);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        player.swingArm(EnumHand.MAIN_HAND);

        if (harvestedCount > 0 || plantedCount > 0) {
            // 播放作物收割与丰收欢悦音效
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.2F, 1.0F);
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 1.0F, 1.0F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                        player.posX, player.posY + 1.2D, player.posZ,
                        16, 0.6D, 0.4D, 0.6D, 0.08D);
            }

            player.sendStatusMessage(new TextComponentTranslation(
                    "morphext.action.villager_harvest.success", harvestedCount, plantedCount), true);
        } else {
            // 无成熟作物：播放村民点头交互音效与提示
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 1.0F, 0.9F);

            player.sendStatusMessage(new TextComponentTranslation(
                    "morphext.action.villager_harvest.no_crops"), true);
        }
    }
}

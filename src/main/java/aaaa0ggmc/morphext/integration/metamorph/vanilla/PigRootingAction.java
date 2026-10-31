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
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 猪专属动作：拱地寻宝 (Pig Rooting)。
 * 在泥土或草方块上用力拱地，随机挖掘出胡萝卜、马铃薯、甜菜根或甜菜种子，冷却 30 秒。
 */
public class PigRootingAction implements IAction, ICooldownAction {
    public static final String ID = "pig_rooting";
    public static final int COOLDOWN_ROOT = 600; // 30 秒
    public static final int COOLDOWN_PROMPT = 20; // 1 秒防连点提示

    private final DynamicActionCooldowns dynamicCooldowns = new DynamicActionCooldowns();

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        // 非破坏查询（CORE-17）：任何额外查询不再消费 execute 写入的冷却值
        return this.dynamicCooldowns.get(entity);
    }

    private static boolean isRootable(Block block) {
        return block == Blocks.GRASS
                || block == Blocks.DIRT
                || block == Blocks.FARMLAND
                || block == Blocks.GRASS_PATH
                || block == Blocks.MYCELIUM;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }

        World world = entity.world;
        BlockPos pos = entity.getPosition();
        BlockPos under = pos.down();

        boolean onGround = isRootable(world.getBlockState(under).getBlock())
                || isRootable(world.getBlockState(pos).getBlock());

        if (onGround) {
            // 播放挖掘泥土音效与猪叫
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.BLOCK_GRASS_HIT, SoundCategory.PLAYERS, 1.2F, 0.8F);
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.1F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                        entity.posX, entity.posY + 0.1D, entity.posZ,
                        30, 0.35D, 0.1D, 0.35D, 0.15D, Block.getStateId(Blocks.DIRT.getDefaultState()));
            }

            // 随机产出胡萝卜/马铃薯/甜菜根/甜菜种子
            int roll = world.rand.nextInt(4);
            ItemStack loot;
            switch (roll) {
                case 0:
                    loot = new ItemStack(Items.CARROT);
                    break;
                case 1:
                    loot = new ItemStack(Items.POTATO);
                    break;
                case 2:
                    loot = new ItemStack(Items.BEETROOT);
                    break;
                default:
                    loot = new ItemStack(Items.BEETROOT_SEEDS);
                    break;
            }
            entity.entityDropItem(loot, 0.1F);

            this.dynamicCooldowns.set(entity, COOLDOWN_ROOT);
        } else {
            // 未在适宜地面上拱地
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.PLAYERS, 1.0F, 0.9F);

            if (entity instanceof EntityPlayer) {
                ((EntityPlayer) entity).sendStatusMessage(new TextComponentTranslation("morphext.action.pig_rooting.prompt"), true);
            }

            this.dynamicCooldowns.set(entity, COOLDOWN_PROMPT);
        }
    }
}

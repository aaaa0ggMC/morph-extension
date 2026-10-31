package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import aaaa0ggmc.morphext.integration.metamorph.IMorphAnimationAction;
import aaaa0ggmc.morphext.integration.metamorph.MorphSyncHelper;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
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
 * 羊专属主动技能：啃草吃食与毛发生长 (Sheep Graze Action)。
 * 1. 深度还原原版 EntitySheep.eatGrassBonus 机制；
 * 2. 检查脚下、正前方及视野低处的草方块 (Blocks.GRASS)；
 * 3. 将草方块啃食变为泥土 (Blocks.DIRT)，播放咀嚼与破裂音效，恢复饥饿度与饱和度；
 * 4. 关键联动：若当前处于秃毛状态 (isSheared == true)，吃草后立刻重置为 false，让羊毛完整重生；
 * 5. 全网广播 PacketMorphPlayer 同步新羊毛外观与 NBT 数据。
 */
public class SheepGrazeAction implements IAction, ICooldownAction, IMorphAnimationAction {
    public static final String ID = "sheep_graze";
    public static final String ALIAS_ID = "morphext:sheep_graze";
    public static final int COOLDOWN_GRAZE = 200;  // 10 秒
    public static final int COOLDOWN_PROMPT = 20;  // 1 秒防连点

    private final DynamicActionCooldowns dynamicCooldowns = new DynamicActionCooldowns();

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        // 非破坏查询（CORE-17）：任何额外查询不再消费 execute 写入的冷却值
        int dynamic = this.dynamicCooldowns.get(entity);
        return dynamic > 0 ? dynamic : COOLDOWN_GRAZE;
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

        // 探测草方块位置（脚底、前方俯视、面前平视）
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
            // 啃食并将草方块转为泥土
            world.playEvent(2001, grassPos, Block.getStateId(Blocks.GRASS.getDefaultState()));
            world.setBlockState(grassPos, Blocks.DIRT.getDefaultState(), 3);

            world.playSound(null, grassPos.getX() + 0.5D, grassPos.getY() + 0.5D, grassPos.getZ() + 0.5D,
                    SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.0F, 1.0F);

            // 恢复饱腹度
            player.getFoodStats().addStats(3, 0.6F);

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                        grassPos.getX() + 0.5D, grassPos.getY() + 1.0D, grassPos.getZ() + 0.5D,
                        25, 0.3D, 0.2D, 0.3D, 0.1D, Block.getStateId(Blocks.GRASS.getDefaultState()));
            }

            // 联动机制：若处于剪毛状态，重新长出羊毛
            boolean wasSheared = false;
            EntitySheep sheep = null;
            NBTTagCompound entityData = null;

            if (morph instanceof EntityMorph) {
                EntityMorph em = (EntityMorph) morph;
                EntityLivingBase mob = em.getEntity(world);
                if (mob instanceof EntitySheep) {
                    sheep = (EntitySheep) mob;
                }
                entityData = em.getEntityData();
            }

            if (sheep != null && sheep.getSheared()) {
                wasSheared = true;
                sheep.setSheared(false);
            }
            if (entityData != null && entityData.hasKey("Sheared") && entityData.getBoolean("Sheared")) {
                wasSheared = true;
                entityData.setBoolean("Sheared", false);
            }

            if (wasSheared) {
                // 广播同步羊毛重生长外观 (平滑更替，不触发 20-tick 翻转爆炸过渡动画)
                MorphSyncHelper.syncMorph(player, morph);
                player.sendStatusMessage(new TextComponentTranslation("morphext.action.sheep_graze.wool_regrown"), true);
                world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.2F, 1.1F);
            }

            player.swingArm(EnumHand.MAIN_HAND);
            this.dynamicCooldowns.set(player, COOLDOWN_GRAZE);
        } else {
            // 未找到可啃食草方块
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            player.sendStatusMessage(new TextComponentTranslation("morphext.action.sheep_graze.no_grass"), true);
            this.dynamicCooldowns.set(player, COOLDOWN_PROMPT);
        }
    }
}

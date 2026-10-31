package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 僵尸主动技能：破门而入 (Door Break)。
 * <p>
 * 依据 {@code EntityZombie} 的破门 AI（EntityAIBreakDoor，僵尸最著名的行为之一）——
 * 捣毁面前的木门（仅木门，铁门无恙），扬起木屑。0 伤害纯工具技，冷却较长。
 */
public class ZombieDoorBreakAction implements IAction, ICooldownAction {
    public static final String ID = "zombie_door_break";
    public static final String ALIAS_ID = "morphext:zombie_door_break";
    public static final int COOLDOWN = 400; // 20 秒

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }

        World world = entity.world;
        entity.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);

        EnumFacing facing = entity.getHorizontalFacing();
        boolean broke = false;
        for (int step = 1; step <= 2 && !broke; step++) {
            BlockPos base = entity.getPosition().offset(facing, step);
            for (BlockPos pos : new BlockPos[] { base, base.up() }) {
                IBlockState state = world.getBlockState(pos);
                Block block = state.getBlock();
                if (!(block instanceof BlockDoor) || state.getMaterial() != Material.WOOD) {
                    continue;
                }
                world.setBlockToAir(pos);
                broke = true;
                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                            12, 0.3D, 0.3D, 0.3D, 0.05D,
                            Block.getStateId(state));
                }
            }
        }

        if (broke) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 1.5F, 0.8F);
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_DOOR_WOOD, SoundCategory.PLAYERS, 1.3F, 1.0F);
        } else {
            // 没有木门可破时保留捶打音效反馈
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_DOOR_WOOD, SoundCategory.PLAYERS, 0.8F, 1.2F);
        }
    }
}

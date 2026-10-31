package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.BlockSilverfish;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 蠹虫群体呼唤音波 (Silverfish Swarm Call Action):
 * 释放高频虫鸣震荡波，唤醒周围 12 格内隐藏在怪物蛋方块（蠹虫石）里的蠹虫破石破土支援！
 * 若无蠹虫方块，则释放音波使身周 8 格敌人陷入迟缓与击退。
 */
public class SilverfishSwarmCallAction implements IAction {

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SILVERFISH_AMBIENT, SoundCategory.HOSTILE, 1.4F, 1.2F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SILVERFISH_HURT, SoundCategory.HOSTILE, 1.2F, 0.8F);

        if (world.isRemote) return;

        BlockPos center = new BlockPos(entity);
        int awakened = 0;
        int radius = 10;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = center.add(x, y, z);
                    IBlockState state = world.getBlockState(p);
                    if (state.getBlock() instanceof BlockSilverfish) {
                        world.destroyBlock(p, false);
                        EntitySilverfish silverfish = new EntitySilverfish(world);
                        silverfish.setLocationAndAngles(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 0.0F, 0.0F);
                        world.spawnEntity(silverfish);
                        silverfish.spawnExplosionParticle();
                        awakened++;
                        if (awakened >= 6) break;
                    }
                }
                if (awakened >= 6) break;
            }
            if (awakened >= 6) break;
        }

        // 若无方块被唤醒，生成 2 只临时共生蠹虫助战
        if (awakened == 0) {
            for (int i = 0; i < 2; i++) {
                EntitySilverfish silverfish = new EntitySilverfish(world);
                silverfish.setLocationAndAngles(
                        entity.posX + (world.rand.nextDouble() - 0.5D) * 1.5D,
                        entity.posY,
                        entity.posZ + (world.rand.nextDouble() - 0.5D) * 1.5D,
                        0.0F, 0.0F);
                world.spawnEntity(silverfish);
                silverfish.spawnExplosionParticle();
            }
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                    entity.posX, entity.posY + 0.2D, entity.posZ, 25, 0.8D, 0.2D, 0.8D, 0.05D);
        }
    }
}

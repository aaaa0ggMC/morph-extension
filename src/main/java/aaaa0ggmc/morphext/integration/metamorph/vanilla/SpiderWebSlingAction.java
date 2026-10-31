package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWeb;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 蜘蛛主动技能：吐网缠缚 (Web Sling)。
 * <p>
 * 依据蜘蛛结网/吐丝的生态位（web_trap 的远程单体版）——向准星目标喷出一蓬蛛网，
 * 在其立足点结出临时蛛网（5 秒后自动清除，不永久改变地形）并令其短暂减速。
 * 不造成直接伤害。
 */
public class SpiderWebSlingAction implements IAction, ICooldownAction {
    public static final String ID = "spider_web_sling";
    public static final String ALIAS_ID = "morphext:spider_web_sling";
    public static final int COOLDOWN = 240; // 12 秒
    public static final int WEB_DURATION = 100; // 5 秒自动清除

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

        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 16.0D, 0.7D);
        if (target == null) {
            return;
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.4F, 1.3F);

        target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 2, false, true));

        // 目标立足点结网（仅替换可替换方块，5 秒后若仍是蛛网则清除）
        List<BlockPos> placed = new ArrayList<>();
        BlockPos base = new BlockPos(target.posX, target.posY, target.posZ);
        for (BlockPos pos : new BlockPos[] { base, base.up(), base.north(), base.south(), base.east(), base.west() }) {
            Block current = world.getBlockState(pos).getBlock();
            if (current != Blocks.AIR && !world.getBlockState(pos).getMaterial().isReplaceable()) {
                continue;
            }
            world.setBlockState(pos, Blocks.WEB.getDefaultState());
            placed.add(pos);
        }
        if (!placed.isEmpty()) {
            final List<BlockPos> toClear = placed;
            ServerActionScheduler.schedule(WEB_DURATION, () -> {
                for (BlockPos pos : toClear) {
                    if (world.getBlockState(pos).getBlock() instanceof BlockWeb) {
                        world.setBlockToAir(pos);
                        if (world instanceof WorldServer) {
                            ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                                    4, 0.2D, 0.2D, 0.2D, 0.02D,
                                    Block.getStateId(Blocks.WEB.getDefaultState()));
                        }
                    }
                }
            });
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, target.posX, target.posY + 0.5D, target.posZ, 16, 0.5D, 0.5D, 0.5D, 0.02D);
            VanillaActionHelper.drawParticleLine(ws,
                    VanillaActionHelper.getSafeSpawnPos(entity, 1.0D),
                    new net.minecraft.util.math.Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ),
                    EnumParticleTypes.CRIT, 0.5D, 0.05D);
        }
    }
}

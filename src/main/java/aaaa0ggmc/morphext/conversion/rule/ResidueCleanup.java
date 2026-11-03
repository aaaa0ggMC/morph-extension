package aaaa0ggmc.morphext.conversion.rule;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * 死法残留清理：转化「接住死亡」后，把死法留下的坑填掉，否则会连死。
 *
 * 对照仪式框架的残留表：窒息=破封（清掉规则自己的掩埋方块 + 确保头顶净空）、
 * 溺水=回满空气（仍在水中给短暂水下呼吸）、火焰/岩浆=熄灭 + 脱离火源、
 * 摔落=清零、爆炸=清零动量、凋零=清效果、闪电=灭火。
 */
public final class ResidueCleanup {
    private ResidueCleanup() {
    }

    public static void cleanup(EntityPlayerMP player, DamageSource source, ConversionRule rule) {
        if (source == null) {
            return;
        }
        String type = String.valueOf(source.getDamageType()).toLowerCase(Locale.ROOT);
        switch (type) {
            case "inwall":
                clearBurial(player, rule);
                ensureHeadroom(player);
                break;
            case "drown":
                player.setAir(30000);
                if (player.isInWater()) {
                    player.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING, 100, 0, false, false));
                }
                break;
            case "onfire":
            case "infire":
            case "lava":
            case "lightningbolt":
                player.extinguish();
                if (player.isInLava() || isInsideFire(player)) {
                    teleportToSafety(player);
                }
                break;
            case "fall":
                player.fallDistance = 0.0F;
                break;
            case "explosion":
                player.motionX = 0.0D;
                player.motionY = 0.0D;
                player.motionZ = 0.0D;
                player.extinguish();
                break;
            case "wither":
                player.removePotionEffect(MobEffects.WITHER);
                break;
            default:
                break;
        }
    }

    /** 清掉规则自己声明的掩埋方块（沙葬的沙子），不动玩家其它建筑。 */
    private static void clearBurial(EntityPlayerMP player, ConversionRule rule) {
        if (rule.place.nearBlocks == null || rule.place.nearBlocks.isEmpty()) {
            return;
        }
        BlockPos base = new BlockPos(player);
        for (BlockPos pos : new BlockPos[]{base, base.up(), base.up(2)}) {
            String name = String.valueOf(net.minecraft.block.Block.REGISTRY
                    .getNameForObject(player.world.getBlockState(pos).getBlock()));
            for (String want : rule.place.nearBlocks) {
                if (want.equalsIgnoreCase(name)) {
                    player.world.setBlockToAir(pos);
                }
            }
        }
    }

    /** 头顶/身上还有方块（破封没破干净）时，上传到首个能站的地方。 */
    private static void ensureHeadroom(EntityPlayerMP player) {
        BlockPos base = new BlockPos(player);
        for (int i = 0; i <= 4; i++) {
            BlockPos p = base.up(i);
            if (player.world.isAirBlock(p) && player.world.isAirBlock(p.up())) {
                if (i > 0) {
                    player.setPosition(player.posX, p.getY(), player.posZ);
                }
                return;
            }
        }
    }

    private static boolean isInsideFire(EntityPlayerMP player) {
        BlockPos pos = new BlockPos(player);
        return player.world.getBlockState(pos).getBlock() == Blocks.FIRE
                || player.world.getBlockState(pos.up()).getBlock() == Blocks.FIRE;
    }

    /** 在附近找一处「脚下实、身上空」的位置传送过去（岩浆/火里脱身用）。 */
    private static void teleportToSafety(EntityPlayerMP player) {
        World world = player.world;
        BlockPos base = new BlockPos(player);
        for (int dy = -2; dy <= 6; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (world.isAirBlock(p) && world.isAirBlock(p.up())
                            && world.getBlockState(p.down()).isSideSolid(world, p.down(), net.minecraft.util.EnumFacing.UP)) {
                        player.setPosition(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
                        return;
                    }
                }
            }
        }
    }
}

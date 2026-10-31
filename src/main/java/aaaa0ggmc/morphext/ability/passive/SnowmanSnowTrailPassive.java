package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;

/**
 * 雪傀儡极寒雪径被动 (Snow Golem Snow Trail & Thermal Sensitivity Passive)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntitySnowman#onLivingUpdate()} 原版机理：
 * 1. 【极寒雪径】：足迹所踏足的空气方块若具有稳固支撑且气温适宜，自动铺设雪层 (Blocks.SNOW_LAYER)；
 * 2. 【岩浆融化保护】：临近熔岩或烈火时，雪层自然消融气化并发出蒸汽熄灭声；
 * 3. 【炎热群系融化伤害】：在群系温度大于 1.0（如沙漠、下界、热带草原）的环境中，每秒受到 1 点高温灼烧融化伤害 (HotBiomesDamage)。
 */
public class SnowmanSnowTrailPassive extends AbstractPassiveAbility {
    public static final String ID = "snowman_snow_trail";

    public SnowmanSnowTrailPassive(String id) {
        super(id, "morphext.passive.snowman_snow_trail.name", "morphext.passive.snowman_snow_trail.desc", new ItemStack(Blocks.SNOW_LAYER));
    }

    public SnowmanSnowTrailPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }

        World world = player.world;

        // 1. 炎热生物群系每秒灼烧融化伤害 (HotBiomesDamage，20 ticks 一次)
        if (player.ticksExisted % 20 == 0) {
            BlockPos pPos = new BlockPos(player);
            Biome biome = world.getBiome(pPos);
            float temp = biome.getTemperature(pPos);

            if (temp > 1.0F && !player.capabilities.isCreativeMode) {
                player.attackEntityFrom(DamageSource.ON_FIRE, 1.0F);

                world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6F, 1.6F);

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                            player.posX, player.posY + 1.0D, player.posZ, 8, 0.2D, 0.4D, 0.2D, 0.02D);
                }
            }
        }

        // 2. 极寒足迹铺设雪层 (严谨还原 EntitySnowman 4 点采样机制)
        for (int l = 0; l < 4; ++l) {
            int i = MathHelper.floor(player.posX + (double) ((float) (l % 2 * 2 - 1) * 0.25F));
            int j = MathHelper.floor(player.posY);
            int k = MathHelper.floor(player.posZ + (double) ((float) (l / 2 % 2 * 2 - 1) * 0.25F));
            BlockPos bPos = new BlockPos(i, j, k);

            // 遇到高温熔岩自然融化判定
            boolean nearThermalHazard = isNearThermalHazard(world, bPos);

            if (world.getBlockState(bPos).getMaterial() == Material.AIR
                    && world.getBiome(bPos).getTemperature(bPos) < 0.8F
                    && Blocks.SNOW_LAYER.canPlaceBlockAt(world, bPos)
                    && !nearThermalHazard) {
                world.setBlockState(bPos, Blocks.SNOW_LAYER.getDefaultState());
            } else if (nearThermalHazard && world.getBlockState(bPos).getBlock() == Blocks.SNOW_LAYER) {
                // 临近岩浆雪层自然融化
                world.setBlockToAir(bPos);
                world.playSound(null, bPos.getX(), bPos.getY(), bPos.getZ(),
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.4F, 1.8F);
            }
        }
    }

    private static boolean isNearThermalHazard(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos check = pos.offset(facing);
            Material mat = world.getBlockState(check).getMaterial();
            if (mat == Material.LAVA || mat == Material.FIRE) {
                return true;
            }
        }
        return false;
    }
}

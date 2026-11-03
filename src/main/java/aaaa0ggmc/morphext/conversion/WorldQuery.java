package aaaa0ggmc.morphext.conversion;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * 世界查询小工具（转化规则判定用）。
 */
public final class WorldQuery {
    private WorldQuery() {
    }

    /**
     * 取位置的真实群系：读区块实存的群系数组。
     *
     * 不要用 {@code World#getBiome}：它走 BiomeProvider 的生成层缓存，
     * 群系模组（BOP/RTG/JEID 类）改写提供器后可能恒报错误值
     * （实测过整张地图恒报 Deep Ocean），而区块实存数据才与地形一致。
     */
    public static Biome biomeAt(World world, BlockPos pos) {
        return world.getChunk(pos).getBiome(pos, world.getBiomeProvider());
    }
}

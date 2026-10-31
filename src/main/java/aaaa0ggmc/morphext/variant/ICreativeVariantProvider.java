package aaaa0ggmc.morphext.variant;

import net.minecraft.world.World;
import java.util.List;

/**
 * 创造模式变体提供者契约 (Creative Variant Provider SPI)。
 * 任何扩展或补丁模组（如 morphext-patch-abyssalcraft）均可实现此接口，
 * 将专属的复杂变种（如修格斯阶数、特定 NBT、恶魔/反物质动物幼年）注入给 morphext，
 * 再由 morphext 统一挂载至 Metamorph 的创造模式选单与检索系统中。
 */
public interface ICreativeVariantProvider {
    /**
     * 提供变体形态定义清单
     * @param world 客户端当前世界实例（可用于查询注册状态）
     * @return 变体形态列表
     */
    List<CreativeVariantDef> getVariants(World world);
}

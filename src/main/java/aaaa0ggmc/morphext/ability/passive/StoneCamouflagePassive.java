package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;

/**
 * 岩石拟态被动 (Stone Camouflage Passive)。
 * <p>
 * 依据 {@code EntitySilverfish} 的 AIHideInStone（蠹虫潜入石块、以虫蚀石为巢）——
 * 站在岩石类方块上时如化石为虫般与岩石融为一体获得隐身，离开岩石立即失效。
 */
public class StoneCamouflagePassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:stone_camouflage";

    public StoneCamouflagePassive(String id, String nameKey, String descKey, ItemStack icon) {
        super(id, nameKey, descKey, icon);
    }

    public StoneCamouflagePassive(String id) {
        this(id, "morphext.passive." + id.replace(':', '.') + ".name",
                "morphext.passive." + id.replace(':', '.') + ".desc", new ItemStack(Items.FLINT));
    }

    public StoneCamouflagePassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }
        if (player.ticksExisted % 10 != 0) {
            return;
        }
        BlockPos below = new BlockPos(player.posX, player.getEntityBoundingBox().minY - 0.5D, player.posZ);
        IBlockState state = player.world.getBlockState(below);
        if (state.getMaterial() == Material.ROCK) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, true));
        }
    }
}

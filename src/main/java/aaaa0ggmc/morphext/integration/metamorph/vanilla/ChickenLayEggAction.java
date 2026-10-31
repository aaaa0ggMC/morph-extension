package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 鸡专属动作：母鸡下蛋 (Chicken Lay Egg)。
 * 产下一枚新鲜鸡蛋，冷却 45 秒。
 */
public class ChickenLayEggAction implements IAction, ICooldownAction {
    public static final String ID = "chicken_lay_egg";
    public static final int COOLDOWN = 900; // 45 秒 (900 ticks)

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
        entity.entityDropItem(new ItemStack(Items.EGG), 0.1F);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 1.0F,
                (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F);

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
                    entity.posX, entity.posY + 0.2D, entity.posZ,
                    12, 0.2D, 0.1D, 0.2D, 0.05D, Item.getIdFromItem(Items.EGG));
        }
    }
}

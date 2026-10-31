package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Random;

/**
 * 女巫主动技能：投掷剧毒秘药 (Witch Throw Potion)。
 * 随机投掷喷溅型有害药水（迟缓、剧毒、虚弱、瞬伤），发射起点安全前移 1.5 格规避自砸。
 */
public class WitchThrowPotionAction implements IAction, ICooldownAction {
    public static final String ID = "witch_throw_potion";
    public static final int COOLDOWN = 160; // 8 秒
    private static final Random RANDOM = new Random();

    private static final PotionType[] HARMFUL_POTIONS = new PotionType[] {
            PotionTypes.SLOWNESS,
            PotionTypes.POISON,
            PotionTypes.WEAKNESS,
            PotionTypes.HARMING
    };

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
        entity.swingArm(EnumHand.MAIN_HAND);

        // 随机有害药水类型
        PotionType selected = HARMFUL_POTIONS[RANDOM.nextInt(HARMFUL_POTIONS.length)];
        ItemStack potionStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), selected);

        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.5D);

        EntityPotion potion = new EntityPotion(world, entity, potionStack);
        potion.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        // 沿视线微仰角抛掷
        potion.shoot(entity, entity.rotationPitch, entity.rotationYaw, -15.0F, 0.85F, 1.0F);
        world.spawnEntity(potion);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, spawnPos.x, spawnPos.y, spawnPos.z, 10, 0.2, 0.2, 0.2, 0.05);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS, 1.4F, 0.9F + RANDOM.nextFloat() * 0.2F);
    }
}

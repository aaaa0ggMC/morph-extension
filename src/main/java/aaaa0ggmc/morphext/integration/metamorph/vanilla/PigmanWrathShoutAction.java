package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 僵尸猪人怒潮长啸 (Pigman Wrath Shout Action):
 * 仰天发出震耳欲聋的血怒战吼，鼓舞周围 16 格内同族与自身，赋予 15 秒力量 II 与抗性提升 I！
 */
public class PigmanWrathShoutAction implements IAction {

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        entity.swingArm(EnumHand.MAIN_HAND);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ZOMBIE_PIG_ANGRY, SoundCategory.HOSTILE, 1.8F, 0.9F);

        if (world.isRemote) return;

        entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 300, 1));
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 300, 0));

        double radius = 16.0D;
        List<EntityPigZombie> pigmen = world.getEntitiesWithinAABB(EntityPigZombie.class,
                entity.getEntityBoundingBox().grow(radius),
                p -> p != null && p.isEntityAlive() && !p.equals(entity));

        for (EntityPigZombie p : pigmen) {
            p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 300, 1));
            p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 300, 0));
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    entity.posX, entity.posY + entity.getEyeHeight() + 0.4D, entity.posZ,
                    15, 0.8D, 0.3D, 0.8D, 0.05D);
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 雪傀儡主动技能：极寒冰环 (Frost Aura)。
 * 爆发周身刺骨极寒气旋，重创并冰冻周围敌对生物，造成减速与虚弱。
 */
public class FrostAuraAction implements IAction, ICooldownAction {
    public static final String ID = "frost_aura";
    public static final int COOLDOWN = 80; // 4 秒

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
        double radius = 5.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, 2.0D, radius);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase e : list) {
            e.attackEntityFrom(source, 5.0F);
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 2));
            e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 80, 0));
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SNOWBALL,
                    entity.posX, entity.posY + 0.3D, entity.posZ,
                    50, 1.8D, 0.4D, 1.8D, 0.1D);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2F, 1.6F);
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 远古守卫者主动技能：深海诅咒 (Elder Curse)。
 * 爆发远古遗迹的恐怖诅咒灵压，让周围 16 格内的生物陷入挖掘疲劳 III、虚弱与缓慢，
 * 并对范围内的玩家投射远古守卫者的幽魂幻影。
 */
public class ElderCurseAction implements IAction, ICooldownAction {
    public static final String ID = "elder_curse";
    public static final int COOLDOWN = 280; // 14 秒

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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 远古诅咒音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 诅咒黑水与暗影粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SUSPENDED_DEPTH, x, y + 1.0, z, 50, 2.0, 1.5, 2.0, 0.05);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y + 1.0, z, 30, 1.5, 1.0, 1.5, 0.1);
        }

        double radius = 16.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, radius * 0.5, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive());

        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 240, 2, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 120, 1, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 120, 0, false, true));

            // 对受诅咒的玩家施加标志性的幽魂掠过屏幕视觉粒子
            if (target instanceof EntityPlayerMP && world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                ws.spawnParticle((EntityPlayerMP) target, EnumParticleTypes.MOB_APPEARANCE, false,
                        target.posX, target.posY, target.posZ, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}

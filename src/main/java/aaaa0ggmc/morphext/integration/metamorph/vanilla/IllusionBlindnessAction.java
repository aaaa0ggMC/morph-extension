package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 幻术师主动技能：致盲幻咒 (Illusion Blindness)。
 * 吟唱幻术咒文向准星目标投射致盲幽光，命中致盲 8 秒；同时自身获得 6 秒隐形与速度 II，并在原地留下迷惑敌人的幻影烟雾。
 */
public class IllusionBlindnessAction implements IAction, ICooldownAction {
    public static final String ID = "illusion_blindness";
    public static final int COOLDOWN = 400; // 20 秒

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

        // 原地幻象粒子与施法音效
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double x = entity.posX;
            double y = entity.posY + entity.height * 0.5D;
            double z = entity.posZ;
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, x, y, z, 25, 0.5, 0.4, 0.5, 1.0);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 10, 0.4, 0.3, 0.4, 0.05);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 1.8F, 1.0F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ILLAGER_PREPARE_BLINDNESS, SoundCategory.PLAYERS, 1.5F, 1.0F);

        // 自身获得 6 秒 (120 ticks) 隐形与速度 II
        entity.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 120, 0, false, false));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 120, 1, false, false));

        // 准星 32 格索敌并施加致盲幽光
        Vec3d eyePos = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        Vec3d look = entity.getLookVec();
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.35D);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            if (target != null) {
                Vec3d targetCenter = new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ);
                VanillaActionHelper.drawParticleLine(ws, eyePos, targetCenter, EnumParticleTypes.SPELL, 0.4D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.SPELL_MOB, targetCenter.x, targetCenter.y, targetCenter.z, 15, 0.3, 0.3, 0.3, 0.0);
            } else {
                Vec3d beamEnd = eyePos.add(look.scale(32.0D));
                VanillaActionHelper.drawParticleLine(ws, eyePos, beamEnd, EnumParticleTypes.SPELL, 0.5D, 0.05D);
            }
        }

        if (target != null) {
            // 致盲 8 秒 (160 ticks) 与轻微魔法震颤
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 160, 0, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 0, false, true));
            target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(entity, entity), 4.0F);

            world.playSound(null, target.posX, target.posY, target.posZ,
                    SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.4F, 1.2F);
        }
    }
}

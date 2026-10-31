package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
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
 * 顺应灾厄魔法源码体系：
 * 1. 施法咒文音画驱动：高举双手吟唱致盲法术 (SpellType.BLINDNESS) 并散发暗夜幽黑魔纹粒子；
 * 2. 自由射击与致盲光球：顺着准星视线向前投射致盲光束与幽光波纹，无锁定状态亦可畅快发射；
 * 3. 命中致盲与削弱：命中目标附加 8 秒 (160 ticks) 致盲与 4 秒迟缓减速，并造成 5 点暗黑魔法重击。
 */
public class IllusionBlindnessAction implements IAction, ICooldownAction {
    public static final String ID = "illusion_blindness";
    public static final int COOLDOWN = 200; // 10 秒

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

        // 施法咒文音画驱动 (SpellType.BLINDNESS + 幽黑魔纹粒子 + 致盲施法长吟)
        VanillaActionHelper.triggerSpellEffects(world, entity, morph,
                EntitySpellcasterIllager.SpellType.BLINDNESS,
                SoundEvents.ENTITY_ILLAGER_PREPARE_BLINDNESS,
                SoundEvents.ENTITY_ILLAGER_CAST_SPELL);

        Vec3d eyePos = new Vec3d(entity.posX, entity.posY + (double) entity.getEyeHeight(), entity.posZ);
        Vec3d look = entity.getLookVec();

        // 准星 32 格自由索敌与射击判定
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.35D);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            if (target != null) {
                Vec3d targetCenter = new Vec3d(target.posX, target.posY + target.height * 0.5D, target.posZ);
                VanillaActionHelper.drawParticleLine(ws, eyePos, targetCenter, EnumParticleTypes.SPELL_MOB, 0.4D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, targetCenter.x, targetCenter.y, targetCenter.z, 15, 0.3, 0.3, 0.3, 0.05);
            } else {
                Vec3d beamEnd = eyePos.add(look.scale(32.0D));
                VanillaActionHelper.drawParticleLine(ws, eyePos, beamEnd, EnumParticleTypes.SPELL_MOB, 0.5D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, beamEnd.x, beamEnd.y, beamEnd.z, 10, 0.4, 0.4, 0.4, 0.05);
            }
        }

        if (target != null) {
            // 致盲 8 秒 (160 ticks) 与迟缓 4 秒
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 160, 0, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 0, false, true));
            target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(entity, entity), 5.0F);

            world.playSound(null, target.posX, target.posY, target.posZ,
                    SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.4F, 1.2F);
        }
    }
}

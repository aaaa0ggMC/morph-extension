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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 凋灵大招主动技能：凋灵诞生冲击波 (Wither Invulnerable Birth Blast)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.EntityWither} 蓄能诞生爆破终极机理：
 * 1. 【3 秒无敌蓄能】：进入 60 Tick (3 秒) 诞生充能状态，期间获得绝对无敌抗性与生命回溯；
 * 2. 【全域警报与微粒】：播放 1023 凋灵诞生全图广播巨响，周身涌动狂暴白色电弧与暗夜黑烟；
 * 3. 【终极冲击波爆发】：充能完毕瞬间在核心引爆威力 7.0 的灭世冲击波（防破坏方块，安全环保）；
 * 4. 【范围重创与震退】：重创身周 15 格内所有敌人造成 25 点破甲伤害、15 秒凋零 II 效果并狠狠轰飞；
 * 5. 【自残免疫豁免】：施法者自身绝对豁免本次爆炸的伤害与击退。
 */
public class WitherBirthBlastAction implements IAction, ICooldownAction {
    public static final String ID = "wither_birth_blast";
    public static final int COOLDOWN = 600; // 30 秒大招冷却

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

        // 1. 广播凋灵诞生全图音效与低沉原声
        world.playBroadcastSound(1023, new BlockPos(entity), 0);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 2.5F, 0.75F);

        // 2. 蓄能期间赋予抗性提升 V (减免 100% 伤害，还原原版 invulTime 期间完全无敌)
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 65, 4, false, true));

        // 3. 调度 60 Ticks (3 秒) 充能蓄能过程
        ServerActionScheduler.scheduleTicker(60, tickLeft -> {
            if (entity.isDead || entity.world == null) {
                return;
            }

            World cw = entity.world;
            if (cw instanceof WorldServer) {
                WorldServer ws = (WorldServer) cw;
                // 周身白色电弧与暗夜死雾狂涌
                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, entity.posX, entity.posY + 1.2D, entity.posZ,
                        8, 0.6D, 0.6D, 0.6D, 0.08D);
                ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, entity.posX, entity.posY + 1.0D, entity.posZ,
                        6, 0.5D, 0.5D, 0.5D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, entity.posX, entity.posY + 1.2D, entity.posZ,
                        4, 0.4D, 0.4D, 0.4D, 0.02D);
            }

            // 每 10 Ticks 恢复生命（还原原版蓄能回血）
            if (tickLeft % 10 == 0) {
                entity.heal(6.0F);
            }
        }, () -> {
            if (entity.isDead || entity.world == null) {
                return;
            }

            World blastWorld = entity.world;
            double bx = entity.posX;
            double by = entity.posY + (double) entity.getEyeHeight();
            double bz = entity.posZ;

            // 4. 原版 7.0 威力安全非破坏性爆炸（false, false: 纯冲击波与伤害，不破坏玩家基建）
            blastWorld.newExplosion(entity, bx, by, bz, 7.0F, false, false);

            // 5. 惊天巨响与核爆级微粒
            blastWorld.playSound(null, bx, by, bz, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.0F, 0.7F);
            blastWorld.playSound(null, bx, by, bz, SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 2.0F, 1.2F);

            if (blastWorld instanceof WorldServer) {
                WorldServer ws = (WorldServer) blastWorld;
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, bx, by, bz, 3, 1.0D, 1.0D, 1.0D, 0.0D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, bx, by, bz, 40, 2.0D, 1.5D, 2.0D, 0.1D);
            }

            // 6. 对 15 格内所有敌人施加 25 点破甲伤害、15 秒凋零 II 与暴虐震荡击飞
            double range = 15.0D;
            AxisAlignedBB box = entity.getEntityBoundingBox().grow(range, 6.0D, range);
            List<EntityLivingBase> enemies = blastWorld.getEntitiesWithinAABB(EntityLivingBase.class, box,
                    e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

            DamageSource blastSource = entity instanceof EntityPlayer
                    ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                    : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

            for (EntityLivingBase enemy : enemies) {
                double dx = enemy.posX - bx;
                double dz = enemy.posZ - bz;
                double dist = Math.max(0.2D, Math.sqrt(dx * dx + dz * dz));

                // 造成 25 点破甲伤害
                enemy.attackEntityFrom(blastSource, 25.0F);

                // 附加 15 秒 (300 ticks) 凋零 II
                enemy.addPotionEffect(new PotionEffect(MobEffects.WITHER, 300, 1, false, true));

                // 冲击波强力向外震飞
                enemy.motionX += (dx / dist) * 3.0D;
                enemy.motionY += 1.0D;
                enemy.motionZ += (dz / dist) * 3.0D;
                enemy.velocityChanged = true;
            }
        });
    }
}

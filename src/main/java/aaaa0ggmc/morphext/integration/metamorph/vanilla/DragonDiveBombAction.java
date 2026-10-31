package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 末影龙主动技能：盘旋俯冲强袭 (Dragon Dive Bomb / Charging Player Phase)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.dragon.phase.PhaseChargingPlayer} 俯冲扑杀机理：
 * 1. 【破空俯冲突刺】：以极高速度朝准星视线方向极速俯冲突刺，提供极强位移动力与滞空掌控；
 * 2. 【沿途贯穿撞击】：开启持续 15 Tick 的高危撞击碾压判定，对沿途触碰的敌人造成 16 点无视护甲的破甲冲击伤害并暴虐击飞；
 * 3. 【无伤飞行保护】：突刺期间与结束彻底豁免掉落伤害，留下一路紫曜龙息与异界传送门拖尾。
 */
public class DragonDiveBombAction implements IAction, ICooldownAction {
    public static final String ID = "dragon_dive";
    public static final int COOLDOWN = 200; // 10 秒冷却

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
        Vec3d look = entity.getLookVec();

        // 1. 赋予极速俯冲初动量
        entity.motionX = look.x * 2.4D;
        entity.motionY = look.y * 1.8D;
        entity.motionZ = look.z * 2.4D;
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        // 2. 霸气龙啸与破空音效
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.PLAYERS, 2.5F, 1.1F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_FIREWORK_LAUNCH, SoundCategory.PLAYERS, 2.0F, 0.6F);

        Set<Integer> hitEntityIds = new HashSet<>();
        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity).setDamageBypassesArmor()
                : DamageSource.causeMobDamage(entity).setDamageBypassesArmor();

        // 3. 开启持续 15 Ticks (0.75秒) 俯冲高能撞击判定
        ServerActionScheduler.scheduleTicker(15, tickLeft -> {
            if (entity.isDead || entity.world == null) {
                return;
            }

            entity.fallDistance = 0.0F;
            World currentWorld = entity.world;

            // 轨迹粒子拖尾
            if (currentWorld instanceof WorldServer) {
                WorldServer ws = (WorldServer) currentWorld;
                ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, entity.posX, entity.posY + 0.8D, entity.posZ,
                        8, 0.4D, 0.4D, 0.4D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.PORTAL, entity.posX, entity.posY + 0.5D, entity.posZ,
                        6, 0.3D, 0.3D, 0.3D, 0.1D);
            }

            // 检索身前 2.5 格碰撞箱敌人
            AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(1.5D, 1.2D, 1.5D);
            List<EntityLivingBase> list = currentWorld.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                    e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

            for (EntityLivingBase hit : list) {
                if (hitEntityIds.add(hit.getEntityId())) {
                    // 造成 16 点破甲冲击重击
                    hit.attackEntityFrom(source, 16.0F);

                    // 强力撞飞
                    hit.motionX += look.x * 1.6D;
                    hit.motionY += 0.7D;
                    hit.motionZ += look.z * 1.6D;
                    hit.velocityChanged = true;

                    currentWorld.playSound(null, hit.posX, hit.posY, hit.posZ,
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4F, 1.2F);

                    if (currentWorld instanceof WorldServer) {
                        ((WorldServer) currentWorld).spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                                hit.posX, hit.posY + 1.0D, hit.posZ, 2, 0.2D, 0.2D, 0.2D, 0.0D);
                    }
                }
            }
        }, () -> {
            entity.fallDistance = 0.0F;
        });
    }
}

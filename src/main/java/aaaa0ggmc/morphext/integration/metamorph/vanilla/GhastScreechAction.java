package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 恶魂主动技能：怨念哀鸣激波 (Ghast Screech & Shockwave)。
 * <p>
 * 深度融合下界霸主的空灵尖叫与气流偏折机制：
 * 1. 发出震撼下界的刺耳哀鸣 {@link SoundEvents#ENTITY_GHAST_SCREAM}；
 * 2. 悬停气流提升：给予施法者向上攀升的浮空推力，维持制空权；
 * 3. 弹幕气流偏折与火球反弹判定：检测周围 8 格范围内的投掷物、箭矢以及恶魂火球，
 *    以激波将其沿扩散方向反冲弹回（对 {@link EntityFireball} 重新指定攻击发起者与加速度向量）；
 * 4. 范围音波震荡：将周围 8 格内的敌对生物剧烈击退，并附加 4 秒虚弱与迟缓。
 */
public class GhastScreechAction implements IAction, ICooldownAction {
    public static final String ID = "ghast_screech";
    public static final int COOLDOWN = 120; // 6 秒

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

        // 1. 张开巨口触发发怒形态
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityGhast) {
                ((EntityGhast) inner).setAttacking(true);
            }
        }

        // 2. 气流上升浮空悬停升力
        entity.motionY = Math.max(entity.motionY + 0.38D, 0.42D);
        entity.velocityChanged = true;
        entity.fallDistance = 0.0F;

        // 3. 播放悠扬高亢的恶魂尖啸
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.PLAYERS, 3.0F, 1.0F);

        // 4. 激波扩散范围检测
        double radius = 8.0D;
        AxisAlignedBB area = entity.getEntityBoundingBox().grow(radius, radius * 0.6D, radius);

        // 5. 偏折与反弹飞行道具（火球反弹判定、箭矢弹飞）
        List<Entity> projectiles = world.getEntitiesWithinAABB(Entity.class, area,
                e -> e != null && e.isEntityAlive() && (e instanceof IProjectile || e instanceof EntityFireball));

        for (Entity proj : projectiles) {
            double dx = proj.posX - entity.posX;
            double dy = proj.posY - (entity.posY + (double) entity.getEyeHeight());
            double dz = proj.posZ - entity.posZ;
            Vec3d dir = new Vec3d(dx, dy, dz).normalize();

            if (proj instanceof EntityFireball) {
                EntityFireball fb = (EntityFireball) proj;
                fb.shootingEntity = entity;
                fb.accelerationX = dir.x * 0.18D;
                fb.accelerationY = dir.y * 0.18D;
                fb.accelerationZ = dir.z * 0.18D;
                fb.velocityChanged = true;
            } else {
                proj.motionX = dir.x * 1.4D;
                proj.motionY = Math.max(dir.y * 1.0D, 0.25D);
                proj.motionZ = dir.z * 1.4D;
                proj.velocityChanged = true;
            }
        }

        // 6. 范围敌对生物击退与声波震慑
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, area,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            double dx = target.posX - entity.posX;
            double dz = target.posZ - entity.posZ;
            Vec3d pushDir = new Vec3d(dx, 0.0D, dz).normalize();

            target.attackEntityFrom(source, 3.0F);
            target.motionX += pushDir.x * 1.1D;
            target.motionY += 0.35D;
            target.motionZ += pushDir.z * 1.1D;
            target.velocityChanged = true;

            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 80, 0));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
        }

        // 7. 环形激波粒子扩散效果
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            for (int deg = 0; deg < 360; deg += 15) {
                double rad = Math.toRadians(deg);
                double px = entity.posX + Math.cos(rad) * 3.5D;
                double pz = entity.posZ + Math.sin(rad) * 3.5D;
                ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, px, entity.posY + 1.0D, pz, 1, 0, 0, 0, 0.0);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, px, entity.posY + 0.5D, pz, 2, 0.2, 0.2, 0.2, 0.02);
            }
        }

        // 8. 15 Tick 后闭嘴
        ServerActionScheduler.schedule(15, () -> {
            if (morph instanceof EntityMorph) {
                EntityLivingBase inner = ((EntityMorph) morph).getEntity();
                if (inner instanceof EntityGhast) {
                    ((EntityGhast) inner).setAttacking(false);
                }
            }
        });
    }
}

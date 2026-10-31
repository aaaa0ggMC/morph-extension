package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 凋灵副头主动技能：双子追踪蓝色无敌凋灵首 (Wither Dual Blue Skulls)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.EntityWither} 左右副头发射蓝色无敌凋灵首的核心机制：
 * 1. 【三头机理左右副头连射】：左副头与右副头同时觉醒，双向齐射两枚纯正的蓝色无敌凋灵首 (invulnerable = true)；
 * 2. 【智能索敌与轨道追踪】：自动检索准星前方 32 格内的敌对目标并导向追踪；若无目标则呈 20 度扇面交错呼啸轰出；
 * 3. 【无敌蓝色特性】：蓝色凋灵首在原版中拥有更高的破防与破坏潜能，具有闪耀的蓝色幽火微粒拖尾；
 * 4. 【安全发射前移】：安全前移 2.0 格发射点，保障完全豁免自身。
 */
public class WitherBlueSkullAction implements IAction, ICooldownAction {
    public static final String ID = "wither_blue_skull";
    public static final int COOLDOWN = 140; // 7 秒冷却

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

        entity.swingArm(EnumHand.OFF_HAND);
        entity.motionX -= look.x * 0.12D;
        entity.motionZ -= look.z * 0.12D;
        entity.velocityChanged = true;

        // 1. 寻找准星前方 32 格内可追踪的目标
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.25D);

        // 2. 计算左右侧向向量 (横向偏移 0.8 格模拟左副头与右副头)
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01D) {
            right = new Vec3d(1, 0, 0);
        }

        Vec3d centerSpawn = VanillaActionHelper.getSafeSpawnPos(entity, 2.0D);
        Vec3d leftSpawn = centerSpawn.subtract(right.scale(0.85D));
        Vec3d rightSpawn = centerSpawn.add(right.scale(0.85D));

        // 3. 计算左头与右头的发射加速度向量
        Vec3d leftDir;
        Vec3d rightDir;

        if (target != null) {
            Vec3d targetPos = new Vec3d(target.posX, target.posY + target.getEyeHeight() * 0.5D, target.posZ);
            leftDir = targetPos.subtract(leftSpawn).normalize();
            rightDir = targetPos.subtract(rightSpawn).normalize();
        } else {
            // 扇形微微向外散射
            leftDir = look.subtract(right.scale(0.18D)).normalize();
            rightDir = look.add(right.scale(0.18D)).normalize();
        }

        // 4. 生成两枚蓝色无敌凋灵首 (setInvulnerable = true)
        EntityWitherSkull leftSkull = new EntityWitherSkull(world, entity,
                leftDir.x * 0.14D, leftDir.y * 0.14D, leftDir.z * 0.14D);
        leftSkull.setPosition(leftSpawn.x, leftSpawn.y, leftSpawn.z);
        leftSkull.setInvulnerable(true); // 蓝色无敌形态！
        world.spawnEntity(leftSkull);

        EntityWitherSkull rightSkull = new EntityWitherSkull(world, entity,
                rightDir.x * 0.14D, rightDir.y * 0.14D, rightDir.z * 0.14D);
        rightSkull.setPosition(rightSpawn.x, rightSpawn.y, rightSpawn.z);
        rightSkull.setInvulnerable(true); // 蓝色无敌形态！
        world.spawnEntity(rightSkull);

        // 5. 原版 1024 广播事件与高音调发射音效
        world.playEvent(null, 1024, new BlockPos(entity), 0);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 2.0F, 1.25F);

        // 6. 蓝色神圣幽火微粒
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, leftSpawn.x, leftSpawn.y, leftSpawn.z,
                    12, 0.2D, 0.2D, 0.2D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, rightSpawn.x, rightSpawn.y, rightSpawn.z,
                    12, 0.2D, 0.2D, 0.2D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, centerSpawn.x, centerSpawn.y, centerSpawn.z,
                    10, 0.3D, 0.3D, 0.3D, 0.05D);
        }
    }
}

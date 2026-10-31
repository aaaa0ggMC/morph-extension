package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Random;

/**
 * 烈焰人主动技能：蓄能连珠火球 3 连发 (Blaze 3-Shot Burst)。
 * <p>
 * 严谨复刻原版 {@link net.minecraft.entity.monster.EntityBlaze.AIFireballAttack} 经典三连发节奏：
 * 1. 激燃蓄能：启动烈焰护体发光状态 {@link EntityBlaze#setOnFire(boolean)}，并伴随炽热轰鸣；
 * 2. 经典 6 Tick 间隔（0.3秒）连珠齐射：
 *    - 第 1 发：准星直射，附带主手施法动作与微后坐力反冲；
 *    - 第 2 发：精准追击（6 Tick 后发射）；
 *    - 第 3 发：终结轰炸（12 Tick 后发射，伴随强化火烟粒子）；
 * 3. 发射后熄灭蓄能状态，进入 3 秒快速冷却。
 */
public class BlazeFireballAction implements IAction, ICooldownAction {
    public static final String ID = "blaze_fireball";
    public static final int COOLDOWN = 60; // 3 秒冷却

    private final Random rand = new Random();

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

        // 1. 开启烈焰人全身燃烧充能状态
        setBlazeOnFire(morph, true);

        // 2. 燃烧呼啸声
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.PLAYERS, 1.2F, 1.0F);

        // 第 1 发：立即发射
        fireSingleBall(entity, 0.0D);

        // 第 2 发：6 Tick 后发射
        ServerActionScheduler.schedule(6, () -> {
            if (entity.isDead || entity.world == null) {
                setBlazeOnFire(morph, false);
                return;
            }
            fireSingleBall(entity, (rand.nextDouble() - 0.5D) * 0.02D);
        });

        // 第 3 发：12 Tick 后发射，并发射后复位燃烧状态
        ServerActionScheduler.schedule(12, () -> {
            try {
                if (!entity.isDead && entity.world != null) {
                    fireSingleBall(entity, (rand.nextDouble() - 0.5D) * 0.03D);
                }
            } finally {
                setBlazeOnFire(morph, false);
            }
        });
    }

    private void fireSingleBall(EntityLivingBase entity, double spread) {
        World world = entity.world;
        Vec3d look = entity.getLookVec();

        entity.swingArm(EnumHand.MAIN_HAND);

        // 射击微反冲
        entity.motionX -= look.x * 0.06D;
        entity.motionZ -= look.z * 0.06D;
        entity.velocityChanged = true;

        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.4D);

        double accelX = look.x * 0.16D + spread;
        double accelY = look.y * 0.16D;
        double accelZ = look.z * 0.16D + spread;

        EntitySmallFireball fireball = new EntitySmallFireball(world, entity, accelX, accelY, accelZ);
        fireball.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        world.spawnEntity(fireball);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.FLAME, spawnPos.x, spawnPos.y, spawnPos.z, 12, 0.12, 0.12, 0.12, 0.04);
            ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, spawnPos.x, spawnPos.y, spawnPos.z, 6, 0.1, 0.1, 0.1, 0.02);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5F, 1.0F + (rand.nextFloat() - 0.5F) * 0.2F);
    }

    private void setBlazeOnFire(AbstractMorph morph, boolean onFire) {
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityBlaze) {
                ((EntityBlaze) inner).setOnFire(onFire);
            }
        }
    }
}

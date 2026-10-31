package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 雪傀儡主动技能：暴雪机枪扫射 (Snowman Blizzard Machine Gun)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntitySnowman} 极速雪球压制体系：
 * 1. 【高频机枪扫射】：利用调度器在 20 ticks 内连续倾泻 10 发高能压制雪球；
 * 2. 【强力压制机制】：每枚特制暴雪雪球命中生物时造成 2 点伤害、迟缓 II (60 ticks) 与连续冲击击退；
 * 3. 【风雪呼啸视听】：伴随高密度飞雪微粒与密集的雪球出膛破空声。
 */
public class SnowmanBlizzardAction implements IAction, ICooldownAction {
    public static final String ID = "snowman_blizzard";
    public static final int COOLDOWN = 120; // 6 秒冷却 (120 ticks)
    public static final String TAG_BLIZZARD = "SnowmanBlizzard";

    private static boolean listenerRegistered = false;

    public SnowmanBlizzardAction() {
        registerListener();
    }

    private static synchronized void registerListener() {
        if (!listenerRegistered) {
            listenerRegistered = true;
            MinecraftForge.EVENT_BUS.register(new BlizzardImpactListener());
        }
    }

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }

        // 连续 10 发高速雪球机枪扫射，每 2 ticks 一发，总耗时 18 ticks
        for (int i = 0; i < 10; i++) {
            int delay = i * 2;

            ServerActionScheduler.schedule(delay, () -> {
                if (entity == null || entity.world == null || !entity.isEntityAlive() || entity.world.isRemote) {
                    return;
                }

                World world = entity.world;
                entity.swingArm(EnumHand.MAIN_HAND);
                Vec3d look = entity.getLookVec();

                // 0.9 格安全发射起点
                Vec3d safeSpawn = VanillaActionHelper.getSafeSpawnPos(entity, 0.9D);

                EntitySnowball snowball = new EntitySnowball(world, entity);
                snowball.addTag(TAG_BLIZZARD);
                snowball.setPosition(safeSpawn.x, safeSpawn.y, safeSpawn.z);
                snowball.shoot(look.x, look.y, look.z, 2.2F, 3.0F);
                world.spawnEntity(snowball);

                // 出膛飞雪微粒
                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.SNOW_SHOVEL,
                            safeSpawn.x, safeSpawn.y, safeSpawn.z, 6, 0.15D, 0.15D, 0.15D, 0.05D);
                }

                // 连续机枪音效（音调微随机）
                float pitch = 1.1F + (world.rand.nextFloat() * 0.3F);
                world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_SNOWMAN_SHOOT, SoundCategory.PLAYERS, 1.3F, pitch);
            });
        }
    }

    /**
     * 暴雪特制雪球命中击退与迟缓监听器。
     */
    public static class BlizzardImpactListener {
        @SubscribeEvent
        public void onProjectileImpact(ProjectileImpactEvent.Throwable event) {
            if (event.getEntity() instanceof EntitySnowball && event.getEntity().getTags().contains(TAG_BLIZZARD)) {
                EntitySnowball snowball = (EntitySnowball) event.getEntity();
                RayTraceResult ray = event.getRayTraceResult();

                if (ray != null && ray.entityHit instanceof EntityLivingBase) {
                    EntityLivingBase target = (EntityLivingBase) ray.entityHit;
                    EntityLivingBase thrower = snowball.getThrower();

                    DamageSource source = thrower instanceof EntityPlayer
                            ? DamageSource.causePlayerDamage((EntityPlayer) thrower)
                            : (thrower != null ? DamageSource.causeMobDamage(thrower) : DamageSource.GENERIC);

                    // 造成 2 点压制伤害
                    target.attackEntityFrom(source, 2.0F);

                    // 施加迟缓 II (60 ticks = 3 秒)
                    target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));

                    // 持续压制击退
                    Vec3d motion = new Vec3d(snowball.motionX, snowball.motionY, snowball.motionZ);
                    double len = motion.length();
                    if (len > 0.001D) {
                        target.knockBack(thrower, 0.45F, -motion.x / len, -motion.z / len);
                        target.velocityChanged = true;
                    }

                    // 命中飞雪爆破粒子
                    if (target.world instanceof WorldServer) {
                        ((WorldServer) target.world).spawnParticle(EnumParticleTypes.SNOWBALL,
                                target.posX, target.posY + target.height * 0.5D, target.posZ,
                                12, 0.25D, 0.25D, 0.25D, 0.1D);
                    }
                }
            }
        }
    }
}

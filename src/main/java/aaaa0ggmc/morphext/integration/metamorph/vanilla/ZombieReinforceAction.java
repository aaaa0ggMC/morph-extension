package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 尸潮增援 (Zombie Reinforcements)。
 * <p>
 * 依据 {@code EntityZombie} 的 SPAWN_REINFORCEMENTS / SummonAidEvent（HARD 难度受伤召唤增援）——
 * "呼唤同伴"是僵尸最著名的代码行为。这里是它的可控主动版：
 * 咆哮呼唤 2-3 只僵尸增援，并集火宿主准星指向的目标（VanillaActionHelper.findTarget 语义）。
 */
public class ZombieReinforceAction implements IAction, ICooldownAction {
    public static final String ID = "zombie_reinforce";
    public static final String ALIAS_ID = "morphext:zombie_reinforce";
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

        // 咆哮呼唤
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.PLAYERS, 1.6F, 0.65F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.PLAYERS, 0.8F, 1.1F);

        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.35D);

        // 召唤 2-3 只僵尸增援（SummonAid 的可控版）
        int count = 2 + world.rand.nextInt(2);
        for (int i = 0; i < count; i++) {
            float angle = (float) Math.toRadians(entity.rotationYaw) + (i - (count - 1) * 0.5F) * 0.9F;
            double sx = entity.posX + MathHelper.cos(angle) * 2.0D;
            double sy = entity.posY;
            double sz = entity.posZ + MathHelper.sin(angle) * 2.0D;

            EntityZombie zombie = new EntityZombie(world);
            zombie.setLocationAndAngles(sx, sy, sz, entity.rotationYaw, 0.0F);
            zombie.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(entity)), null);
            if (target != null) {
                zombie.setAttackTarget(target);
            }
            world.spawnEntity(zombie);
        }

        // 增援登场烟尘
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            for (int i = 0; i < count; i++) {
                float angle = (float) Math.toRadians(entity.rotationYaw) + (i - (count - 1) * 0.5F) * 0.9F;
                double px = entity.posX + MathHelper.cos(angle) * 2.0D;
                double pz = entity.posZ + MathHelper.sin(angle) * 2.0D;
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, px, entity.posY + 0.2D, pz, 10, 0.3D, 0.2D, 0.3D, 0.02D);
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, px, entity.posY + 0.5D, pz, 6, 0.3D, 0.3D, 0.3D, 0.02D);
            }
        }
    }
}

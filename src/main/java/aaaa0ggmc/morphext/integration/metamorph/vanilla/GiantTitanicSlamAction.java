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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 远古巨人泰坦震地碎击 (Giant Titanic Slam Action):
 * 巨型足部重踏地面，震撼方圆 10 格，碎石横飞并将周围敌人猛烈震飞至高空，造成 20 点破甲物理重创！
 */
public class GiantTitanicSlamAction implements IAction, ICooldownAction {
    public static final String ID = "giant_titanic_slam";
    public static final int COOLDOWN = 400; // 20 秒（20 伤 10 格 AoE + 击飞，冷却红线 ≥200t）

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.8F, 0.6F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 2.0F, 0.5F);

        if (world.isRemote) return;

        double radius = 10.0D;
        AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(radius, 4.0D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                e -> e != null && e.isEntityAlive() && !e.equals(entity));

        DamageSource src = entity instanceof EntityPlayer ?
                DamageSource.causePlayerDamage((EntityPlayer) entity) : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            target.attackEntityFrom(src, 20.0F);
            // 巨力击飞抛向高空
            target.motionY += 0.95D;
            target.velocityChanged = true;
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, entity.posX, entity.posY + 0.5D, entity.posZ, 2, 0.5D, 0.2D, 0.5D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, entity.posX, entity.posY + 0.2D, entity.posZ, 80, 4.0D, 0.2D, 4.0D, 0.1D, 1);
        }
    }
}

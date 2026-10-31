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
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 末影螨主动技能：异界寄生撕咬 (Endermite Parasitic Bite)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.monster.EntityEndermite} 贴身撕咬与末影寄生机制：
 * 1. 【扑击撕咬】：向前扑咬前方 2.5 格内的单一目标，造成 5 点物理撕咬伤害；
 * 2. 【异界毒素迟滞】：对受击者附加 4 秒缓慢 II 与虚弱减益；
 * 3. 【末影汲取反哺】：汲取目标的生命精气，瞬间为自身恢复 2 点生命值；
 * 4. 【视听沉浸反馈】：伴随末影螨啃咬音效与紫色撕裂微粒。
 */
public class EndermiteBiteAction implements IAction, ICooldownAction {
    public static final String ID = "endermite_bite";
    public static final int COOLDOWN = 50; // 2.5 秒冷却

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

        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX += look.x * 0.25D;
        entity.motionZ += look.z * 0.25D;
        entity.velocityChanged = true;

        double reach = 2.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.2D, reach);
        List<EntityLivingBase> candidates = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        EntityLivingBase hitTarget = null;
        double closestDot = 0.3D;

        for (EntityLivingBase c : candidates) {
            Vec3d toTarget = new Vec3d(c.posX - entity.posX, c.posY - entity.posY, c.posZ - entity.posZ).normalize();
            double dot = look.dotProduct(toTarget);
            if (dot > closestDot) {
                closestDot = dot;
                hitTarget = c;
            }
        }

        if (hitTarget != null) {
            DamageSource src = entity instanceof EntityPlayer
                    ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                    : DamageSource.causeMobDamage(entity);

            hitTarget.attackEntityFrom(src, 5.0F);
            hitTarget.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1, false, true));
            hitTarget.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 80, 0, false, true));

            // 吸取 2 点生命
            entity.heal(2.0F);

            world.playSound(null, hitTarget.posX, hitTarget.posY, hitTarget.posZ,
                    SoundEvents.ENTITY_ENDERMITE_HURT, SoundCategory.PLAYERS, 1.4F, 1.2F);

            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                ws.spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                        hitTarget.posX, hitTarget.posY + hitTarget.height * 0.5D, hitTarget.posZ, 5, 0.2D, 0.2D, 0.2D, 0.1D);
                ws.spawnParticle(EnumParticleTypes.PORTAL,
                        hitTarget.posX, hitTarget.posY + 0.5D, hitTarget.posZ, 15, 0.3D, 0.3D, 0.3D, 0.1D);
            }
        } else {
            // 空咬音效
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 1.0F, 1.5F);
        }
    }
}

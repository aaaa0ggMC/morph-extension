package aaaa0ggmc.morphext.integration.metamorph.vanilla;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 驴/骡暴怒后蹬蹄击 (Donkey Rear Kick Action):
 * 扬起铁蹄回旋爆踢，对身后及身侧 3.5 格范围敌人造成 10 点物理重击并爆发强力击退与 3 秒迟缓眩晕。
 */
public class DonkeyRearKickAction implements IAction {

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_DONKEY_ANGRY, SoundCategory.NEUTRAL, 1.3F, 0.9F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2F, 0.8F);

        if (world.isRemote) return;

        Vec3d look = entity.getLookVec();
        // 判定朝身后踢
        Vec3d kickDir = new Vec3d(-look.x, 0.2D, -look.z).normalize();

        AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(2.5D, 1.0D, 2.5D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                e -> e != null && e.isEntityAlive() && !e.equals(entity));

        DamageSource src = entity instanceof EntityPlayer ?
                DamageSource.causePlayerDamage((EntityPlayer) entity) : DamageSource.causeMobDamage(entity);

        for (EntityLivingBase target : targets) {
            target.attackEntityFrom(src, 10.0F);
            target.knockBack(entity, 1.8F, -kickDir.x, -kickDir.z);
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 3));
        }

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL,
                    entity.posX - look.x * 1.2D, entity.posY + 0.6D, entity.posZ - look.z * 1.2D,
                    15, 0.4D, 0.3D, 0.4D, 0.05D);
        }
    }
}

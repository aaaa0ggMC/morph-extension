package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 流髑主动技能：极寒缓慢箭齐射 (Stray Frost Arrow Volley)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntityStray} 极寒冰霜射术机理：
 * 1. 【安全发射起点】：前移 1.2 格，彻底消除模型体积与自身移动带来的碰撞误伤；
 * 2. 【极寒冰霜箭雨】：向准星视线方向呈锥形扇面激射 3 发附带深度缓慢 II (300 ticks = 15秒) 的药水箭 (EntityTippedArrow)；
 * 3. 【极寒视听沉浸】：箭矢伴随大量飞雪裂片与破冰脆响，附加后坐力微震手感。
 */
public class StrayFrostArrowAction implements IAction, ICooldownAction {
    public static final String ID = "stray_frost_arrow";
    public static final int COOLDOWN = 100; // 5 秒冷却 (100 ticks)

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

        // 1. 主手挥击动作与微弱后坐力推力
        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX -= look.x * 0.08D;
        entity.motionZ -= look.z * 0.08D;
        entity.velocityChanged = true;

        // 2. 计算 1.2 格安全前向发射起点
        Vec3d safeSpawn = VanillaActionHelper.getSafeSpawnPos(entity, 1.2D);

        // 3. 锥形扇面发射 3 发极寒滞缓药水箭（水平偏角 -4.5°, 0°, +4.5°）
        for (int i = 0; i < 3; i++) {
            float yawOffset = (i - 1) * 4.5F;

            EntityTippedArrow arrow = new EntityTippedArrow(world, entity);
            arrow.setPosition(safeSpawn.x, safeSpawn.y, safeSpawn.z);
            arrow.shoot(entity, entity.rotationPitch, entity.rotationYaw + yawOffset, 0.0F, 2.2F, 1.0F);
            arrow.setDamage(3.0D);

            // 附带深度缓慢 II (300 ticks = 15秒)
            arrow.addEffect(new PotionEffect(MobEffects.SLOWNESS, 300, 1));
            arrow.pickupStatus = EntityArrow.PickupStatus.DISALLOWED;
            world.spawnEntity(arrow);
        }

        // 4. 极寒冰霜微粒爆发
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, safeSpawn.x, safeSpawn.y, safeSpawn.z, 25, 0.4D, 0.3D, 0.4D, 0.08D);
            ws.spawnParticle(EnumParticleTypes.SNOWBALL, safeSpawn.x, safeSpawn.y, safeSpawn.z, 15, 0.3D, 0.2D, 0.3D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, safeSpawn.x, safeSpawn.y, safeSpawn.z, 10, 0.3D, 0.3D, 0.3D, 0.0D);
        }

        // 5. 复合环境音效：骷髅射箭 + 坚冰破碎 + 流髑空灵战鸣
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.3F, 0.85F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8F, 1.7F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_STRAY_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.2F);
    }
}

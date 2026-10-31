package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 恶魂主动技能：巨型恶魂高爆火球 (Ghast Fireball)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.monster.EntityGhast} 底层机制：
 * 1. 蓄力前置警告：播放尖啸 {@link SoundEvents#ENTITY_GHAST_WARN}，并在嘴部开启动画 {@link EntityGhast#setAttacking(boolean)}；
 * 2. 蓄力聚能粒子：嘴部前方凝聚火焰与浓烟粒子，并带有微量悬停准备减速；
 * 3. 10 Tick 精准蓄力（0.5 秒，还原源码 AIFireballAttack 节奏）；
 * 4. 炮击发射：射出一枚威力巨大 (explosionPower = 2) 的 {@link EntityLargeFireball}；
 * 5. 安全距离前移：发射点前移 3.5 格，彻底避免庞大 4x4 碰撞箱导致的 self-hit 自身炸毁；
 * 6. 后坐力反冲：巨炮发射给施法者带来真实反冲推力；
 * 7. 施法结束复位：还原嘴部闭合状态。
 */
public class GhastFireballAction implements IAction, ICooldownAction {
    public static final String ID = "ghast_fireball";
    public static final int COOLDOWN = 140; // 7 秒冷却，平衡强力爆破与连击体验

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

        // 1. 设置恶魂张嘴发怒动画 (setAttacking)
        if (morph instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) morph).getEntity();
            if (inner instanceof EntityGhast) {
                ((EntityGhast) inner).setAttacking(true);
            }
        }

        // 2. 播放恶魂蓄能尖锐警告声
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_GHAST_WARN, SoundCategory.PLAYERS, 2.5F, 1.0F);

        // 3. 炮口蓄力汇聚粒子
        Vec3d chargePos = VanillaActionHelper.getSafeSpawnPos(entity, 3.5D);
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, chargePos.x, chargePos.y, chargePos.z, 15, 0.3, 0.3, 0.3, 0.05);
            ws.spawnParticle(EnumParticleTypes.FLAME, chargePos.x, chargePos.y, chargePos.z, 20, 0.3, 0.3, 0.3, 0.08);
        }

        // 4. 蓄力轻微悬空制动，提供沉稳炮击手感
        entity.motionX *= 0.5D;
        entity.motionZ *= 0.5D;
        entity.velocityChanged = true;

        // 5. 10 Ticks (0.5秒) 蓄力完成后出膛发射
        ServerActionScheduler.schedule(10, () -> {
            if (entity.isDead || entity.world == null) {
                return;
            }

            World launchWorld = entity.world;
            Vec3d fireLook = entity.getLookVec();
            Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 3.5D);

            entity.swingArm(EnumHand.MAIN_HAND);

            // 强劲的巨炮后坐力反冲
            entity.motionX -= fireLook.x * 0.28D;
            entity.motionY -= fireLook.y * 0.18D;
            entity.motionZ -= fireLook.z * 0.28D;
            entity.velocityChanged = true;

            // 恶魂巨炮发射音效
            launchWorld.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 2.5F, 1.0F);

            // 生成大型恶魂火球
            EntityLargeFireball fireball = new EntityLargeFireball(launchWorld, entity,
                    fireLook.x * 0.12D, fireLook.y * 0.12D, fireLook.z * 0.12D);
            fireball.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
            fireball.explosionPower = 2;
            launchWorld.spawnEntity(fireball);

            // 枪口爆炎与黑烟粒子喷发
            if (launchWorld instanceof WorldServer) {
                WorldServer ws = (WorldServer) launchWorld;
                ws.spawnParticle(EnumParticleTypes.FLAME, spawnPos.x, spawnPos.y, spawnPos.z, 30, 0.4, 0.4, 0.4, 0.1);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, spawnPos.x, spawnPos.y, spawnPos.z, 20, 0.3, 0.3, 0.3, 0.05);
                ws.spawnParticle(EnumParticleTypes.LAVA, spawnPos.x, spawnPos.y, spawnPos.z, 8, 0.2, 0.2, 0.2, 0.0);
            }

            // 发射后嘴部闭合复位
            if (morph instanceof EntityMorph) {
                EntityLivingBase inner = ((EntityMorph) morph).getEntity();
                if (inner instanceof EntityGhast) {
                    ((EntityGhast) inner).setAttacking(false);
                }
            }
        });
    }
}

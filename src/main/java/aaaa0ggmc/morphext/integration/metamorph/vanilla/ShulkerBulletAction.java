package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 潜影贝主动技能：追踪潜影弹 (Shulker Bullet) - 自由释放升级版。
 * 1. 优先自动追踪视线内的存活生物；
 * 2. 若前方无生物，绝不哑火！顺着准星朝视线落点发射，直奔撞击点爆发漂浮魔法；
 * 3. 发射起点安全前移 1.6 格并赋予外冲初速度，彻底防止撞击施法者自身 (Self-Hit 防御)；
 * 4. 始终触发主手施法动作与发射音画，手感丝滑。
 */
public class ShulkerBulletAction implements IAction, ICooldownAction {
    public static final String ID = "shulker_bullet";
    public static final int COOLDOWN = 70; // 3.5 秒

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
        Vec3d eyes = entity.getPositionEyes(1.0F);

        // 发射起点大幅前移 1.6 格，完全超越玩家碰撞箱范围
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.6D);

        // 挥动手臂
        entity.swingArm(EnumHand.MAIN_HAND);

        // 智能索敌
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.2D);
        Entity bulletTarget = target;

        if (bulletTarget == null) {
            // 自由射击：朝准星视线方向延伸 32 格进行光线追踪
            Vec3d reach = eyes.add(look.x * 32.0D, look.y * 32.0D, look.z * 32.0D);
            RayTraceResult hit = world.rayTraceBlocks(eyes, reach, false, true, false);
            Vec3d targetPos = (hit != null && hit.hitVec != null) ? hit.hitVec : reach;

            // 创建隐形短暂追踪锚点
            EntityAreaEffectCloud anchor = new EntityAreaEffectCloud(world, targetPos.x, targetPos.y, targetPos.z);
            anchor.setRadius(0.0F);
            anchor.setDuration(60); // 3 秒后自动消散
            world.spawnEntity(anchor);
            bulletTarget = anchor;
        }

        EntityShulkerBullet bullet = new EntityShulkerBullet(world, entity, bulletTarget, EnumFacing.Axis.Y);
        bullet.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);

        // 赋予前冲微动力，避开上升期碰撞
        bullet.motionX = look.x * 0.25D;
        bullet.motionY = look.y * 0.25D + 0.08D;
        bullet.motionZ = look.z * 0.25D;

        world.spawnEntity(bullet);

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.PLAYERS, 1.8F, 1.0F);
    }
}

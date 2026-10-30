package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 潜影贝主动技能：追踪潜影弹 (Shulker Bullet)。
 * 发射一枚自动追踪视线范围内敌人的潜影弹，造成魔法伤害与漂浮效果。
 * 智能索敌且设置安全发射偏移，避免误锁自身或自我碰撞。
 */
public class ShulkerBulletAction implements IAction, ICooldownAction {
    public static final String ID = "shulker_bullet";
    public static final int COOLDOWN = 80; // 4 秒

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
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 32.0D, 0.2D);

        if (target != null) {
            // 计算安全前向偏移发射点（1.2 格），绝不击中自身
            Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.2D);

            EntityShulkerBullet bullet = new EntityShulkerBullet(world, entity, target, EnumFacing.Axis.Y);
            bullet.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
            world.spawnEntity(bullet);

            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.PLAYERS, 1.8F, 1.0F);
        } else {
            // 无可用锁定目标时发出低鸣失灵声
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.2F);
        }
    }
}

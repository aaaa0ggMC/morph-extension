package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityDragonFireball;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 末影龙主动技能：终末龙息弹 (Dragon Breath Fireball)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.EntityDragon} 与 {@link EntityDragonFireball} 终极龙息机理：
 * 1. 【安全发射起点】：将发射起点前移 3.5 格，彻底消除巨型模型带来的碰撞自伤死穴；
 * 2. 【原版龙息弹生成】：朝准星视线方向射出一枚 {@link EntityDragonFireball}，并在撞击地面或实体时
 *    自动唤醒原版紫雾滞留云 (EntityAreaEffectCloud + 瞬间伤害 II 剧毒侵蚀)；
 * 3. 【视听沉浸网络事件】：触发原版 1017 龙息音效事件、大范围龙吟与后坐力推力。
 */
public class DragonBreathAction implements IAction, ICooldownAction {
    public static final String ID = "dragon_breath";
    public static final int COOLDOWN = 100; // 5 秒冷却

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

        // 1. 手部挥动与反冲后坐力
        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX -= look.x * 0.16D;
        entity.motionZ -= look.z * 0.16D;
        entity.velocityChanged = true;

        // 2. 计算 3.5 格安全前移发射起点
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 3.5D);

        // 3. 实例化并生成原版末影龙息弹
        EntityDragonFireball fireball = new EntityDragonFireball(world, entity,
                look.x * 0.12D, look.y * 0.12D, look.z * 0.12D);
        fireball.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        world.spawnEntity(fireball);

        // 4. 原版 1017 网络事件广播与专属音效
        world.playEvent(null, 1017, new BlockPos(entity), 0);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_ENDERDRAGON_SHOOT, SoundCategory.PLAYERS, 2.4F, 1.0F);

        // 5. 龙息出膛微粒汇聚与喷薄
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, spawnPos.x, spawnPos.y, spawnPos.z,
                    35, 0.4D, 0.4D, 0.4D, 0.08D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, spawnPos.x, spawnPos.y, spawnPos.z,
                    15, 0.2D, 0.2D, 0.2D, 0.5D);
        }
    }
}

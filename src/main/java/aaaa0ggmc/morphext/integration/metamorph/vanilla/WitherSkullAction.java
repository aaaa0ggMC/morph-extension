package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 凋灵主头主动技能：黑色高爆凋灵首 (Wither Skull Shot)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.EntityWither} 主头发射机制：
 * 1. 【安全发射起点】：前移 2.0 格发射点，彻底消除与凋灵宽大碰撞箱的自爆重叠判定；
 * 2. 【黑色高爆凋灵首】：朝准星视线方向射出一枚标准黑色凋灵首 (invulnerable = false)，
 *    命中敌人造成爆炸伤害并施加凋零减益；
 * 3. 【原版网络音画事件】：调用原版 1024 发射事件与凋灵开火音效。
 */
public class WitherSkullAction implements IAction, ICooldownAction {
    public static final String ID = "wither_skull";
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

        // 1. 手臂挥舞与轻微后坐力反冲
        entity.swingArm(EnumHand.MAIN_HAND);
        entity.motionX -= look.x * 0.1D;
        entity.motionZ -= look.z * 0.1D;
        entity.velocityChanged = true;

        // 2. 2.0 格安全前移生成点
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 2.0D);

        // 3. 生成主头黑色高爆凋灵首
        EntityWitherSkull skull = new EntityWitherSkull(world, entity,
                look.x * 0.15D, look.y * 0.15D, look.z * 0.15D);
        skull.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        skull.setInvulnerable(false);
        world.spawnEntity(skull);

        // 4. 原版 1024 发射事件与音效
        world.playEvent(null, 1024, new BlockPos(entity), 0);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.8F, 1.0F);

        // 5. 枪口暗黑死气粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, spawnPos.x, spawnPos.y, spawnPos.z,
                    12, 0.2D, 0.2D, 0.2D, 0.04D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, spawnPos.x, spawnPos.y, spawnPos.z,
                    8, 0.0D, 0.0D, 0.0D, 1.0D);
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 远古守卫者主动技能：深海幽魂诅咒 (Elder Curse)。
 * 深度还原原版 EntityElderGuardian.updateAITasks 遗迹诅咒机制：
 * 1. 爆发海底神殿远古诅咒灵压，令周围 18 格内的敌对生物陷入挖掘疲劳 III (Mining Fatigue III 持续 15 秒 / 300 ticks)；
 * 2. 附加虚弱 I 与缓慢 II 状态；
 * 3. 向范围内所有玩家投递 SPacketChangeGameState(10, 0.0F) 并在客户端唤醒经典的远古守卫者全屏幽灵鬼影幻象 (MOB_APPEARANCE)；
 * 4. 伴随深海幽魂低沉咆哮与深海暗涌粒子。
 */
public class ElderCurseAction implements IAction, ICooldownAction {
    public static final String ID = "elder_curse";
    public static final int COOLDOWN = 260; // 13 秒

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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 远古诅咒低鸣音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 2.5F, 1.0F);

        // 诅咒黑水与暗影深海粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SUSPENDED_DEPTH, x, y + 1.0D, z, 60, 2.5D, 1.5D, 2.5D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y + 1.0D, z, 40, 2.0D, 1.2D, 2.0D, 0.1D);
        }

        double radius = 18.0D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(radius, radius * 0.5D, radius);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        for (EntityLivingBase target : targets) {
            // 挖掘疲劳 III (amplifier 2)，持续 300 ticks (15秒)
            target.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 300, 2, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 140, 1, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 140, 0, false, true));

            // 对受诅咒玩家施加原版全屏幽魂鬼影幻象 (SPacketChangeGameState state 10)
            if (target instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP) target;
                playerMP.connection.sendPacket(new SPacketChangeGameState(10, 0.0F));
            }

            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                ws.spawnParticle(EnumParticleTypes.MOB_APPEARANCE,
                        target.posX, target.posY + target.height * 0.5D, target.posZ,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 潜影贝主动技能：漂浮力场 (Shulker Levitation Field)。
 * <p>
 * 依据 {@code EntityShulker} 的漂浮子弹（EntityShulkerBullet 附 LEVITATION）——
 * 把单体漂浮扩为周身力场：令 6 格内敌人短暂漂浮离地（漂浮 II 3 秒），
 * 搭配潜影贝的静止炮台身份形成自保式控场。不造成直接伤害。
 */
public class ShulkerLevitationFieldAction implements IAction, ICooldownAction {
    public static final String ID = "shulker_levitation_field";
    public static final String ALIAS_ID = "morphext:shulker_levitation_field";
    public static final int COOLDOWN = 300; // 15 秒

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
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.PLAYERS, 1.4F, 0.8F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SHULKER_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.3F);

        AxisAlignedBB box = entity.getEntityBoundingBox().grow(6.0D, 3.5D, 6.0D);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));
        for (EntityLivingBase target : targets) {
            // 预检目标免疫：漂浮/缓慢被 DENY 的目标（如潜影贝镜像局）直接跳过（CORE-05）
            PotionEffect levitation = new PotionEffect(MobEffects.LEVITATION, 60, 1, false, true);
            PotionEffect slowness = new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true);
            if (target.isPotionApplicable(levitation)) {
                target.addPotionEffect(levitation);
            }
            if (target.isPotionApplicable(slowness)) {
                target.addPotionEffect(slowness);
            }
        }

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, entity.posX, entity.posY + 0.6D, entity.posZ, 30, 1.2D, 0.8D, 1.2D, 0.05D);
            ws.spawnParticle(EnumParticleTypes.CLOUD, entity.posX, entity.posY + 0.2D, entity.posZ, 16, 1.5D, 0.3D, 1.5D, 0.02D);
        }
    }
}

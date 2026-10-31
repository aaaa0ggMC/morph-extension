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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 恼鬼主动技能：缠怨 (Vex Haunt)。
 * <p>
 * 依据 {@code EntityVex} 的短命狂徒/缠人鬼魂身份——对准星目标降下怨念标记
 * （发光 8 秒 + 虚弱 I），如同被恼鬼缠上般无法摆脱；自身借怨念疾行获得速度 II。
 * 不造成直接伤害。
 */
public class VexHauntAction implements IAction, ICooldownAction {
    public static final String ID = "vex_haunt";
    public static final String ALIAS_ID = "morphext:vex_haunt";
    public static final int COOLDOWN = 200; // 10 秒

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
        entity.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);

        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 16.0D, 0.6D);
        if (target == null) {
            return;
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 1.3F, 1.4F);
        world.playSound(null, target.posX, target.posY, target.posZ,
                SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.PLAYERS, 1.1F, 0.8F);

        target.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 160, 0, false, true));
        target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 80, 1, false, true));

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            Vec3d start = new Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
            Vec3d end = new Vec3d(target.posX, target.posY + target.height * 0.6D, target.posZ);
            VanillaActionHelper.drawParticleLine(ws, start, end, EnumParticleTypes.SPELL_WITCH, 0.4D, 0.02D);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, target.posX, target.posY + target.height * 0.5D, target.posZ, 20, 0.4D, 0.6D, 0.4D, 0.05D);
        }
    }
}

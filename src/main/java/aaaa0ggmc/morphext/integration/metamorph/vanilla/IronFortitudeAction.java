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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 铁傀儡主动技能：铁躯坚固 (Iron Fortitude)。
 * 瞬间收紧钢铁躯体进入防御姿态，获得短暂的抗性提升与抗击退，但因躯体沉重获得短暂减速，攻守平衡。
 */
public class IronFortitudeAction implements IAction, ICooldownAction {
    public static final String ID = "iron_fortitude";
    public static final int COOLDOWN = 140; // 7 秒

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
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 100, 1, false, false));
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 0, false, false));

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT,
                    entity.posX, entity.posY + 1.0D, entity.posZ,
                    30, 0.6D, 0.8D, 0.6D, 0.1D);
        }
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.2F, 0.7F);
    }
}

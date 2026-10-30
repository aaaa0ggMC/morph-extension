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
 * 僵尸猪人主动技能：狂怒战意 (Pigman Rage)。
 * 发出地狱猪人被激怒时的狂暴咆哮，瞬间激活狂战士本能，
 * 获得力量 II、速度 I 与抗性提升 I（持续 7 秒），近战爆发力翻倍。
 */
public class PigmanRageAction implements IAction, ICooldownAction {
    public static final String ID = "pigman_rage";
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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 猪人狂暴嘶吼音效
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ZOMBIE_PIG_ANGRY, SoundCategory.PLAYERS, 2.0F, 1.0F);

        // 愤怒粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, x, y + 1.8D, z, 10, 0.4, 0.2, 0.4, 0.0);
            ws.spawnParticle(EnumParticleTypes.FLAME, x, y + 1.0D, z, 20, 0.6, 0.5, 0.6, 0.05);
        }

        // 狂暴强化状态（7 秒）
        entity.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 140, 1, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 140, 0, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 140, 0, false, true));
    }
}

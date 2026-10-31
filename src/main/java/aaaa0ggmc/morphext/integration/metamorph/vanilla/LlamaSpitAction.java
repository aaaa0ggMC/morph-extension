package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.projectile.EntityLlamaSpit;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 羊驼主动技能：连珠唾沫 (Llama Spit)。
 * 朝准星连续喷吐 2 颗口水弹，造成微额伤害与压制击退，发射点前移 1.5 格避开命中宿主自身。
 */
public class LlamaSpitAction implements IAction, ICooldownAction {
    public static final String ID = "llama_spit";
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

        // 第 1 发唾沫
        fireSingleSpit(entity, morph);

        // 第 2 发连珠唾沫（4 ticks = 0.2 秒后发射）
        ServerActionScheduler.schedule(4, () -> {
            if (entity.isEntityAlive()) {
                fireSingleSpit(entity, morph);
            }
        });
    }

    private static void fireSingleSpit(EntityLivingBase entity, AbstractMorph morph) {
        World world = entity.world;
        Vec3d look = entity.getLookVec();
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.5D);

        EntityLlamaSpit spit = new EntityLlamaSpit(world);
        spit.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        spit.shoot(look.x, look.y, look.z, 1.5F, 1.0F);

        // 原生底层适配：EntityLlamaSpit.onHit 要求 owner != null 才能结算伤害
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity(world);
            if (dummy instanceof EntityLlama) {
                spit.owner = (EntityLlama) dummy;
            }
        }
        if (spit.owner == null) {
            EntityLlama fallbackLlama = new EntityLlama(world);
            fallbackLlama.setPosition(entity.posX, entity.posY, entity.posZ);
            spit.owner = fallbackLlama;
        }

        world.spawnEntity(spit);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.PLAYERS, 1.4F, 1.0F);
    }
}

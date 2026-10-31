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
 * 羊驼主动技能：连珠口水弹幕 (Llama Spit Barrage)。
 * 源码级还原 EntityLlama 吐口水弹射物发射机制：
 * 朝准星视线方向连环喷射 3 颗口水飞弹 (EntityLlamaSpit)，发射起点安全前移 1.5 格彻底规避自碰，
 * 命中产生击退与微量伤害。
 */
public class LlamaSpitBarrageAction implements IAction, ICooldownAction {
    public static final String ID = "llama_spit_barrage";
    public static final String ALIAS_ID = "llama_spit";
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

        // 第 1 颗口水飞弹 (立刻发射)
        fireSingleSpit(entity, morph);

        // 第 2 颗口水飞弹 (3 ticks / 0.15 秒后连环射出)
        ServerActionScheduler.schedule(3, () -> {
            if (entity.isEntityAlive()) {
                fireSingleSpit(entity, morph);
            }
        });

        // 第 3 颗口水飞弹 (6 ticks / 0.3 秒后连环射出)
        ServerActionScheduler.schedule(6, () -> {
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
        spit.shoot(look.x, look.y, look.z, 1.6F, 1.0F);

        // 原生底层适配：EntityLlamaSpit.onHit 要求 owner != null 才能结算伤害与击退
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

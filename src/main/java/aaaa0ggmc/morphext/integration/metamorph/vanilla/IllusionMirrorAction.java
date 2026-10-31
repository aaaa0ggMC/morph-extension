package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIllusionIllager;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 幻术师主动技能：镜像分身遁逃 (Illusion Mirror & Disappear)。
 * 顺应灾厄魔法源码体系：
 * 1. 施法咒文音画驱动：高举双手吟唱隐遁法术 (SpellType.DISAPPEAR) 并散发青蓝幻象魔纹粒子；
 * 2. 隐身疾走：宿主自身获得 6 秒完全隐身 (Invisibility) 与速度 II 移速增益；
 * 3. 4 个虚假幻象分身：在身周召唤 4 个手持长弓的虚假幻象分身 (EntityIllusionIllager)，
 *    伴随迷雾与烟雾粒子迷惑敌人，6 秒后伴随迷雾烟尘化为虚无。
 */
public class IllusionMirrorAction implements IAction, ICooldownAction {
    public static final String ID = "illusion_mirror";
    public static final int COOLDOWN = 360; // 18 秒

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
        entity.swingArm(EnumHand.MAIN_HAND);

        // 施法咒文音画驱动 (SpellType.DISAPPEAR + 青蓝魔纹粒子 + 镜像准备长吟与移位音效)
        VanillaActionHelper.triggerSpellEffects(world, entity, morph,
                EntitySpellcasterIllager.SpellType.DISAPPEAR,
                SoundEvents.ENTITY_ILLAGER_PREPARE_MIRROR,
                SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE);

        // 宿主获得 6 秒 (120 ticks) 完全隐身与速度 II
        entity.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 120, 0, false, false));
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 120, 1, false, false));

        // 原地施法迷雾烟幕
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, entity.posX, entity.posY + 1.0D, entity.posZ, 25, 0.6, 0.4, 0.6, 0.05);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, entity.posX, entity.posY + 1.0D, entity.posZ, 20, 0.3, 0.3, 0.8, 1.0);
        }

        // 召唤 4 个虚假幻象分身环绕身周 (距离 2.5 格)
        List<EntityIllusionIllager> decoys = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(entity.rotationYaw) + (i * Math.PI * 0.5D);
            double dx = entity.posX + MathHelper.cos((float) angle) * 2.5D;
            double dz = entity.posZ + MathHelper.sin((float) angle) * 2.5D;
            double dy = entity.posY;

            EntityIllusionIllager decoy = new EntityIllusionIllager(world);
            decoy.setLocationAndAngles(dx, dy, dz, (float) Math.toDegrees(angle), 0.0F);
            decoy.setNoAI(true);
            decoy.enablePersistence();
            decoy.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            decoy.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);

            world.spawnEntity(decoy);
            decoys.add(decoy);

            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, dx, dy + 1.0D, dz, 12, 0.3, 0.4, 0.3, 0.05);
                ws.spawnParticle(EnumParticleTypes.SPELL_MOB, dx, dy + 1.0D, dz, 10, 0.3, 0.3, 0.8, 1.0);
            }
        }

        // 6 秒后统一安全销毁幻象分身并爆发烟雾
        ServerActionScheduler.schedule(120, () -> {
            for (EntityIllusionIllager decoy : decoys) {
                if (decoy != null && decoy.isEntityAlive()) {
                    if (world instanceof WorldServer) {
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                                decoy.posX, decoy.posY + 1.0D, decoy.posZ, 12, 0.3, 0.4, 0.3, 0.05);
                    }
                    decoy.setDead();
                }
            }
        });
    }
}

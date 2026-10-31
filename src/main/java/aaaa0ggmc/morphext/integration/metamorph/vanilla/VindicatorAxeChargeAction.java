package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 卫道士主动技能：举斧狂怒冲刺 (Vindicator Axe Charge & Shield Disabling)。
 * 顺应灾厄卫道士凶狠战斧冲杀体系：
 * 1. 暴走冲锋：双手举斧狂奔，爆发性获得速度 II（持续 3 秒，60 ticks）与向前推力；
 * 2. 纯正物理重击：身前 3.5 格扇形横扫命中目标造成 12 点纯正物理伤害与破风击退；
 * 3. 强行破盾机制 (Johnny Shield Disabling)：若目标举盾防御，调用 disableShield(true) 强行击碎破盾，
 *    重置其举手状态并禁用盾牌 5 秒 (100 ticks)，伴随破盾音效与格挡破碎震颤。
 */
public class VindicatorAxeChargeAction implements IAction, ICooldownAction {
    public static final String ID = "vindicator_axe_charge";
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
        Vec3d look = entity.getLookVec();

        entity.swingArm(EnumHand.MAIN_HAND);

        // 暴走冲锋加速 (速度 II 持续 3 秒) 与爆发推力
        entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 1, false, false));
        entity.motionX += look.x * 0.95D;
        entity.motionY += 0.15D;
        entity.motionZ += look.z * 0.95D;
        entity.velocityChanged = true;

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.VINDICATION_ILLAGER_AMBIENT, SoundCategory.PLAYERS, 1.8F, 0.85F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4F, 0.9F);

        // 身前破空剑气与劈砍火花
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            double fx = entity.posX + look.x * 1.8D;
            double fy = entity.posY + entity.getEyeHeight() * 0.5D;
            double fz = entity.posZ + look.z * 1.8D;
            ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, fx, fy, fz, 2, 0.2, 0.1, 0.2, 0.0);
            ws.spawnParticle(EnumParticleTypes.CRIT, fx, fy, fz, 16, 0.4, 0.3, 0.4, 0.15);
        }

        // 身前 3.5 格扇形致命劈砍判定
        double reach = 3.5D;
        AxisAlignedBB box = entity.getEntityBoundingBox().grow(reach, 1.5D, reach);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        DamageSource source = entity instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) entity)
                : DamageSource.causeMobDamage(entity);

        boolean hitAny = false;
        for (EntityLivingBase target : targets) {
            Vec3d toTarget = new Vec3d(target.posX - entity.posX, target.posY - entity.posY, target.posZ - entity.posZ).normalize();
            if (look.dotProduct(toTarget) > 0.35D) {
                // 强行破盾机制 (若目标正在举盾防御)
                if (target instanceof EntityPlayer) {
                    EntityPlayer tp = (EntityPlayer) target;
                    if (tp.isActiveItemStackBlocking()) {
                        ItemStack shieldItem = tp.getActiveItemStack();
                        // 原版破盾契约：disableShield(true) 附带 5 秒 (100 ticks) 强制禁用
                        tp.disableShield(true);
                        if (!shieldItem.isEmpty()) {
                            tp.getCooldownTracker().setCooldown(shieldItem.getItem(), 100);
                        }
                        tp.resetActiveHand();
                        world.setEntityState(tp, (byte) 30); // 原版破盾状态更新

                        world.playSound(null, tp.posX, tp.posY, tp.posZ,
                                SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.8F, 1.0F);

                        if (world instanceof WorldServer) {
                            ((WorldServer) world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
                                    tp.posX, tp.posY + tp.height * 0.5D, tp.posZ, 20, 0.3, 0.3, 0.3, 0.1,
                                    net.minecraft.item.Item.getIdFromItem(shieldItem.getItem()));
                        }
                    }
                }

                // 12 点纯正物理重击伤害
                target.attackEntityFrom(source, 12.0F);

                // 强劲击退
                target.motionX += look.x * 0.75D;
                target.motionY += 0.35D;
                target.motionZ += look.z * 0.75D;
                target.velocityChanged = true;
                hitAny = true;

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                            target.posX, target.posY + target.height * 0.5D, target.posZ, 12, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }

        if (hitAny) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.6F, 1.1F);
        }
    }
}

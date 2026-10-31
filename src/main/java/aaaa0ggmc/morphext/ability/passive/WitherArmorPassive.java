package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

/**
 * 凋灵护甲被动能力 (Wither Armor Passive)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.boss.EntityWither} 核心防御机制：
 * 1. 【血量阈值判定】：当宿主生命值 ≤ 50% 时进入凋灵护甲激发状态；
 * 2. 【光环视觉同步】：同步变身内部 {@link EntityWither} 的生命百分比，使原版客户端 {@code LayerWitherAura}
 *    自动渲染旋转的白光凋灵电弧；服务端周期性喷发神秘白光电弧粒子；
 * 3. 【绝对弹丸弹开与免疫】：在受到任何弓箭 (EntityArrow)、投掷物 (EntityThrowable)、恶魂/烈焰人火球 (EntityFireball)
 *    或弹射物 (IProjectile) 攻击时，100% 触发弹开反弹向量并完全豁免伤害（遵循 {@code attackEntityFrom} 原版判定）；
 * 4. 【视听沉浸反馈】：弹开弹丸时播放强力金属与凋灵防御音效以及电弧微粒爆裂。
 */
public class WitherArmorPassive extends AbstractPassiveAbility {
    public static final String ID = "wither_armor";

    public WitherArmorPassive(String id) {
        super(id, "morphext.passive.wither_armor.name", "morphext.passive.wither_armor.desc", ItemStack.EMPTY);
    }

    public WitherArmorPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null) {
            return;
        }

        // 同步内部 EntityWither 实例生命值比例，驱动客户端 LayerWitherAura 原生渲染
        if (context != null && context.getMorph() instanceof EntityMorph) {
            EntityLivingBase inner = ((EntityMorph) context.getMorph()).getEntity();
            if (inner instanceof EntityWither) {
                float ratio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
                inner.setHealth(inner.getMaxHealth() * ratio);
            }
        }

        // 生命值 ≤ 50% 时周身环绕白光电弧微粒
        if (!player.world.isRemote && isArmored(player)) {
            if (player.ticksExisted % 3 == 0 && player.world instanceof WorldServer) {
                WorldServer ws = (WorldServer) player.world;
                double angle = (player.ticksExisted * 0.35D) % (Math.PI * 2);
                double ox = Math.cos(angle) * 1.15D;
                double oz = Math.sin(angle) * 1.15D;

                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, player.posX + ox, player.posY + 1.1D, player.posZ + oz,
                        2, 0.08D, 0.15D, 0.08D, 0.02D);
                ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, player.posX - ox, player.posY + 0.8D, player.posZ - oz,
                        1, 0.05D, 0.1D, 0.05D, 0.02D);
            }
        }
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (player == null || source == null) {
            return false;
        }

        // 仅在生命值 ≤ 50%（凋灵护甲激活）时生效
        if (!isArmored(player)) {
            return false;
        }

        boolean isProjectile = source.isProjectile();
        Entity immediate = source.getImmediateSource();

        if (!isProjectile && immediate != null) {
            isProjectile = immediate instanceof IProjectile
                    || immediate instanceof EntityArrow
                    || immediate instanceof EntityThrowable
                    || immediate instanceof EntityFireball;
        }

        if (isProjectile) {
            // 弹开攻击弹丸，施加反向力矩并附带轻微向上抛物线
            if (immediate != null) {
                immediate.motionX = -immediate.motionX * 0.85D + (player.world.rand.nextDouble() - 0.5D) * 0.3D;
                immediate.motionY = Math.abs(immediate.motionY) * 0.5D + 0.35D;
                immediate.motionZ = -immediate.motionZ * 0.85D + (player.world.rand.nextDouble() - 0.5D) * 0.3D;
                immediate.velocityChanged = true;
            }

            // 视听反馈：电弧格挡音效与微粒喷发
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.4F, 1.5F);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.8F, 1.8F);

            if (player.world instanceof WorldServer) {
                WorldServer ws = (WorldServer) player.world;
                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, player.posX, player.posY + 1.0D, player.posZ,
                        25, 0.6D, 0.6D, 0.6D, 0.15D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, player.posX, player.posY + 1.0D, player.posZ,
                        8, 0.3D, 0.3D, 0.3D, 0.05D);
            }

            return true; // 彻底取消伤害
        }

        return false;
    }

    /**
     * 判断凋灵护甲是否处于激活状态（生命值 ≤ 50%）
     */
    public static boolean isArmored(EntityLivingBase entity) {
        return entity != null && entity.getHealth() <= entity.getMaxHealth() * 0.5F;
    }
}

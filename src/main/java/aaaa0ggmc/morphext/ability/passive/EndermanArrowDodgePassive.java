package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 末影人箭矢/投掷物主动闪避防御被动 (Enderman Arrow Dodge Passive)。
 * <p>
 * 深度重构并 100% 还原 {@link net.minecraft.entity.monster.EntityEnderman#attackEntityFrom} 经典机理：
 * 1. 【远程判定】：拦截任何 {@link EntityDamageSourceIndirect}、弹丸 (IProjectile) 或投掷物 (EntityArrow, EntityThrowable) 攻击；
 * 2. 【随机紧急折跃】：至多进行 64 次多维空间寻址，自动寻找安全陆地执行瞬间折跃；
 * 3. 【无伤闪避】：无论玩家被何等神弓或密集箭雨射击，攻击必定被判定完全无效并瞬间折跃脱离；
 * 4. 【折跃微粒与音效】：在起点与落点生成大量经典末影传送门微粒并消除跌落伤害。
 */
public class EndermanArrowDodgePassive extends AbstractPassiveAbility {
    public static final String ID = "enderman_arrow_dodge";

    public EndermanArrowDodgePassive(String id) {
        super(id, "morphext.passive.enderman_arrow_dodge.name", "morphext.passive.enderman_arrow_dodge.desc", ItemStack.EMPTY);
    }

    public EndermanArrowDodgePassive() {
        this(ID);
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (player == null || source == null || player.world == null) {
            return false;
        }

        boolean isIndirect = source instanceof EntityDamageSourceIndirect || source.isProjectile();
        Entity immediate = source.getImmediateSource();

        if (!isIndirect && immediate != null) {
            isIndirect = immediate instanceof IProjectile
                    || immediate instanceof EntityArrow
                    || immediate instanceof EntityThrowable;
        }

        if (isIndirect) {
            // 在服务端执行 64 次原版随机折跃尝试
            if (!player.world.isRemote) {
                teleportRandomlyWithFeedback(player);
            }
            return true; // 100% 豁免本次远程伤害
        }

        return false;
    }

    /**
     * 还原原版 EntityEnderman.teleportRandomly() 算法并辅以视听反馈
     */
    public static boolean teleportRandomlyWithFeedback(EntityLivingBase entity) {
        World world = entity.world;
        if (world.isRemote) {
            return false;
        }

        double startX = entity.posX;
        double startY = entity.posY;
        double startZ = entity.posZ;

        for (int i = 0; i < 64; ++i) {
            double destX = startX + (entity.getRNG().nextDouble() - 0.5D) * 32.0D;
            double destY = MathHelper.clamp(startY + (double) (entity.getRNG().nextInt(32) - 16),
                    1.0D, (double) (world.getActualHeight() - 1));
            double destZ = startZ + (entity.getRNG().nextDouble() - 0.5D) * 32.0D;

            if (entity.isRiding()) {
                entity.dismountRidingEntity();
            }

            if (entity.attemptTeleport(destX, destY, destZ)) {
                // 起点粒子
                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, startX, startY + 1.0D, startZ,
                            40, 0.4D, 0.8D, 0.4D, 0.5D);
                }
                world.playSound(null, startX, startY, startZ,
                        SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.2F, 1.0F);

                // 修正玩家客户端网络坐标并消除掉落
                if (entity instanceof EntityPlayerMP) {
                    EntityPlayerMP playerMP = (EntityPlayerMP) entity;
                    playerMP.connection.setPlayerLocation(entity.posX, entity.posY, entity.posZ,
                            entity.rotationYaw, entity.rotationPitch);
                }
                entity.fallDistance = 0.0F;

                // 落点粒子与音效
                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, entity.posX, entity.posY + 1.0D, entity.posZ,
                            40, 0.4D, 0.8D, 0.4D, 0.5D);
                }
                world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.2F, 1.0F);

                return true;
            }
        }

        return false;
    }
}

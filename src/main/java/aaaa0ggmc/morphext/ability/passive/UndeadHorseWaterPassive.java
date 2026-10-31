package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;

/**
 * 不死坐骑深海疾驰被动 (Undead Steed Ocean Trench Gallop Passive)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.passive.EntitySkeletonHorse} 与 {@link net.minecraft.entity.passive.EntityZombieHorse} 生理机理：
 * 1. 【永不溺水】：彻底免疫溺水伤害 (DamageSource.DROWN)，并在水下始终维持满充肺活量；
 * 2. 【深海重力沉降】：水下消除浮力阻碍，自然稳固下沉着陆于海底基岩与沙床；
 * 3. 【海底无阻疾驰与跳跃】：在深海底部移动时消除水体阻力，赋予犹如陆地般流畅的冲刺与高跳跨越能力，伴随深海潜流气泡。
 */
public class UndeadHorseWaterPassive extends AbstractPassiveAbility {
    public static final String ID = "undead_horse_water";

    public UndeadHorseWaterPassive(String id) {
        super(id, "morphext.passive.undead_horse_water.name", "morphext.passive.undead_horse_water.desc", new ItemStack(Items.SADDLE));
    }

    public UndeadHorseWaterPassive() {
        this(ID);
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        // 完全免疫溺水伤害
        if (source == DamageSource.DROWN) {
            return true;
        }
        return false;
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }

        // 持续重置氧气，确保绝无窒息可能
        player.setAir(300);

        if (player.isInWater()) {
            // 1. 水下平滑沉降：若悬空于深水区，辅助平稳下沉至海底，还原不死骨马踏底机制
            if (!player.onGround && player.motionY > -0.35D) {
                player.motionY -= 0.025D;
            }

            // 2. 水下海底无阻疾驰推进
            double hSpeed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
            if (hSpeed > 0.02D) {
                player.motionX *= 1.12D;
                player.motionZ *= 1.12D;

                double maxSpeed = 0.38D;
                double currentSpeed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
                if (currentSpeed > maxSpeed) {
                    player.motionX = (player.motionX / currentSpeed) * maxSpeed;
                    player.motionZ = (player.motionZ / currentSpeed) * maxSpeed;
                }
                player.velocityChanged = true;

                // 深海蹄下气泡尾迹
                if (player.world instanceof WorldServer && player.ticksExisted % 4 == 0) {
                    ((WorldServer) player.world).spawnParticle(EnumParticleTypes.WATER_BUBBLE,
                            player.posX, player.posY + 0.1D, player.posZ, 3, 0.2D, 0.1D, 0.2D, 0.05D);
                }
            }

            // 3. 深海跳跃跨越：起跳时提供充足垂直跃力，轻松跨越深海壕沟与障碍
            if (player.motionY > 0.04D && player.motionY < 0.42D) {
                player.motionY = 0.42D;
                player.velocityChanged = true;
            }
        }
    }
}

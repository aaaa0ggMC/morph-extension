package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

/**
 * 鱿鱼触手律动游动与应激喷墨被动 (Squid Tentacle Propulsion & Panic Ink Passive)。
 * 1. 水中触手脉冲推进力：在水中移动时产生平滑的流线型触手脉冲推进，伴随气泡尾迹；
 * 2. 水下免受溺水伤害并保持充盈空气；
 * 3. 水中受击应激反应：受到伤害时爆发一团浓黑墨汁并获得脱战逃逸加速。
 */
public class SquidPassive extends AbstractPassiveAbility {
    public static final String ID = "squid_swim";

    public SquidPassive(String id) {
        super(id, "morphext.passive.squid_swim.name", "morphext.passive.squid_swim.desc", ItemStack.EMPTY);
    }

    public SquidPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.isInWater()) {
            player.setAir(300);

            // 触手游动推进脉冲
            double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
            if (speed > 0.04D && !player.onGround) {
                Vec3d look = player.getLook(1.0F);
                player.motionX += look.x * 0.035D;
                player.motionY += look.y * 0.035D;
                player.motionZ += look.z * 0.035D;

                if (player.ticksExisted % 15 == 0 && player.world instanceof WorldServer) {
                    ((WorldServer) player.world).spawnParticle(EnumParticleTypes.WATER_BUBBLE,
                            player.posX - look.x * 0.6D, player.posY + 0.3D - look.y * 0.6D, player.posZ - look.z * 0.6D,
                            6, 0.15D, 0.15D, 0.15D, 0.02D);
                }
            }
        }
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (player.isInWater() && source != null) {
            Vec3d look = player.getLook(1.0F);
            player.motionX += look.x * 0.6D;
            player.motionY += look.y * 0.4D + 0.15D;
            player.motionZ += look.z * 0.6D;
            player.velocityChanged = true;

            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_SQUID_HURT, SoundCategory.PLAYERS, 1.2F, 1.1F);

            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                        player.posX, player.posY + 0.5D, player.posZ, 20, 0.8D, 0.5D, 0.8D, 0.02D);
            }
        }
        return false;
    }
}

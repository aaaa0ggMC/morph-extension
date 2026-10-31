package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

/**
 * 末影人触水瞬移应激被动 (Enderman Water Stress Passive)。
 * <p>
 * 深度还原 {@link net.minecraft.entity.monster.EntityEnderman} 遇水侵蚀与狂躁逃逸机理：
 * 1. 【水体与雨水侵蚀】：接触雨水或浸入水中时，每秒受到 1 点侵蚀伤害；
 * 2. 【应激逃逸折跃】：受水侵蚀瞬间触发强烈的求生应激反应，瞬间智能折跃至周围干燥无水的陆地；
 * 3. 【视听反馈】：伴随火焰熄灭的滋滋声与末影传送门微粒。
 */
public class EndermanWaterStressPassive extends AbstractPassiveAbility {
    public static final String ID = "enderman_water_stress";

    public EndermanWaterStressPassive(String id) {
        super(id, "morphext.passive.enderman_water_stress.name", "morphext.passive.enderman_water_stress.desc", ItemStack.EMPTY);
    }

    public EndermanWaterStressPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead || player.capabilities.isCreativeMode) {
            return;
        }

        // 判定是否浸入水中或处于暴雨之中
        if (player.isWet()) {
            if (player.ticksExisted % 20 == 0) {
                // 造成水侵蚀伤害
                player.attackEntityFrom(DamageSource.DROWN, 1.0F);

                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6F, 1.4F);
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_ENDERMEN_HURT, SoundCategory.PLAYERS, 0.8F, 1.2F);

                if (player.world instanceof WorldServer) {
                    ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                            player.posX, player.posY + 1.0D, player.posZ, 12, 0.3D, 0.5D, 0.3D, 0.05D);
                }

                // 狂暴应激：尝试向干燥安全地面逃逸折跃
                EndermanArrowDodgePassive.teleportRandomlyWithFeedback(player);
            }
        }
    }
}

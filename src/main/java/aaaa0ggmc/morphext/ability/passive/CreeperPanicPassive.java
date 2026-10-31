package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.List;

/**
 * 苦力怕天敌恐慌威慑被动 (Creeper Panic Passive)。
 * 源码级还原原版 EntityAICreeperPanic 与苦力怕避猫机制：
 * 每 10 ticks 扫描周围 16 格内的 EntityCreeper，强制熄灭其引信、清空仇恨攻击目标，
 * 并赋予其速度加成使其朝远离玩家方向反向狂奔恐慌逃窜。
 */
public class CreeperPanicPassive extends AbstractPassiveAbility {
    public static final String ID = "morphext:creeper_panic";

    public CreeperPanicPassive(String id) {
        super(id, "morphext.passive.creeper_panic.name", "morphext.passive.creeper_panic.desc", ItemStack.EMPTY);
    }

    public CreeperPanicPassive() {
        this("creeper_panic");
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.ticksExisted % 10 != 0) {
            return;
        }

        List<EntityCreeper> creepers = player.world.getEntitiesWithinAABB(EntityCreeper.class,
                player.getEntityBoundingBox().grow(16.0D));

        for (EntityCreeper creeper : creepers) {
            if (creeper == null || !creeper.isEntityAlive()) {
                continue;
            }

            // 1. 强制清空攻击目标与复仇目标
            if (creeper.getAttackTarget() != null) {
                creeper.setAttackTarget(null);
            }
            if (creeper.getRevengeTarget() != null) {
                creeper.setRevengeTarget(null);
            }

            // 2. 强制熄灭并重置苦力怕引信膨胀状态 (setCreeperState(-1) 与反射重置 timeSinceIgnited)
            creeper.setCreeperState(-1);
            try {
                ReflectionHelper.setPrivateValue(EntityCreeper.class, creeper, 0, "timeSinceIgnited", "field_70833_d");
            } catch (Throwable ignored) {
            }

            // 3. 赋予逃跑速度加成 (迅捷 II)
            creeper.addPotionEffect(new PotionEffect(MobEffects.SPEED, 40, 1, false, false));

            // 4. 寻路逃窜：调用底层 RandomPositionGenerator 寻找远离玩家的安全坐标
            Vec3d runawayTarget = RandomPositionGenerator.findRandomTargetBlockAwayFrom(
                    creeper, 16, 7, player.getPositionVector());
            if (runawayTarget != null) {
                creeper.getNavigator().tryMoveToXYZ(runawayTarget.x, runawayTarget.y, runawayTarget.z, 1.40D);
            }

            // 5. 反向狂奔推力：施加物理位移远离宿主
            Vec3d away = creeper.getPositionVector().subtract(player.getPositionVector()).normalize();
            if (away.lengthSquared() > 0.0001D) {
                creeper.motionX += away.x * 0.22D;
                creeper.motionZ += away.z * 0.22D;
                creeper.velocityChanged = true;
            }

            // 6. 恐慌冒汗与烟雾微粒
            if (player.world instanceof WorldServer) {
                WorldServer ws = (WorldServer) player.world;
                ws.spawnParticle(EnumParticleTypes.WATER_SPLASH,
                        creeper.posX, creeper.posY + creeper.height + 0.15D, creeper.posZ,
                        6, 0.2D, 0.2D, 0.2D, 0.05D);
                ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                        creeper.posX, creeper.posY + creeper.height * 0.8D, creeper.posZ,
                        3, 0.15D, 0.15D, 0.15D, 0.02D);
            }
        }
    }
}

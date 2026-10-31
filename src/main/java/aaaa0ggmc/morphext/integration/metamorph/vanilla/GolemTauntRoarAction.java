package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 铁傀儡专属动作：构装嘲讽战吼 (Golem Taunt Roar)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntityIronGolem} 钢铁嘲讽与村庄仇恨吸引机制：
 * 1. 【重锤胸膛】：重拳猛烈敲击钢铁胸铠，激荡出震慑山河的金属震颤与回音；
 * 2. 【强制目标重定向】：强行嘲讽周围 16 格内所有正在攻击村民、友军或附近的敌对生物 (IMob)，强行将其仇恨目标 (setAttackTarget & setRevengeTarget) 绑定到自身；
 * 3. 【仇恨追踪与高亮】：被嘲讽敌对生物获得 5 秒发光 (GLOWING) 标记并立即向铁傀儡加速围拢；
 * 4. 【装甲强化防御】：自身进入嘲讽硬化防御，获得 4 秒抗性提升 II 与 10 秒伤害吸收 I 抵御围攻；
 * 5. 【贴身震波击退】：身前 3.5 格近身敌人受到金属声波冲击被震退一段距离。
 */
public class GolemTauntRoarAction implements IAction, ICooldownAction {
    public static final String ID = "golem_taunt_roar";
    public static final String ALIAS_ID = "morphext:golem_taunt_roar";
    public static final int COOLDOWN = 300; // 15 秒冷却 (300 ticks)

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        World world = player.world;

        // 1. 手臂动作与实体挥拳动画
        player.swingArm(EnumHand.MAIN_HAND);
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity();
            if (dummy instanceof EntityIronGolem) {
                // 状态 4 触发原版铁傀儡出拳攻击动画
                world.setEntityState(dummy, (byte) 4);
            }
        }

        // 2. 沉重金属战吼音效
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 1.4F, 0.75F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.PLAYERS, 1.5F, 0.6F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 1.2F, 0.7F);

        // 3. 巨响冲击波粒子
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                    player.posX, player.posY + 1.2D, player.posZ,
                    2, 0.2D, 0.2D, 0.2D, 0.0D);
            ws.spawnParticle(EnumParticleTypes.CRIT,
                    player.posX, player.posY + 1.2D, player.posZ,
                    30, 0.8D, 0.8D, 0.8D, 0.2D);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    player.posX, player.posY + 1.8D, player.posZ,
                    15, 0.6D, 0.4D, 0.6D, 0.1D);
        }

        // 4. 周围 16 格强制嘲讽重定向
        AxisAlignedBB tauntBox = player.getEntityBoundingBox().grow(16.0D, 8.0D, 16.0D);
        List<EntityLiving> hostiles = world.getEntitiesWithinAABB(EntityLiving.class, tauntBox, e -> {
            if (e == null || !e.isEntityAlive()) {
                return false;
            }
            return e instanceof IMob || e.getAttackTarget() != null;
        });

        int tauntedCount = 0;
        for (EntityLiving mob : hostiles) {
            mob.setAttackTarget(player);
            mob.setRevengeTarget(player);

            if (mob.getNavigator() != null) {
                mob.getNavigator().tryMoveToEntityLiving(player, 1.25D);
            }

            // 赋予 5 秒高亮轮廓标记
            mob.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 100, 0, false, false));

            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                        mob.posX, mob.posY + mob.height + 0.3D, mob.posZ,
                        6, 0.2D, 0.2D, 0.2D, 0.05D);
            }
            tauntedCount++;
        }

        // 5. 身前 3.5 格近身敌人冲击波轻微击退
        AxisAlignedBB closeBox = player.getEntityBoundingBox().grow(3.5D, 2.0D, 3.5D);
        List<EntityLivingBase> closeMobs = world.getEntitiesWithinAABB(EntityLivingBase.class, closeBox,
                e -> e != player && e.isEntityAlive() && e instanceof IMob);
        for (EntityLivingBase cm : closeMobs) {
            double dx = cm.posX - player.posX;
            double dz = cm.posZ - player.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.05D) {
                cm.motionX += (dx / dist) * 0.45D;
                cm.motionY += 0.18D;
                cm.motionZ += (dz / dist) * 0.45D;
                cm.velocityChanged = true;
            }
        }

        // 6. 嘲讽强化防御：抗性提升 II (4秒) 与 伤害吸收 I (10秒)
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 80, 1, false, false));
        player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 200, 0, false, false));

        player.sendStatusMessage(new TextComponentTranslation(
                "morphext.action.golem_taunt_roar.success", tauntedCount), true);
    }
}

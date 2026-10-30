package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 蜘蛛/洞穴蜘蛛主动技能：吐丝结网 (Web Trap) - 防穿墙自由释放版。
 * 向准星喷吐黏性蛛丝。防穿墙射线检测：命中生物造成缠绕伤害与迟滞减速（缓慢 IV）；
 * 命中方块则爆发蛛网碎屑风暴，小范围迟滞周边生物；射空自然贯穿消散。
 */
public class WebTrapAction implements IAction, ICooldownAction {
    public static final String ID = "web_trap";
    public static final int COOLDOWN = 120; // 6 秒

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
        Vec3d start = VanillaActionHelper.getSafeSpawnPos(entity, 0.8D);
        Vec3d end = start.add(look.x * 16.0D, look.y * 16.0D, look.z * 16.0D);

        entity.swingArm(EnumHand.MAIN_HAND);

        // 1. 视线方块阻挡检测（防止隔墙命中）
        RayTraceResult blockHit = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d maxReach = blockHit != null ? blockHit.hitVec : end;

        // 2. 在发射点到障碍物之间检测被射线命中的实体
        AxisAlignedBB searchBox = new AxisAlignedBB(start.x, start.y, start.z, maxReach.x, maxReach.y, maxReach.z).grow(1.0D);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, searchBox,
                e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));

        EntityLivingBase target = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (EntityLivingBase candidate : list) {
            AxisAlignedBB candidateBox = candidate.getEntityBoundingBox().grow(0.3D);
            RayTraceResult intercept = candidateBox.calculateIntercept(start, maxReach);
            if (intercept != null) {
                double distSq = start.squareDistanceTo(intercept.hitVec);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    target = candidate;
                }
            }
        }

        // 3. 服务端绘制蛛丝弹道
        Vec3d hitVec = target != null ? target.getPositionVector().add(0.0D, (double) target.height * 0.5D, 0.0D) : maxReach;
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            VanillaActionHelper.drawParticleLine(ws, start, hitVec, EnumParticleTypes.CLOUD, 0.4D, 0.02D);
        }

        // 4. 结算判定
        if (target != null) {
            target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(entity, entity), 4.0F);
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 3, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 0, false, true));
        } else if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK) {
            // 命中地面或墙面：爆发蛛丝缠绕效果，施加小范围迟滞减速
            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                int webState = net.minecraft.block.Block.getStateId(Blocks.WEB.getDefaultState());
                ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, hitVec.x, hitVec.y, hitVec.z, 35, 0.6, 0.4, 0.6, 0.05, webState);
            }
            List<EntityLivingBase> splashTargets = world.getEntitiesWithinAABB(EntityLivingBase.class,
                    new AxisAlignedBB(hitVec.x - 2.5, hitVec.y - 1.5, hitVec.z - 2.5,
                            hitVec.x + 2.5, hitVec.y + 1.5, hitVec.z + 2.5),
                    e -> e != null && e != entity && e.isEntityAlive() && !e.isOnSameTeam(entity));
            for (EntityLivingBase e : splashTargets) {
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 2, false, true));
            }
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 1.2F, 0.8F);
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.BLOCK_SLIME_HIT, SoundCategory.PLAYERS, 1.0F, 1.4F);
    }
}

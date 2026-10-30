package aaaa0ggmc.morphext.integration.metamorph;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 宿主安全生物动作指挥器 (Safe Universal Mob Act Action):
 * 替代脆弱且存在严重自锁缺陷的 Metamorph Max AITarget。
 *
 * 核心设计：
 * 1. 潜行释放：清空索敌，重置 AI 仇恨，使 dummy 实体回归平静（Disengage / Calm）；
 * 2. 正常释放：准星 64 格射线检测，精确排除宿主玩家、排除变身 dummy 实体自身、排除队友及自身驯服生物；
 * 3. 驱动原生生物动作：
 *    - 优先派发至已注册的扩展模组适配器（如深渊 Boss 专属三头齐射、暗影烈焰等）；
 *    - 远程生物（IRangedAttackMob）：直接触发 attackEntityWithRangedAttack 射击；
 *    - 近战生物：驱动挥臂动作（swingArm），对锁定敌人或身前 3.5 格锥形区域敌人造成原生近战判定与击退；
 * 4. 彻底杜绝触发 Metamorph 的 dummy -> player 递归重定向死穴。
 */
public class SafeMobActAction implements IAction {

    @FunctionalInterface
    public interface IMobActAdapter {
        /**
         * 处理生物专属原生动作
         * @param player 宿主实体
         * @param morph 当前变身形态
         * @param dummy 变身内部 dummy 实体
         * @param targetEnemy 准星锁定的敌对生物（可为 null）
         * @param lookTarget 准星前方空间坐标
         * @return 是否已成功执行动作
         */
        boolean handleMobAct(EntityLivingBase player, AbstractMorph morph, EntityLiving dummy, @Nullable EntityLivingBase targetEnemy, Vec3d lookTarget);
    }

    private static final List<IMobActAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    public static void registerAdapter(IMobActAdapter adapter) {
        if (adapter != null && !ADAPTERS.contains(adapter)) {
            ADAPTERS.add(0, adapter); // 后注册优先拦截
        }
    }

    @Override
    public void execute(EntityLivingBase entity, @Nullable AbstractMorph morph) {
        if (entity.world.isRemote) return;
        if (!(morph instanceof EntityMorph)) return;

        EntityLivingBase dummy = ((EntityMorph) morph).getEntity(entity.world);
        if (!(dummy instanceof EntityLiving)) return;
        EntityLiving living = (EntityLiving) dummy;

        if (entity.isSneaking()) {
            // 潜行释放：彻底解除锁定与仇恨
            living.setAttackTarget(null);
            living.setRevengeTarget(null);
            living.setNoAI(true);
            entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.6F, 0.8F);
            return;
        }

        // 准星精确搜索视线中的敌对目标（排除自身、排除宿主）
        EntityLivingBase targetEnemy = findValidTargetInCrosshairs(entity, dummy, 64.0D);
        Vec3d lookVec = entity.getLook(1.0F);
        Vec3d eyePos = entity.getPositionEyes(1.0F);
        Vec3d lookTarget = eyePos.add(lookVec.x * 64.0D, lookVec.y * 64.0D, lookVec.z * 64.0D);

        // 安全更新仇恨目标（绝不指向宿主自身）
        if (targetEnemy != null) {
            living.setNoAI(false);
            living.setAttackTarget(targetEnemy);
            living.setRevengeTarget(targetEnemy);
        } else {
            living.setAttackTarget(null);
        }

        // 1. 优先交由专属适配器链处理（如深渊 Boss 原生技能复用）
        boolean handled = false;
        for (IMobActAdapter adapter : ADAPTERS) {
            try {
                if (adapter.handleMobAct(entity, morph, living, targetEnemy, lookTarget)) {
                    handled = true;
                    break;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        if (handled) {
            return;
        }

        // 2. 原版通用远程生物适配（Skeleton, Stray, Snowman, Witch, etc.）
        if (living instanceof IRangedAttackMob) {
            entity.swingArm(EnumHand.MAIN_HAND);
            living.swingArm(EnumHand.MAIN_HAND);
            if (targetEnemy != null) {
                ((IRangedAttackMob) living).attackEntityWithRangedAttack(targetEnemy, 1.0F);
            } else {
                entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }

        // 3. 原版通用近战生物适配（Zombie, Iron Golem, Spider, Pigman, Wolf, etc.）
        entity.swingArm(EnumHand.MAIN_HAND);
        living.swingArm(EnumHand.MAIN_HAND);

        float attackDamage = 4.0F;
        IAttributeInstance dmgAttr = living.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            attackDamage = (float) dmgAttr.getAttributeValue();
        }

        if (targetEnemy != null && entity.getDistanceSq(targetEnemy) <= 16.0D) {
            // 已锁定且在近战范围内：精准重击
            living.attackEntityAsMob(targetEnemy);
            DamageSource dmg = entity instanceof EntityPlayer ?
                    DamageSource.causePlayerDamage((EntityPlayer) entity) : DamageSource.causeMobDamage(living);
            targetEnemy.attackEntityFrom(dmg, attackDamage);
            entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            // 自由横扫：对身前 3.5 格内扇形范围内的敌对目标造成近战横扫重击
            List<EntityLivingBase> nearby = entity.world.getEntitiesWithinAABB(EntityLivingBase.class,
                    entity.getEntityBoundingBox().grow(3.5D),
                    e -> e != entity && e != living && e.isEntityAlive() && !e.isOnSameTeam(entity));

            boolean hitAny = false;
            for (EntityLivingBase e : nearby) {
                Vec3d toTarget = e.getPositionVector().subtract(entity.getPositionVector()).normalize();
                if (toTarget.dotProduct(lookVec) > 0.35D) {
                    living.attackEntityAsMob(e);
                    DamageSource dmg = entity instanceof EntityPlayer ?
                            DamageSource.causePlayerDamage((EntityPlayer) entity) : DamageSource.causeMobDamage(living);
                    e.attackEntityFrom(dmg, attackDamage);
                    hitAny = true;
                }
            }

            if (hitAny) {
                entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            } else {
                entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.8F, 1.2F);
            }
        }
    }

    public static EntityLivingBase findValidTargetInCrosshairs(EntityLivingBase caster, EntityLivingBase dummy, double range) {
        World world = caster.world;
        Vec3d eyePos = caster.getPositionEyes(1.0F);
        Vec3d lookVec = caster.getLook(1.0F);
        Vec3d reachVec = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);

        AxisAlignedBB searchBox = caster.getEntityBoundingBox().expand(lookVec.x * range, lookVec.y * range, lookVec.z * range).grow(2.0D);
        List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(caster, searchBox);

        EntityLivingBase closestTarget = null;
        double minDistance = range;

        for (Entity e : list) {
            if (!(e instanceof EntityLivingBase) || !e.isEntityAlive()) continue;
            if (e == caster || e == dummy) continue;

            // 排除其 AABB 包含视线起点的实体（防止超大模型 dummy 误判）
            AxisAlignedBB aabb = e.getEntityBoundingBox().grow(e.getCollisionBorderSize());
            if (aabb.contains(eyePos)) continue;

            // 队友或驯服生物豁免
            if (caster instanceof EntityPlayer && e.isOnSameTeam(caster)) continue;

            RayTraceResult intercept = aabb.calculateIntercept(eyePos, reachVec);
            if (intercept != null) {
                double dist = eyePos.distanceTo(intercept.hitVec);
                if (dist < minDistance) {
                    minDistance = dist;
                    closestTarget = (EntityLivingBase) e;
                }
            }
        }

        return closestTarget;
    }
}

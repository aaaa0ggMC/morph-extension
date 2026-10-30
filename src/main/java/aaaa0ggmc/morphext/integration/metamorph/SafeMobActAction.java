package aaaa0ggmc.morphext.integration.metamorph;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 宿主安全索敌指挥动作 (Safe Universal Aggro Action):
 * 替代脆弱且存在严重自锁缺陷的 Metamorph Max AITarget。
 *
 * 核心设计：
 * 1. 潜行释放：清空索敌，重置 AI 仇恨，使 dummy 实体回归平静；
 * 2. 正常释放：准星 64 格射线检测，精确排除宿主玩家、排除变身 dummy 实体自身、排除队友及自身驯服生物；
 * 3. 若准星无敌人：绝不将目标设为自身或宿主；
 * 4. 彻底杜绝触发 Metamorph 的 dummy -> player 递归重定向死穴。
 */
public class SafeMobActAction implements IAction {

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

        if (targetEnemy != null) {
            living.setNoAI(false);
            living.setAttackTarget(targetEnemy);
            living.setRevengeTarget(targetEnemy);
            entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
        } else {
            // 前方未锁定到具体怪物：绝不误锁玩家或 dummy，保持 null
            living.setAttackTarget(null);
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

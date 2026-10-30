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
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 蜘蛛/洞穴蜘蛛主动技能：吐丝结网 (Web Trap)。
 * 向准星目标喷吐极具黏性的蛛丝。直接命中生物将造成缠绕伤害并附加极高程度的迟滞减速（缓慢 IV）；
 * 命中地面或墙面则会在目标位置织就一张蜘蛛网。
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
        Vec3d start = VanillaActionHelper.getSafeSpawnPos(entity, 0.6D);
        Vec3d end = start.add(look.scale(16.0D));

        // 视线投射检测
        RayTraceResult blockHit = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d hitVec = blockHit != null ? blockHit.hitVec : end;

        // 检查是否命中实体
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 16.0D, 0.3D);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            Vec3d targetPoint = target != null ? target.getPositionVector().add(0.0D, (double) target.height * 0.5D, 0.0D) : hitVec;
            VanillaActionHelper.drawParticleLine(ws, start, targetPoint, EnumParticleTypes.CLOUD, 0.5D, 0.02D);
        }

        if (target != null) {
            target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(entity, entity), 4.0F);
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 3, false, true));
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 0, false, true));
        } else if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK) {
            // 命中地面或墙面：爆发蛛丝缠绕效果，施加小范围迟滞减速，不残留永久方块污染世界
            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                int webState = net.minecraft.block.Block.getStateId(Blocks.WEB.getDefaultState());
                ws.spawnParticle(EnumParticleTypes.BLOCK_DUST, hitVec.x, hitVec.y, hitVec.z, 35, 0.6, 0.4, 0.6, 0.05, webState);
            }
            List<EntityLivingBase> splashTargets = world.getEntitiesWithinAABB(EntityLivingBase.class,
                    new net.minecraft.util.math.AxisAlignedBB(hitVec.x - 2.5, hitVec.y - 1.5, hitVec.z - 2.5,
                            hitVec.x + 2.5, hitVec.y + 1.5, hitVec.z + 2.5),
                    e -> e != null && e != entity && e.isEntityAlive());
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

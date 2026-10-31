package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 苦力怕主动技能：引信蓄力自爆 (Creeper Fuse)。
 *
 * 玩法机制：
 * 1. 蓄力点火：进入 1.5 秒 (30 ticks) 引信蓄力状态，苦力怕模型开始剧烈膨胀闪白并发出急促引信嘶嘶声；
 * 2. 掐灭引信：蓄力期间若再次潜行使用该技能，可及时掐灭引信取消自爆；
 * 3. 终极爆炸与副作用：
 *    - 燃尽瞬间在玩家所在位置产生威力 3.0（高压充能状态下威力翻倍为 6.0）的真实环境大爆炸；
 *    - 生存模式下玩家将承受致命自爆伤害而死亡（真实苦力怕代价），创造模式下免死生存；
 *    - 爆炸后自动重置变身实体的膨胀与死亡状态，杜绝卡模型。
 */
public class CreeperFuseAction implements IAction, ICooldownAction {
    public static final String ID = "creeper_fuse";
    public static final int COOLDOWN = 300; // 15 秒（如果存活或创造模式）
    public static final int FUSE_TICKS = 30; // 1.5 秒

    private static final Set<UUID> PRIMED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 正在结算的自爆死亡伤害（一次性放行标记）：豁免管线不得拦截有意的自杀结算（CORE-06）。 */
    private static final Set<UUID> DETONATION_SETTLEMENT = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 自爆结算伤害是否处于放行窗口（MorphImmunityHandler 自伤豁免查询）。 */
    public static boolean isDetonationSettlement(EntityLivingBase entity) {
        return entity != null && DETONATION_SETTLEMENT.contains(entity.getUniqueID());
    }

    public static boolean isPrimed(EntityPlayer player) {
        return player != null && PRIMED_PLAYERS.contains(player.getUniqueID());
    }

    public static void clearPrimed(EntityPlayer player) {
        if (player != null) {
            PRIMED_PLAYERS.remove(player.getUniqueID());
        }
    }

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
        UUID uuid = entity.getUniqueID();

        // 潜行释放且当前正在蓄力中 -> 掐灭引信 (Defuse)
        if (entity.isSneaking() && PRIMED_PLAYERS.contains(uuid)) {
            PRIMED_PLAYERS.remove(uuid);
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.2F, 1.2F);
            entity.removePotionEffect(MobEffects.SLOWNESS);

            if (morph instanceof EntityMorph) {
                EntityLivingBase dummy = ((EntityMorph) morph).getEntity(world);
                if (dummy instanceof EntityCreeper) {
                    EntityCreeper creeper = (EntityCreeper) dummy;
                    creeper.setCreeperState(-1);
                    ReflectionHelper.setPrivateValue(EntityCreeper.class, creeper, 0, "timeSinceIgnited", "field_70833_d");
                }
            }
            if (entity instanceof EntityPlayer) {
                AbilityNetwork.sendCreeperSwelling((EntityPlayer) entity, false);
                ((EntityPlayer) entity).sendStatusMessage(new TextComponentTranslation("morphext.msg.creeper_defused"), true);
            }
            return;
        }

        if (PRIMED_PLAYERS.contains(uuid)) {
            // 已在蓄力中，避免重复触发
            return;
        }

        PRIMED_PLAYERS.add(uuid);

        // 1. 播放苦力怕引爆前标志性嘶嘶声与缓速
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.PLAYERS, 2.0F, 0.5F);
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, FUSE_TICKS, 2, false, false));

        // 2. 设置 dummy 实体状态为膨胀并网络同步至客户端
        boolean isPowered = false;
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity(world);
            if (dummy instanceof EntityCreeper) {
                EntityCreeper creeper = (EntityCreeper) dummy;
                isPowered = creeper.getPowered();
                creeper.setCreeperState(1);
            }
        }
        if (entity instanceof EntityPlayer) {
            AbilityNetwork.sendCreeperSwelling((EntityPlayer) entity, true);
        }

        final boolean poweredFinal = isPowered;

        // 3. 调度 1.5 秒蓄力引信与大爆炸
        ServerActionScheduler.scheduleTicker(FUSE_TICKS, remaining -> {
            if (!entity.isEntityAlive() || !PRIMED_PLAYERS.contains(uuid)) {
                return;
            }
            if (world instanceof WorldServer && remaining % 2 == 0) {
                WorldServer ws = (WorldServer) world;
                double spread = (FUSE_TICKS - remaining) * 0.025D;
                ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                        6, spread, entity.height * 0.3D, spread, 0.02D);
                ws.spawnParticle(EnumParticleTypes.FLAME, entity.posX, entity.posY + entity.height * 0.4D, entity.posZ,
                        2, spread * 0.5D, 0.1D, spread * 0.5D, 0.01D);
            }
        }, () -> {
            if (!PRIMED_PLAYERS.remove(uuid) || !entity.isEntityAlive()) {
                return;
            }

            double x = entity.posX;
            double y = entity.posY + entity.height * 0.3D;
            double z = entity.posZ;

            float power = poweredFinal ? 6.0F : 3.0F;
            boolean mobGriefing = world.getGameRules().getBoolean("mobGriefing");

            // 恢复 dummy Creeper 状态（避免留在 dead/swollen 卡死状态）
            if (morph instanceof EntityMorph) {
                EntityLivingBase dummy = ((EntityMorph) morph).getEntity(world);
                if (dummy instanceof EntityCreeper) {
                    EntityCreeper creeper = (EntityCreeper) dummy;
                    creeper.setCreeperState(-1);
                    creeper.isDead = false;
                    creeper.deathTime = 0;
                    ReflectionHelper.setPrivateValue(EntityCreeper.class, creeper, 0, "timeSinceIgnited", "field_70833_d");
                }
            }
            if (entity instanceof EntityPlayer) {
                AbilityNetwork.sendCreeperSwelling((EntityPlayer) entity, false);
            }

            // 4. 产生真实大爆炸
            Explosion explosion = world.createExplosion(entity, x, y, z, power, mobGriefing);

            // 5. 苦力怕真实代价副作用：生存模式下宿主因自爆而死亡！
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (!player.isCreative()) {
                    // 结算伤害走一次性放行标记：自伤豁免（投射物/爆炸自伤取消）不得拦截有意的自杀结算，
                    // 否则第一段必空、只能靠 OUT_OF_WORLD 兜底致死，死亡归因变成“掉出世界”（CORE-06）。
                    DamageSource deathSource = DamageSource.causeExplosionDamage(explosion);
                    DETONATION_SETTLEMENT.add(uuid);
                    try {
                        player.attackEntityFrom(deathSource, Float.MAX_VALUE);
                    } finally {
                        DETONATION_SETTLEMENT.remove(uuid);
                    }
                    if (player.isEntityAlive()) {
                        player.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
                    }
                }
            }
        });
    }
}

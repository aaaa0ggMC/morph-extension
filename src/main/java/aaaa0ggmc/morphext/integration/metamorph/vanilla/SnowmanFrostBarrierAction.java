package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雪傀儡专属动作：极地暴风雪凝结结界 (Snowman Frost Barrier)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntitySnowman} 极寒冰雪结界与反击御敌机制：
 * 1. 【暴风雪急冻爆发】：瞬间以自身为中心爆发刺骨寒流与呼啸雪风，周身涌现大量飞雪、冰霜与浓白冰云；
 * 2. 【5 格地面凝冰铺雪】：在半径 5 格内地表迅速凝结 1~3 层厚雪层 (Blocks.SNOW_LAYER)，并将明水急冻为自然消融的浮冰霜 (Blocks.FROSTED_ICE)；
 * 3. 【绝对领域击退与缓速】：将周围 6 格内所有非友方目标向外猛烈击退，造成 4 点魔法急冻伤害，并施加迟缓 III 与挖掘疲劳 II (持续 6 秒)；
 * 4. 【8 秒急冻冰霜护盾】：通过事件监听器减免 50% 受到的所有物理/近战伤害，且受击时反弹刺骨冰霜，使攻击者陷入迟缓 II (4秒) 并承受 3 点寒冰反弹伤害。
 */
public class SnowmanFrostBarrierAction implements IAction, ICooldownAction {
    public static final String ID = "snowman_frost_barrier";
    public static final String ALIAS_ID = "morphext:snowman_frost_barrier";
    public static final int COOLDOWN = 360; // 18 秒冷却 (360 ticks)
    public static final int SHIELD_DURATION_TICKS = 160; // 8 秒冰霜护盾 (160 ticks)

    private static final Map<UUID, Long> ACTIVE_BARRIERS = new ConcurrentHashMap<>();
    private static boolean listenerRegistered = false;

    public SnowmanFrostBarrierAction() {
        registerListener();
    }

    private static synchronized void registerListener() {
        if (!listenerRegistered) {
            listenerRegistered = true;
            MinecraftForge.EVENT_BUS.register(new FrostBarrierEventListener());
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
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        World world = player.world;

        player.swingArm(EnumHand.MAIN_HAND);

        // 1. 暴风雪呼啸与破冰音效
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.3F, 1.4F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 1.2F, 0.75F);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.WEATHER_RAIN, SoundCategory.PLAYERS, 1.0F, 1.6F);

        // 2. 密集飞雪与暴风雪气流微粒
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SNOW_SHOVEL,
                    player.posX, player.posY + 1.0D, player.posZ,
                    80, 2.5D, 1.2D, 2.5D, 0.12D);
            ws.spawnParticle(EnumParticleTypes.SNOWBALL,
                    player.posX, player.posY + 1.0D, player.posZ,
                    50, 2.0D, 1.0D, 2.0D, 0.15D);
            ws.spawnParticle(EnumParticleTypes.CLOUD,
                    player.posX, player.posY + 0.3D, player.posZ,
                    30, 2.2D, 0.4D, 2.2D, 0.05D);
        }

        // 3. 5 格地面凝雪成冰
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25) {
                    continue;
                }

                // 纵向寻找地表
                for (int y = py + 1; y >= py - 3; y--) {
                    BlockPos checkPos = new BlockPos(px + dx, y, pz + dz);
                    IBlockState state = world.getBlockState(checkPos);
                    Block block = state.getBlock();

                    // 水域急冻为霜冰
                    if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
                        world.setBlockState(checkPos, Blocks.FROSTED_ICE.getDefaultState(), 3);
                        break;
                    }

                    // 坚实地面铺上 1~3 层雪
                    BlockPos above = checkPos.up();
                    if (world.isAirBlock(above) && Blocks.SNOW_LAYER.canPlaceBlockAt(world, above)) {
                        int layers = Math.min(3, 1 + world.rand.nextInt(3));
                        world.setBlockState(above, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, layers), 3);
                        break;
                    }
                }
            }
        }

        // 4. 周围 6 格敌对生物强烈击退、迟缓与急冻伤害
        AxisAlignedBB enemyBox = player.getEntityBoundingBox().grow(6.0D, 3.0D, 6.0D);
        List<EntityLivingBase> enemies = world.getEntitiesWithinAABB(EntityLivingBase.class, enemyBox,
                e -> e != player && e.isEntityAlive() && !e.isOnSameTeam(player));

        int repelledCount = 0;
        for (EntityLivingBase enemy : enemies) {
            double dx = enemy.posX - player.posX;
            double dz = enemy.posZ - player.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= 6.0D) {
                if (dist > 0.05D) {
                    enemy.motionX += (dx / dist) * 0.75D;
                    enemy.motionY += 0.32D;
                    enemy.motionZ += (dz / dist) * 0.75D;
                    enemy.velocityChanged = true;
                }

                // 迟缓 III (6秒) + 挖掘疲劳 II (6秒)
                enemy.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 120, 2, false, true));
                enemy.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 120, 1, false, true));

                // 4 点魔法急冻伤害
                enemy.attackEntityFrom(DamageSource.causeIndirectMagicDamage(player, player), 4.0F);

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.SNOWBALL,
                            enemy.posX, enemy.posY + enemy.height * 0.5D, enemy.posZ,
                            12, 0.3D, 0.3D, 0.3D, 0.1D);
                }
                repelledCount++;
            }
        }

        // 5. 激活 8 秒急冻冰霜护盾
        ACTIVE_BARRIERS.put(player.getUniqueID(), world.getTotalWorldTime() + SHIELD_DURATION_TICKS);
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, SHIELD_DURATION_TICKS, 0, false, false));

        player.sendStatusMessage(new TextComponentTranslation(
                "morphext.action.snowman_frost_barrier.activated", repelledCount), true);
    }

    /**
     * 急冻冰霜护盾伤害减免与反伤监听器。
     */
    public static class FrostBarrierEventListener {
        @SubscribeEvent
        public void onLivingHurt(LivingHurtEvent event) {
            EntityLivingBase victim = event.getEntityLiving();
            if (victim == null || victim.world == null || victim.world.isRemote) {
                return;
            }

            Long expireTime = ACTIVE_BARRIERS.get(victim.getUniqueID());
            if (expireTime == null) {
                return;
            }

            long currentTime = victim.world.getTotalWorldTime();
            if (currentTime > expireTime) {
                ACTIVE_BARRIERS.remove(victim.getUniqueID());
                return;
            }

            DamageSource source = event.getSource();

            // 减免 50% 物理与非纯魔法/虚空/火焰伤害
            if (!source.isMagicDamage() && !source.isFireDamage() && !source.isDamageAbsolute() && !source.canHarmInCreative()) {
                event.setAmount(event.getAmount() * 0.5F);

                // 反弹刺骨冰霜给直接攻击者
                if (source.getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) source.getTrueSource();
                    if (attacker != victim && attacker.isEntityAlive()) {
                        attacker.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1, false, true)); // 迟缓 II (4秒)
                        attacker.attackEntityFrom(DamageSource.causeIndirectMagicDamage(victim, victim), 3.0F);

                        victim.world.playSound(null, victim.posX, victim.posY, victim.posZ,
                                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.9F, 1.6F);

                        if (victim.world instanceof WorldServer) {
                            ((WorldServer) victim.world).spawnParticle(EnumParticleTypes.SNOWBALL,
                                    attacker.posX, attacker.posY + attacker.height * 0.5D, attacker.posZ,
                                    15, 0.3D, 0.3D, 0.3D, 0.1D);
                        }
                    }
                }
            }
        }
    }
}

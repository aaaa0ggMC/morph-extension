package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentFrostWalker;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * 流髑极寒霜冻光环被动 (Stray Glacial Aura & Frost Walker Passive)。
 * <p>
 * 深度探究 {@link net.minecraft.entity.monster.EntityStray} 极寒冻气生理机制：
 * 1. 【极寒冰霜光环】：周身 5 格范围内的存活敌对生物持续遭受刺骨寒风侵袭，附加轻度缓慢 I 效果；
 * 2. 【踏水凝冰】：移动经过水面时，足下静止水源瞬间凝结为短暂存在的霜冰 (Blocks.FROSTED_ICE)，伴随冰霜结晶微粒。
 */
public class StrayFrostAuraPassive extends AbstractPassiveAbility {
    public static final String ID = "stray_frost_aura";

    public StrayFrostAuraPassive(String id) {
        super(id, "morphext.passive.stray_frost_aura.name", "morphext.passive.stray_frost_aura.desc", new ItemStack(Blocks.ICE));
    }

    public StrayFrostAuraPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player == null || player.world == null || player.world.isRemote || player.isDead) {
            return;
        }

        World world = player.world;

        // 1. 踏水短暂凝结为浮冰 (Frost Walker 机制，每 5 ticks 探测)
        if (player.ticksExisted % 5 == 0) {
            BlockPos footPos = new BlockPos(player.posX, player.posY - 0.2D, player.posZ);
            BlockPos surfacePos = new BlockPos(player);

            if (world.getBlockState(footPos).getMaterial() == Material.WATER
                    || world.getBlockState(surfacePos).getMaterial() == Material.WATER) {
                EnchantmentFrostWalker.freezeNearby(player, world, footPos, 2);
            }
            if (player.onGround) {
                EnchantmentFrostWalker.freezeNearby(player, world, surfacePos, 2);
            }

            // 足迹飘散微量霜花粒子
            if (world instanceof WorldServer && world.getBlockState(footPos).getBlock() == Blocks.FROSTED_ICE) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SNOWBALL,
                        player.posX, player.posY + 0.05D, player.posZ, 2, 0.2D, 0.02D, 0.2D, 0.01D);
            }
        }

        // 2. 5 格极寒侵袭光环（每 20 ticks = 1 秒持续刷新）
        if (player.ticksExisted % 20 == 0) {
            double radius = 5.0D;
            AxisAlignedBB aabb = player.getEntityBoundingBox().grow(radius, 2.5D, radius);
            List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb,
                    e -> e != player && e.isEntityAlive() && !e.isOnSameTeam(player));

            for (EntityLivingBase target : targets) {
                // 施加轻度缓慢 I (60 ticks = 3秒持续覆盖)
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0));

                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.SNOW_SHOVEL,
                            target.posX, target.posY + target.height * 0.5D, target.posZ,
                            4, 0.25D, 0.25D, 0.25D, 0.02D);
                }
            }
        }
    }
}

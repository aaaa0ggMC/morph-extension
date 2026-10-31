package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Random;
import java.util.UUID;

/**
 * 潜影贝闭壳防御与受击瞬移被动 (Shulker Shell Defense & Teleport Passive)。
 * 深度还原原版 EntityShulker 底层机制：
 * 1. 闭壳坚守 (+20 护甲并弹开箭矢与飞弹)：
 *    当玩家按住潜行 (Shift) 时闭壳进入防御状态，获得 +20 点护甲修饰符 (COVERED_ARMOR_BONUS)，
 *    并完全格挡反弹所有箭矢与投掷物；
 * 2. 窥探切换 (Peek)：潜行进出时触发原版开合壳音效 (BLOCK_SHULKER_BOX_CLOSE / OPEN)；
 * 3. 濒死瞬移：血量低于 50% 受击时有 25% 概率瞬移至安全方块 (tryTeleportToNewPosition)。
 */
public class ShulkerPassive extends AbstractPassiveAbility {
    public static final String ID = "shulker_defense";
    private static final UUID COVERED_ARMOR_UUID = UUID.fromString("7e0292f2-9434-48d5-a29f-9583af7df27f");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER =
            new AttributeModifier(COVERED_ARMOR_UUID, "Covered armor bonus", 20.0D, 0).setSaved(false);

    public ShulkerPassive(String id) {
        super(id, "morphext.passive.shulker_defense.name", "morphext.passive.shulker_defense.desc", ItemStack.EMPTY);
    }

    public ShulkerPassive() {
        this(ID);
    }

    @Override
    public void onApplied(EntityPlayer player, MorphContext context) {
        // 装载被动
    }

    @Override
    public void onRemoved(EntityPlayer player, MorphContext context) {
        IAttributeInstance armor = player.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        if (armor != null && armor.hasModifier(COVERED_ARMOR_MODIFIER)) {
            armor.removeModifier(COVERED_ARMOR_MODIFIER);
        }
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        IAttributeInstance armor = player.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        if (armor == null) return;

        boolean closed = player.isSneaking();
        boolean hasMod = armor.hasModifier(COVERED_ARMOR_MODIFIER);

        if (closed && !hasMod) {
            armor.applyModifier(COVERED_ARMOR_MODIFIER);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.PLAYERS, 1.2F, 0.9F);
        } else if (!closed && hasMod) {
            armor.removeModifier(COVERED_ARMOR_MODIFIER);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_SHULKER_BOX_OPEN, SoundCategory.PLAYERS, 1.2F, 0.9F);
        }
    }

    @Override
    public boolean onAttacked(EntityPlayer player, DamageSource source, float amount, MorphContext context) {
        if (source == null) return false;

        // 1. 闭壳状态弹开投掷物与箭矢 (EntityShulker.attackEntityFrom 原生判定)
        if (player.isSneaking() && (source.isProjectile() || source.getImmediateSource() instanceof EntityArrow)) {
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.PLAYERS, 1.4F, 1.1F);
            if (player.world instanceof WorldServer) {
                int purpurState = net.minecraft.block.Block.getStateId(Blocks.PURPUR_BLOCK.getDefaultState());
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.BLOCK_DUST,
                        player.posX, player.posY + player.height * 0.5D, player.posZ,
                        15, 0.3D, 0.3D, 0.3D, 0.05D, purpurState);
            }
            if (source.getImmediateSource() != null) {
                source.getImmediateSource().motionX = -source.getImmediateSource().motionX * 0.5D;
                source.getImmediateSource().motionZ = -source.getImmediateSource().motionZ * 0.5D;
            }
            return true; // 彻底免疫该投掷物伤害
        }

        // 2. 受击瞬移机制：生命值低于 50% 时 25% 概率瞬移 (tryTeleportToNewPosition)
        if (player.getHealth() < player.getMaxHealth() * 0.5F && player.getRNG().nextInt(4) == 0) {
            tryTeleport(player);
        }

        return false;
    }

    private void tryTeleport(EntityPlayer player) {
        World world = player.world;
        Random rand = player.getRNG();
        double startX = player.posX;
        double startY = player.posY;
        double startZ = player.posZ;

        for (int i = 0; i < 16; i++) {
            double targetX = startX + (rand.nextDouble() - 0.5D) * 16.0D;
            double targetY = Math.max(1, startY + (rand.nextInt(9) - 4));
            double targetZ = startZ + (rand.nextDouble() - 0.5D) * 16.0D;
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

            if (world.isAirBlock(targetPos) && world.isAirBlock(targetPos.up())
                    && world.getBlockState(targetPos.down()).isOpaqueCube()) {
                player.setPositionAndUpdate(targetX + 0.5D, targetY, targetZ + 0.5D);
                world.playSound(null, startX, startY, startZ,
                        SoundEvents.ENTITY_SHULKER_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_SHULKER_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                if (world instanceof WorldServer) {
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL,
                            startX, startY + 0.5D, startZ, 30, 0.4D, 0.4D, 0.4D, 0.1D);
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL,
                            player.posX, player.posY + 0.5D, player.posZ, 30, 0.4D, 0.4D, 0.4D, 0.1D);
                }
                break;
            }
        }
    }
}

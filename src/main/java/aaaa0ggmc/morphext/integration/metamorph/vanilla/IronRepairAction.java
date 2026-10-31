package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 铁傀儡专属动作：精铁自修 (Iron Repair)。
 * 手持铁锭敲击重铸身躯，消耗 1 个铁锭瞬间恢复 25 点生命值，伴随铁砧敲击与治疗粒子。
 * 若未手持铁锭则在 Action Bar 提示 "手持铁锭可修复"。
 */
public class IronRepairAction implements IAction, ICooldownAction {
    public static final String ID = "iron_repair";
    public static final int COOLDOWN_REPAIR = 100; // 5 秒
    public static final int COOLDOWN_PROMPT = 20; // 1 秒防连点提示

    private final Map<UUID, Integer> dynamicCooldowns = new ConcurrentHashMap<>();

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        if (entity != null) {
            Integer cd = this.dynamicCooldowns.remove(entity.getUniqueID());
            if (cd != null) {
                return cd;
            }
        }
        return 0;
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

        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();

        EnumHand ingotHand = null;
        if (!main.isEmpty() && main.getItem() == Items.IRON_INGOT) {
            ingotHand = EnumHand.MAIN_HAND;
        } else if (!off.isEmpty() && off.getItem() == Items.IRON_INGOT) {
            ingotHand = EnumHand.OFF_HAND;
        }

        if (ingotHand != null) {
            // 消耗 1 个铁锭
            if (!player.isCreative()) {
                ItemStack stack = player.getHeldItem(ingotHand);
                stack.shrink(1);
            }

            // 恢复 25 点生命值
            player.heal(25.0F);

            // 播放铁砧敲击重铸音效
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.2F, 1.0F);

            // 治疗与重铸火花粒子
            if (world instanceof WorldServer) {
                WorldServer ws = (WorldServer) world;
                ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                        player.posX, player.posY + 1.2D, player.posZ,
                        25, 0.5D, 0.8D, 0.5D, 0.1D);
                ws.spawnParticle(EnumParticleTypes.HEART,
                        player.posX, player.posY + 1.8D, player.posZ,
                        6, 0.4D, 0.4D, 0.4D, 0.1D);
                ws.spawnParticle(EnumParticleTypes.CRIT,
                        player.posX, player.posY + 1.0D, player.posZ,
                        15, 0.5D, 0.6D, 0.5D, 0.15D);
            }

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_REPAIR);
        } else {
            // 未手持铁锭提示
            player.sendStatusMessage(new TextComponentTranslation("morphext.action.iron_repair.prompt"), true);

            this.dynamicCooldowns.put(player.getUniqueID(), COOLDOWN_PROMPT);
        }
    }
}

package aaaa0ggmc.morphext.item;

import aaaa0ggmc.morphext.MorphExtension;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * 能力卷轴（整合包制作者工具）：带 NBT 指定能力 id，右键切换解锁状态。
 *
 * 两个版本：
 *  - 普通（ability_scroll）：移除能力但<b>保留</b>其状态数据——重新解锁后原地恢复
 *  - 强力（ability_scroll_powerful）：移除能力并<b>清除</b>其状态数据——
 *    对叠层/进度类能力这就是“移除副作用”：连积累一起清零
 *
 * NBT 两种写法（等价）：
 *   {MorphExtAbility:"morphext:sprint"}
 *   {morphext:{ability:"morphext:sprint"}}
 *
 * give 示例：
 *   /give @p aaaa0ggmc_morph_extension:ability_scroll 1 0 {MorphExtAbility:"morphext:sprint"}
 */
public class AbilityScrollItem extends Item {
    private final boolean powerful;

    public AbilityScrollItem(boolean powerful) {
        this.powerful = powerful;
        this.setTranslationKey(powerful ? "morphext.ability_scroll_powerful" : "morphext.ability_scroll");
        this.setRegistryName(new ResourceLocation(MorphExtension.MODID,
                powerful ? "ability_scroll_powerful" : "ability_scroll"));
        this.setCreativeTab(CreativeTabs.MISC);
        this.setMaxStackSize(1);
    }

    public boolean isPowerful() {
        return this.powerful;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        String abilityId = readAbilityId(player.getHeldItem(hand));
        if (abilityId == null || abilityId.isEmpty()) {
            return super.onItemRightClick(world, player, hand);
        }
        if (!aaaa0ggmc.morphext.ability.PlayerAbilityData.validId(abilityId)) {
            return new ActionResult<>(EnumActionResult.FAIL, player.getHeldItem(hand));
        }
        // 由原版右键物品协议带到服务端；只信任服务端玩家实际手持卷轴的 NBT。
        if (!world.isRemote && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            try {
                toggle((net.minecraft.entity.player.EntityPlayerMP) player, abilityId);
            } catch (RuntimeException error) {
                org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                        .error("Ability scroll operation failed; client permissions not changed", error);
                player.sendStatusMessage(new TextComponentTranslation("gui.morphext.data_failed"), true);
                return new ActionResult<>(EnumActionResult.FAIL, player.getHeldItem(hand));
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private void toggle(net.minecraft.entity.player.EntityPlayerMP player, String abilityId) {
        boolean remove = aaaa0ggmc.morphext.ability.ServerAbilityService.isAvailable(player, abilityId);
        boolean wipe = remove && this.powerful;
        aaaa0ggmc.morphext.ability.PlayerAbilityStore.update(player, data -> {
            data.setUnlocked(abilityId, !remove);
            if (wipe) { data.wipe(abilityId); }
        });
        aaaa0ggmc.morphext.ability.ServerAbilityService.Handler handler =
                aaaa0ggmc.morphext.ability.ServerAbilityService.handler(abilityId);
        boolean cleanupFailed = false;
        if (remove && handler != null) {
            try {
                handler.onRevoked(player, abilityId);
                if (wipe) { handler.onDataWiped(player, abilityId); }
            } catch (RuntimeException error) {
                cleanupFailed = true;
                org.apache.logging.log4j.LogManager.getLogger("morph-extension")
                        .error("Ability removal saved, but addon cleanup failed: {}", abilityId, error);
            }
        }
        aaaa0ggmc.morphext.network.AbilityNetwork.syncPlayer(player, wipe ? abilityId : "");
        String key = cleanupFailed ? "gui.morphext.cleanup_failed"
                : wipe ? "morphext.scroll.purged" : remove ? "morphext.scroll.removed" : "morphext.scroll.unlocked";
        player.sendStatusMessage(new TextComponentTranslation(key, abilityId), true);
    }

    /** 读取卷轴绑定的能力 id（兼容两种 NBT 写法）。 */
    public static String readAbilityId(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        if (tag.hasKey("MorphExtAbility")) {
            String id = tag.getString("MorphExtAbility");
            if (!id.isEmpty()) {
                return id;
            }
        }
        if (tag.hasKey("morphext")) {
            NBTTagCompound sub = tag.getCompoundTag("morphext");
            if (sub.hasKey("ability")) {
                String id = sub.getString("ability");
                if (!id.isEmpty()) {
                    return id;
                }
            }
        }
        return null;
    }
}

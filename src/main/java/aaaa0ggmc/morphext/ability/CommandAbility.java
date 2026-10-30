package aaaa0ggmc.morphext.ability;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** 聊天命令能力：激活时以玩家身份发送一条命令（服务端执行，联机可用）。 */
public class CommandAbility extends Ability {
    private final String command;

    public CommandAbility(String id, String displayName, ItemStack icon, String command) {
        super(id, displayName);
        this.icon(icon);
        this.command = command;
    }

    public String getCommand() {
        return this.command;
    }

    @Override
    public void onActivate(EntityPlayer player) {
        if (player instanceof EntityPlayerSP && this.command != null && !this.command.isEmpty()) {
            ((EntityPlayerSP) player).sendChatMessage(this.command);
        }
    }
}

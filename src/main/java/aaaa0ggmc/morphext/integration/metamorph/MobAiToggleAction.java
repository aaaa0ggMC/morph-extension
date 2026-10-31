package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.ability.MobAiToggleAbility;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import com.google.gson.JsonObject;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;

/**
 * Metamorph 主动动作适配：生物自主 AI 切换 (AI Toggle Action)。
 * 支持绑定至动作按键或能力轮盘。
 */
public class MobAiToggleAction implements IAction {
    public static final String ID = "ai_toggle";
    public static final String ALIAS_ID = "morphext:ai_toggle";

    @Override
    public void execute(EntityLivingBase entity, @Nullable AbstractMorph morph) {
        if (entity == null || entity.world.isRemote || !(entity instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) entity;
        JsonObject state = PlayerAbilityStore.get(player).getState(MobAiToggleAbility.ID);
        boolean enabled = !aaaa0ggmc.morphext.ability.CoreServerAbilities.isEnabled(state);
        state.addProperty("enabled", enabled);
        PlayerAbilityStore.update(player, data -> data.putState(MobAiToggleAbility.ID, state));

        TextComponentTranslation msg = enabled
                ? new TextComponentTranslation("morphext.ability.mob_ai.enabled")
                : new TextComponentTranslation("morphext.ability.mob_ai.disabled");
        player.sendStatusMessage(msg, true);

        float pitch = enabled ? 1.2F : 0.8F;
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.6F, pitch);
    }
}

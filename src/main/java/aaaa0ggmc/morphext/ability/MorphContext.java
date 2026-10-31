package aaaa0ggmc.morphext.ability;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * 变身上下文对象，在能力激活、被动触发或事件分发时提供当前变身实体的完整上下文信息。
 */
public class MorphContext {
    private final EntityPlayer player;
    private final AbstractMorph morph;
    private final String morphName;
    private final EntityLivingBase dummy;

    public MorphContext(EntityPlayer player, @Nullable AbstractMorph morph) {
        this.player = player;
        this.morph = morph;
        this.morphName = morph != null && morph.name != null ? morph.name : "";
        EntityLivingBase d = null;
        if (morph instanceof EntityMorph) {
            try {
                d = ((EntityMorph) morph).getEntity(player.world);
            } catch (Throwable ignored) {
            }
        }
        this.dummy = d;
    }

    public MorphContext(EntityPlayer player, @Nullable AbstractMorph morph, String morphName, @Nullable EntityLivingBase dummy) {
        this.player = player;
        this.morph = morph;
        this.morphName = morphName != null ? morphName : "";
        this.dummy = dummy;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    @Nullable
    public AbstractMorph getMorph() {
        return morph;
    }

    public String getMorphName() {
        return morphName;
    }

    @Nullable
    public EntityLivingBase getDummy() {
        return dummy;
    }

    public boolean hasMorph() {
        return morph != null;
    }
}

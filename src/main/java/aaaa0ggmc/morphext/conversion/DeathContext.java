package aaaa0ggmc.morphext.conversion;

import aaaa0ggmc.morphext.form.FormCategories;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 一次死亡结算的上下文：玩家、伤害来源、原始事件（调试路径可空）。
 *
 * 由 {@link ConversionPipeline} 创建并依次交给各 {@link ConversionStage}；
 * 阶段把「为什么没命中」写进 {@link #notes}，供管线输出诊断日志。
 */
public final class DeathContext {
    public final EntityPlayerMP player;
    public final DamageSource source;
    /** 原始死亡事件；命令调试路径（无事件触发）下为 null。 */
    public final LivingDeathEvent event;

    private final List<String> notes = new ArrayList<>();
    private String cachedFormId;
    private boolean formIdResolved;

    public DeathContext(EntityPlayerMP player, DamageSource source, LivingDeathEvent event) {
        this.player = player;
        this.source = source;
        this.event = event;
    }

    public void note(String reason) {
        if (reason != null && !reason.isEmpty()) {
            notes.add(reason);
        }
    }

    public List<String> notes() {
        return Collections.unmodifiableList(notes);
    }

    /** 玩家当前形态 id（未变身 / 取不到能力时返回 null）。 */
    public String formId() {
        if (!formIdResolved) {
            formIdResolved = true;
            try {
                IMorphing morphing = Morphing.get(player);
                if (morphing != null && morphing.isMorphed() && morphing.getCurrentMorph() != null) {
                    cachedFormId = FormCategories.normalize(morphing.getCurrentMorph().name);
                }
            } catch (Throwable ignored) {
                cachedFormId = null;
            }
        }
        return cachedFormId;
    }

    /** 玩家当前形态所属类别。 */
    public Set<String> categories() {
        return FormCategories.categoriesOf(formId());
    }

    /** 死亡是否由玩家自己造成（自伤/自爆）：这类死亡默认不触发转化，除非规则显式打开。 */
    public boolean isSelfCaused() {
        if (source == null) {
            return false;
        }
        return source.getTrueSource() == player || source.getImmediateSource() == player;
    }

    /** 取消原始死亡事件（有事件时）。 */
    public void cancelDeath() {
        if (event != null) {
            event.setCanceled(true);
        }
    }

    /** 恢复原始死亡事件（执行失败时回退，让玩家正常死亡）。 */
    public void uncancelDeath() {
        if (event != null) {
            event.setCanceled(false);
        }
    }

    /** 实体 id（EntityList 注册名），取不到返回 null。 */
    public static String entityId(Entity entity) {
        if (entity == null) {
            return null;
        }
        net.minecraft.util.ResourceLocation key = net.minecraft.entity.EntityList.getKey(entity);
        return key == null ? null : key.toString();
    }
}

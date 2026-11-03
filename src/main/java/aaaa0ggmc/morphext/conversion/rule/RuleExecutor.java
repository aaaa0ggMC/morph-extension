package aaaa0ggmc.morphext.conversion.rule;

import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.conversion.ConversionEvent;
import aaaa0ggmc.morphext.conversion.DeathContext;
import aaaa0ggmc.morphext.conversion.MorphFactory;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import com.google.gson.JsonObject;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 规则执行：命中后把「死亡」换成「转化」，一次做完：
 *   取消死亡 -&gt; 变身 -&gt; 置 1 点血 -&gt; 死法残留清理 -&gt; 消耗献祭物 -&gt;
 *   表现（标题/粒子/音效）-&gt; 记录（morphext 状态 + ConversionEvent）-&gt; 日志。
 */
public final class RuleExecutor {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");

    private RuleExecutor() {
    }

    public static boolean execute(ConversionRule rule, DeathContext ctx) {
        EntityPlayerMP player = ctx.player;
        ctx.cancelDeath();
        try {
            player.getCombatTracker().reset();

            AbstractMorph morph = MorphFactory.buildFromFormId(rule.target, player.world);
            if (morph == null) {
                LOGGER.error("[morphext] Conversion rule {}: no morph available for target {}",
                        rule.id, rule.target);
                ctx.uncancelDeath();
                return false;
            }
            MorphAPI.acquire(player, morph);
            MorphAPI.morph(player, morph, true);

            // 转化后只给 1 点血，绝不回满：回满血=刷血漏洞（尸壳埋沙反复触发 sand_burial
            // 即可无限回满）。metamorph 变身时按比例折算血量，死亡取消后残血接近 0，
            // 这里显式置 1.0F：既防刷血，又保证不留下 0 血/Float.MIN_VALUE 残留立刻二次死亡。
            player.setHealth(1.0F);

            ResidueCleanup.cleanup(player, ctx.source, rule);
            consume(player, rule);
            effects(player, rule);
            record(player, rule);

            MinecraftForge.EVENT_BUS.post(new ConversionEvent(player, rule.id, rule.target));
            LOGGER.info("[morphext] conversion rule hit: id={} form={} player={}",
                    rule.id, rule.target, player.getName());
            return true;
        } catch (Throwable t) {
            LOGGER.error("[morphext] Conversion rule {} failed", rule.id, t);
            // 死亡已被取消：兜底给 1 点血，避免留下 0 血活人立刻二次死亡。
            // 与成功路径同语义——转化只给 1 点血，异常兜底同样不得回满（防刷血）。
            player.setHealth(1.0F);
            return false;
        }
    }

    private static void consume(EntityPlayerMP player, ConversionRule rule) {
        if (rule.item != null && rule.item.consume) {
            EnumHand hand = RuleMatcher.findHeld(player, rule.item);
            if (hand != null) {
                ItemStack stack = player.getHeldItem(hand);
                stack.shrink(1);
                if (rule.item.leave != null) {
                    Item leave = Item.getByNameOrId(rule.item.leave);
                    if (leave != null) {
                        ItemStack rest = new ItemStack(leave);
                        if (stack.isEmpty()) {
                            player.setHeldItem(hand, rest);
                        } else if (!player.addItemStackToInventory(rest)) {
                            player.dropItem(rest, false);
                        }
                    }
                }
            }
        }
        for (String id : rule.consume) {
            removeOne(player, id);
        }
    }

    private static void removeOne(EntityPlayerMP player, String rawId) {
        Item want = Item.getByNameOrId(rawId);
        if (want == null) {
            return;
        }
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (!stack.isEmpty() && stack.getItem() == want) {
                stack.shrink(1);
                return;
            }
        }
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack stack = player.inventory.mainInventory.get(i);
            if (!stack.isEmpty() && stack.getItem() == want) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static void effects(EntityPlayerMP player, ConversionRule rule) {
        if (rule.title != null && !rule.title.isEmpty()) {
            player.sendStatusMessage(new TextComponentTranslation(rule.title), true);
        }
        if (rule.subtitle != null && !rule.subtitle.isEmpty()) {
            player.sendMessage(new TextComponentTranslation(rule.subtitle));
        }
        if (rule.particle != null && player.world instanceof WorldServer) {
            net.minecraft.util.EnumParticleTypes particle =
                    net.minecraft.util.EnumParticleTypes.getByName(stripNamespace(rule.particle));
            if (particle != null) {
                ((WorldServer) player.world).spawnParticle(particle,
                        player.posX, player.posY + player.height / 2.0D, player.posZ,
                        30, 0.5D, 0.6D, 0.5D, 0.02D);
            }
        }
        if (rule.sound != null && !rule.sound.isEmpty()) {
            try {
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        new SoundEvent(new ResourceLocation(rule.sound)),
                        SoundCategory.PLAYERS, 0.8F, 0.9F);
            } catch (Throwable ignored) {
                // 音效 id 写错不阻断转化
            }
        }
    }

    private static String stripNamespace(String raw) {
        int i = raw.indexOf(':');
        return i >= 0 ? raw.substring(i + 1) : raw;
    }

    /** 写 morphext 玩家状态（进度任务读这个键）+ 发事件给监听方。 */
    private static void record(EntityPlayerMP player, ConversionRule rule) {
        if (rule.advancement == null || rule.advancement.isEmpty()) {
            return;
        }
        try {
            JsonObject state = new JsonObject();
            state.addProperty("rule", rule.id);
            state.addProperty("form", rule.target);
            state.addProperty("tick", PlayerAbilityStore.now(player));
            PlayerAbilityStore.update(player, data -> data.putState(rule.advancement, state));
            AbilityNetwork.syncPlayer(player, "");
        } catch (Throwable t) {
            LOGGER.warn("[morphext] Failed to record conversion state for {}: {}", rule.id, t.toString());
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.profiler.ProfilerBridge;
import mchorse.metamorph.api.events.MorphEvent;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 变身状态下的药水与伤害免疫事件处理器（JSON 数据驱动 + 深度防护）。
 *
 * 核心防御特性：
 * 1. 药水与伤害别名深度规范化（自动匹配 abyssalcraft:dplague, cplague, shoggoth_acid 等）；
 * 2. 彻底阻断变身玩家的投射物自伤（Self-Hit Immunity）；
 * 3. 暗影形态绝对免疫失明，防范 Sacthoth（萨克索斯）30 格硬编码失明光环反噬；
 * 4. 周期性（每 10 tick）自动清除变身免疫的残留药水；
 * 5. 拦截针对玩家的 Integer.MAX_VALUE 恶意秒杀打击。
 */
public final class MorphImmunityHandler {
    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new MorphImmunityHandler());
        }
    }

    private AbstractMorph getCurrentMorph(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) {
            return null;
        }
        IMorphing morphing = Morphing.get((EntityPlayer) entity);
        return morphing == null ? null : morphing.getCurrentMorph();
    }

    /**
     * 判断当前形态是否免疫指定的药水效果（含别名与特权防护）
     */
    private boolean isImmuneToPotion(String morphName, MorphAbilityDefinition def, Potion potion) {
        if (potion == null) {
            return false;
        }

        // 萨克索斯 / 暗影生物防范：所有暗影形态强制免疫失明
        if (potion == MobEffects.BLINDNESS && morphName != null && morphName.toLowerCase().contains("shadow")) {
            return true;
        }

        if (def == null || def.immunities.isEmpty()) {
            return false;
        }

        ResourceLocation rl = potion.getRegistryName();
        String registryName = rl != null ? rl.toString().toLowerCase() : "";
        String unlocalizedName = potion.getName().toLowerCase();
        String path = rl != null ? rl.getPath().toLowerCase() : "";

        for (String immunity : def.immunities) {
            String imm = immunity.toLowerCase();
            // 直接匹配
            if (imm.equals(registryName) || imm.equals(unlocalizedName) || imm.equals(path)) {
                return true;
            }

            // 恐惧瘟疫别名互通
            if ((imm.contains("dread") || imm.contains("dplague"))
                    && (registryName.contains("dplague") || unlocalizedName.contains("dplague") || path.contains("dplague"))) {
                return true;
            }

            // 珊瑚瘟疫别名互通
            if ((imm.contains("coralium") || imm.contains("cplague"))
                    && (registryName.contains("cplague") || unlocalizedName.contains("cplague") || path.contains("cplague"))) {
                return true;
            }

            // 反物质别名互通
            if (imm.contains("antimatter")
                    && (registryName.contains("antimatter") || unlocalizedName.contains("antimatter") || path.contains("antimatter"))) {
                return true;
            }

            // 失明别名互通
            if (imm.contains("blindness")
                    && (potion == MobEffects.BLINDNESS || unlocalizedName.contains("blindness") || path.contains("blindness"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断当前形态是否免疫指定的伤害来源（含通用 Flag、自伤与深渊专用 DamageSource）
     */
    private boolean isImmuneToDamage(String morphName, MorphAbilityDefinition def, DamageSource source, EntityLivingBase victim) {
        if (source == null) {
            return false;
        }

        // 1. 变身自伤彻底防护 (Self-Hit Immunity)
        // 当发射者/伤害制造者是受害玩家自身时，阻断所有投射物、爆炸与本体系伤害
        if (victim instanceof EntityPlayer) {
            if (source.getTrueSource() == victim || source.getImmediateSource() == victim) {
                if (source.isProjectile() || source.isExplosion()) {
                    return true;
                }
                String dt = source.getDamageType().toLowerCase();
                if (dt.contains("dread") || dt.contains("acid") || dt.contains("coralium") || dt.contains("antimatter") || dt.contains("shadow")) {
                    return true;
                }
            }
        }

        // 2. 拦截针对玩家的恶意秒杀打击 (如 Integer.MAX_VALUE 破第四面墙)
        //    具体模组（如 AbyssalCraft 的 J'zahar）在各自 patch 中声明，核心不硬编码形态名。

        if (def == null || def.damageImmunities.isEmpty()) {
            return false;
        }

        String damageType = source.getDamageType().toLowerCase();

        for (String imm : def.damageImmunities) {
            String immLower = imm.toLowerCase();

            // 基础 damageType 比对
            if (immLower.equals(damageType)) {
                return true;
            }

            // 修格斯酸液 damageType: "shoggoth_acid" 与 "acid" 互通
            if ((immLower.contains("acid") || immLower.contains("shoggoth_acid"))
                    && (damageType.contains("acid") || damageType.contains("shoggoth_acid"))) {
                return true;
            }

            // 恐惧伤害互通
            if (immLower.contains("dread") && damageType.contains("dread")) {
                return true;
            }

            // 珊瑚伤害互通
            if (immLower.contains("coralium") && damageType.contains("coralium")) {
                return true;
            }

            // 反物质伤害互通
            if (immLower.contains("antimatter") && damageType.contains("antimatter")) {
                return true;
            }

            // 暗影伤害互通
            if (immLower.contains("shadow") && damageType.contains("shadow")) {
                return true;
            }

            // 通用 Flag 映射
            if ((immLower.equals("fire") || immLower.equals("infire") || immLower.equals("onfire") || immLower.equals("lava") || immLower.equals("hotfloor"))
                    && source.isFireDamage()) {
                return true;
            }

            if (immLower.equals("fall") && source == DamageSource.FALL) {
                return true;
            }

            if (immLower.equals("drown") && source == DamageSource.DROWN) {
                return true;
            }

            if (immLower.equals("inwall") && source == DamageSource.IN_WALL) {
                return true;
            }

            if (immLower.equals("cactus") && source == DamageSource.CACTUS) {
                return true;
            }

            if (immLower.equals("explosion") && source.isExplosion()) {
                return true;
            }

            if (immLower.equals("magic") && source.isMagicDamage() && !source.canHarmInCreative()) {
                return true;
            }
        }

        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        long start = ProfilerBridge.begin();
        try {
            AbstractMorph morph = getCurrentMorph(event.getEntityLiving());
            if (morph == null || morph.name == null) {
                return;
            }

            PotionEffect effect = event.getPotionEffect();
            if (effect == null || effect.getPotion() == null) {
                return;
            }

            MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
            if (isImmuneToPotion(morph.name, def, effect.getPotion())) {
                event.setResult(Event.Result.DENY);
            }
        } finally {
            ProfilerBridge.end("morphext:immunity_potion", start);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        long start = ProfilerBridge.begin();
        try {
            AbstractMorph morph = getCurrentMorph(event.getEntityLiving());
            if (morph == null || morph.name == null) {
                return;
            }

            MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
            if (isImmuneToDamage(morph.name, def, event.getSource(), event.getEntityLiving())) {
                event.setCanceled(true);
            }
        } finally {
            ProfilerBridge.end("morphext:immunity_attack", start);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        AbstractMorph morph = getCurrentMorph(event.getEntityLiving());
        if (morph == null || morph.name == null) {
            return;
        }

        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        if (isImmuneToDamage(morph.name, def, event.getSource(), event.getEntityLiving())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    /**
     * 周期性主动清除变身免疫的残留药水效果（每 10 tick 一次），杜绝非事件途径感染与上线残留
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }

        if (entity.ticksExisted % 10 != 0) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        AbstractMorph morph = getCurrentMorph(player);
        if (morph == null || morph.name == null) {
            return;
        }

        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        List<Potion> toRemove = null;

        for (PotionEffect active : player.getActivePotionEffects()) {
            if (active != null && isImmuneToPotion(morph.name, def, active.getPotion())) {
                if (toRemove == null) {
                    toRemove = new ArrayList<>();
                }
                toRemove.add(active.getPotion());
            }
        }

        if (toRemove != null) {
            for (Potion p : toRemove) {
                player.removePotionEffect(p);
            }
        }
    }

    @SubscribeEvent
    public void onMorphChange(MorphEvent.Post event) {
        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote) {
            return;
        }

        AbstractMorph morph = event.morph;
        if (morph == null || morph.name == null) {
            return;
        }

        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        List<Potion> toRemove = new ArrayList<>();
        for (PotionEffect active : player.getActivePotionEffects()) {
            if (active != null && isImmuneToPotion(morph.name, def, active.getPotion())) {
                toRemove.add(active.getPotion());
            }
        }

        for (Potion p : toRemove) {
            player.removePotionEffect(p);
        }
    }

    private MorphImmunityHandler() {
    }
}

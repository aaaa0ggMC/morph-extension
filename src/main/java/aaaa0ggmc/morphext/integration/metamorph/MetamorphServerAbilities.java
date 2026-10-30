package aaaa0ggmc.morphext.integration.metamorph;

import aaaa0ggmc.morphext.ability.ActivationResult;
import aaaa0ggmc.morphext.ability.ServerAbilityService;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.events.MorphActionEvent;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;

/** 仅由 common 侧在检测到 Metamorph 时加载。不依赖任何客户端 UI/注册表。 */
public final class MetamorphServerAbilities implements ServerAbilityService.Handler {
    private static final String MORPH = "metamorph:morph:";
    private static final String GRANTED = "metamorph:granted:";

    private MetamorphServerAbilities() { }

    public static void register() {
        ServerAbilityService.register("metamorph:", new MetamorphServerAbilities());
    }

    @Override
    public boolean isAvailable(EntityPlayerMP player, String id) {
        IMorphing morphing = Morphing.get(player);
        if (morphing == null) { return false; }
        if ("metamorph:demorph".equals(id)) {
            return !aaaa0ggmc.morphext.config.ModConfig.overwriteMetamorph || player.isCreative();
        }
        if (id.startsWith(MORPH)) {
            if (aaaa0ggmc.morphext.config.ModConfig.overwriteMetamorph && !player.isCreative()) {
                return false;
            }
            for (AbstractMorph acquired : morphing.getAcquiredMorphs()) {
                if (acquired != null && id.equals(MORPH + acquired.name)) { return true; }
            }
            return false;
        }
        AbstractMorph current = morphing.getCurrentMorph();
        if (current == null) { return false; }
        String prefix = GRANTED + current.name + ":";
        if (!id.startsWith(prefix)) { return false; }
        String name = id.substring(prefix.length());

        // 检查数据驱动的 JSON 配置能力
        MorphAbilityDefinition def = MorphJsonConfig.get(current.name);
        if (def != null && def.action != null && def.action.equals(name)) {
            return true;
        }

        if (current.getSettings() == null) { return false; }
        if (current.getSettings().action != null && name.equals(nameOf(MorphManager.INSTANCE.actions,
                current.getSettings().action, "action"))) { return true; }
        if (current.getSettings().attack != null && name.equals(nameOf(MorphManager.INSTANCE.attacks,
                current.getSettings().attack, "attack"))) { return true; }
        if (current.getSettings().abilities != null) {
            for (mchorse.metamorph.api.abilities.IAbility passive : current.getSettings().abilities) {
                if (passive != null && name.equals(nameOf(MorphManager.INSTANCE.abilities, passive,
                        passive.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT)))) { return true; }
            }
        }
        return "mob_act".equals(name) && current instanceof EntityMorph
                && MorphManager.INSTANCE.actions.containsKey("ai_target");
    }

    private static String nameOf(Map<String, ?> registry, Object instance, String fallback) {
        for (Map.Entry<String, ?> entry : registry.entrySet()) {
            if (entry.getValue() == instance) { return entry.getKey(); }
        }
        return fallback;
    }

    @Override
    public ActivationResult activate(EntityPlayerMP player, String id) {
        if (player.isSpectator()) {
            return ActivationResult.unavailable();
        }
        IMorphing morphing = Morphing.get(player);
        if (morphing == null) {
            return ActivationResult.unavailable();
        }
        if ("metamorph:demorph".equals(id)) {
            if (aaaa0ggmc.morphext.config.ModConfig.overwriteMetamorph && !player.isCreative()) {
                return ActivationResult.unavailable();
            }
            MorphAPI.demorph(player);
            return ActivationResult.success(0);
        }
        if (id.startsWith(MORPH)) {
            if (aaaa0ggmc.morphext.config.ModConfig.overwriteMetamorph && !player.isCreative()) {
                return ActivationResult.unavailable();
            }
            String name = id.substring(MORPH.length());
            // 不接受客户端任意指定生物；只允许服务端已获取的 morph。
            for (AbstractMorph acquired : morphing.getAcquiredMorphs()) {
                if (acquired != null && name.equals(acquired.name)) {
                    MorphAPI.morph(player, acquired, true);
                    return ActivationResult.success(0);
                }
            }
            return new ActivationResult(ActivationResult.Status.LOCKED, 0);
        }
        AbstractMorph current = morphing.getCurrentMorph();
        if (current == null || current.getSettings() == null) {
            return ActivationResult.unavailable();
        }
        String prefix = GRANTED + current.name + ":";
        if (!id.startsWith(prefix)) {
            return ActivationResult.unavailable(); // 变身已变化/旧形态能力请求。
        }
        String name = id.substring(prefix.length());
        IAction action = current.getSettings() != null ? current.getSettings().action : null;
        boolean matches = false;
        if (action != null) {
            String registeredName = "action";
            for (Map.Entry<String, IAction> entry : MorphManager.INSTANCE.actions.entrySet()) {
                if (entry.getValue() == action) {
                    registeredName = entry.getKey();
                    break;
                }
            }
            matches = registeredName.equals(name);
        }
        if (!matches) {
            // 检查 JSON 数据驱动配置的 action
            MorphAbilityDefinition def = MorphJsonConfig.get(current.name);
            if (def != null && def.action != null && def.action.equals(name)) {
                action = MorphManager.INSTANCE.actions.get(def.action);
                if (action != null) {
                    matches = true;
                }
            }
        }
        if (!matches && "mob_act".equals(name) && current instanceof EntityMorph) {
            try {
                action = (IAction) Class.forName("jones.metamorphmax.metamorph.actions.AITarget")
                        .getDeclaredConstructor().newInstance();
                matches = true;
            } catch (ReflectiveOperationException error) {
                return ActivationResult.unavailable();
            }
        }
        if (!matches) {
            return ActivationResult.unavailable();
        }
        int remaining = externalCooldown(player);
        if (remaining > 0) {
            return new ActivationResult(ActivationResult.Status.COOLDOWN, remaining);
        }
        action.execute(player, current);
        MinecraftForge.EVENT_BUS.post(new MorphActionEvent(player, action, current));
        return ActivationResult.success(externalCooldown(player));
    }

    /** 服务端权威冷却，不把客户端预测时间当作放行依据。 */
    private static int externalCooldown(EntityPlayerMP player) {
        try {
            Potion potion = (Potion) Class.forName("jones.metamorphmax.init.PotionInit")
                    .getField("MOB_ABILITY_COOLDOWN").get(null);
            PotionEffect effect = potion == null ? null : player.getActivePotionEffect(potion);
            return effect == null ? 0 : effect.getDuration();
        } catch (ReflectiveOperationException error) {
            return 0;
        }
    }
}

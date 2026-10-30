package aaaa0ggmc.morphext.integration.metamorph;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import mchorse.metamorph.api.events.MorphEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 变身状态下的药水与伤害免疫事件处理器（由 JSON 数据驱动）。
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

    @SubscribeEvent
    public void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        AbstractMorph morph = getCurrentMorph(event.getEntityLiving());
        if (morph == null || morph.name == null) {
            return;
        }

        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        if (def == null || def.immunities.isEmpty()) {
            return;
        }

        PotionEffect effect = event.getPotionEffect();
        if (effect == null || effect.getPotion() == null) {
            return;
        }

        Potion potion = effect.getPotion();
        ResourceLocation rl = potion.getRegistryName();
        String registryName = rl != null ? rl.toString() : "";
        String unlocalizedName = potion.getName();

        for (String immunity : def.immunities) {
            if (immunity.equalsIgnoreCase(registryName)
                    || immunity.equalsIgnoreCase(unlocalizedName)
                    || (rl != null && immunity.equalsIgnoreCase(rl.getPath()))) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        AbstractMorph morph = getCurrentMorph(event.getEntityLiving());
        if (morph == null || morph.name == null) {
            return;
        }

        MorphAbilityDefinition def = MorphJsonConfig.get(morph.name);
        if (def == null || def.damageImmunities.isEmpty()) {
            return;
        }

        String damageType = event.getSource().getDamageType();
        for (String imm : def.damageImmunities) {
            if (imm.equalsIgnoreCase(damageType)) {
                event.setCanceled(true);
                return;
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
        if (def == null || def.immunities.isEmpty()) {
            return;
        }

        // 变身瞬间，自动清除该形态免疫的所有负面效果
        List<Potion> toRemove = new ArrayList<>();
        for (PotionEffect active : player.getActivePotionEffects()) {
            Potion potion = active.getPotion();
            ResourceLocation rl = potion.getRegistryName();
            String regName = rl != null ? rl.toString() : "";
            String unloc = potion.getName();
            for (String immunity : def.immunities) {
                if (immunity.equalsIgnoreCase(regName)
                        || immunity.equalsIgnoreCase(unloc)
                        || (rl != null && immunity.equalsIgnoreCase(rl.getPath()))) {
                    toRemove.add(potion);
                    break;
                }
            }
        }

        for (Potion p : toRemove) {
            player.removePotionEffect(p);
        }
    }

    private MorphImmunityHandler() {
    }
}

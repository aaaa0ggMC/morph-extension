package aaaa0ggmc.morphext.ability;

import aaaa0ggmc.morphext.ability.passive.DamageImmunityPassive;
import aaaa0ggmc.morphext.ability.passive.PotionImmunityPassive;
import aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition;
import aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 变身能力生命周期调度中枢 (Morph Ability Lifecycle Manager)。
 * 统一管理主动与被动能力的装载、卸载、Tick 驱动、伤害结算、药水过滤及属性同步。
 */
public final class MorphAbilityManager {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static final MorphAbilityManager INSTANCE = new MorphAbilityManager();
    private static boolean registered = false;

    private final Map<UUID, List<IPassiveAbility>> activePassives = new ConcurrentHashMap<>();
    private final Map<UUID, List<IActiveAbility>> activeActives = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentMorphs = new ConcurrentHashMap<>();

    private MorphAbilityManager() {}

    public static MorphAbilityManager getInstance() {
        return INSTANCE;
    }

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            LOGGER.info("[morphext] MorphAbilityManager registered to Forge event bus");
        }
    }

    public List<IPassiveAbility> getActivePassives(EntityPlayer player) {
        if (player == null) return Collections.emptyList();
        List<IPassiveAbility> list = activePassives.get(player.getUniqueID());
        return list != null ? list : Collections.emptyList();
    }

    public List<IActiveAbility> getActiveActives(EntityPlayer player) {
        if (player == null) return Collections.emptyList();
        List<IActiveAbility> list = activeActives.get(player.getUniqueID());
        return list != null ? list : Collections.emptyList();
    }

    public List<IAbility> getAllActiveAbilities(EntityPlayer player) {
        if (player == null) return Collections.emptyList();
        List<IAbility> result = new ArrayList<>();
        result.addAll(getActiveActives(player));
        result.addAll(getActivePassives(player));
        return result;
    }

    /**
     * 根据形态名称解析并装配其所有统一能力（主动 + 被动 + 声明式免疫）
     */
    public List<IAbility> resolveAbilitiesForMorph(String morphName) {
        if (morphName == null || morphName.isEmpty()) return Collections.emptyList();
        List<IAbility> resolved = new ArrayList<>();

        MorphAbilityDefinition def = MorphJsonConfig.get(morphName);
        if (def == null) return Collections.emptyList();

        // 1. 解析主动能力 (actives)
        List<String> actionList = new ArrayList<>();
        if (def.action != null && !def.action.isEmpty()) actionList.add(def.action);
        if (def.actions != null) {
            for (String a : def.actions) {
                if (a != null && !a.isEmpty() && !actionList.contains(a)) {
                    actionList.add(a);
                }
            }
        }
        for (String actId : actionList) {
            IAbility ab = AbilityRegistry.get(actId);
            if (ab == null) ab = AbilityRegistry.get("morphext:" + actId);
            if (ab != null && !resolved.contains(ab)) {
                resolved.add(ab);
            }
        }

        // 2. 解析被动能力 (passives)
        if (def.abilities != null) {
            for (String pId : def.abilities) {
                IAbility ab = AbilityRegistry.get(pId);
                if (ab == null) ab = AbilityRegistry.get("morphext:" + pId);
                if (ab != null && !resolved.contains(ab)) {
                    resolved.add(ab);
                }
            }
        }

        // 3. 解析药水免疫被动 (immunities)
        if (def.immunities != null && !def.immunities.isEmpty()) {
            String immId = "morphext:immunity_potion_" + morphName.replace(':', '_');
            IAbility existing = AbilityRegistry.get(immId);
            if (existing instanceof IPassiveAbility) {
                if (!resolved.contains(existing)) resolved.add(existing);
            } else {
                PotionImmunityPassive pip = new PotionImmunityPassive(immId,
                        "morphext.passive.immunity_potion.name",
                        "morphext.passive.immunity_potion.desc",
                        ItemStack.EMPTY,
                        def.immunities.toArray(new String[0]));
                AbilityRegistry.register(pip);
                resolved.add(pip);
            }
        }

        // 4. 解析伤害免疫被动 (damageImmunities)
        if (def.damageImmunities != null && !def.damageImmunities.isEmpty()) {
            String immId = "morphext:immunity_damage_" + morphName.replace(':', '_');
            IAbility existing = AbilityRegistry.get(immId);
            if (existing instanceof IPassiveAbility) {
                if (!resolved.contains(existing)) resolved.add(existing);
            } else {
                DamageImmunityPassive dip = new DamageImmunityPassive(immId,
                        "morphext.passive.immunity_damage.name",
                        "morphext.passive.immunity_damage.desc",
                        ItemStack.EMPTY,
                        true,
                        def.damageImmunities.toArray(new String[0]));
                AbilityRegistry.register(dip);
                resolved.add(dip);
            }
        }

        return resolved;
    }

    /**
     * 变身状态变动处理：安全触发旧形态的 onRemoved，并挂载新形态的 onApplied
     */
    public void handleMorphChange(EntityPlayer player, String oldMorphName, String newMorphName, AbstractMorph newMorph) {
        UUID uuid = player.getUniqueID();
        MorphContext oldContext = new MorphContext(player, null, oldMorphName, null);

        // 1. 卸载旧形态的所有被动
        List<IPassiveAbility> oldPassives = activePassives.remove(uuid);
        if (oldPassives != null) {
            for (IPassiveAbility p : oldPassives) {
                try {
                    p.onRemoved(player, oldContext);
                } catch (Throwable t) {
                    LOGGER.error("[morphext] Error onRemoved for ability {} on player {}", p.getId(), player.getName(), t);
                }
            }
        }
        activeActives.remove(uuid);

        if (newMorphName == null || newMorphName.isEmpty() || newMorph == null) {
            currentMorphs.remove(uuid);
            return;
        }

        currentMorphs.put(uuid, newMorphName);
        MorphContext newContext = new MorphContext(player, newMorph);

        // 2. 解析新形态的所有能力
        List<IAbility> all = resolveAbilitiesForMorph(newMorphName);
        List<IPassiveAbility> nextPassives = new ArrayList<>();
        List<IActiveAbility> nextActives = new ArrayList<>();

        for (IAbility ab : all) {
            if (ab instanceof IPassiveAbility) {
                nextPassives.add((IPassiveAbility) ab);
            } else if (ab instanceof IActiveAbility) {
                nextActives.add((IActiveAbility) ab);
            } else if (ab.isPassive()) {
                // 通用被动包装
                nextPassives.add(new LegacyPassiveWrapper(ab));
            }
        }

        activePassives.put(uuid, nextPassives);
        activeActives.put(uuid, nextActives);

        // 3. 触发新被动的 onApplied
        for (IPassiveAbility p : nextPassives) {
            try {
                p.onApplied(player, newContext);
            } catch (Throwable t) {
                LOGGER.error("[morphext] Error onApplied for ability {} on player {}", p.getId(), player.getName(), t);
            }
        }

        LOGGER.info("[morphext] Mounted {} passives and {} actives for player {} ({})",
                nextPassives.size(), nextActives.size(), player.getName(), newMorphName);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null || event.player.isDead) {
            return;
        }
        EntityPlayer player = event.player;
        UUID uuid = player.getUniqueID();

        IMorphing morphing = Morphing.get(player);
        AbstractMorph currentMorph = morphing != null ? morphing.getCurrentMorph() : null;
        String morphName = currentMorph != null && currentMorph.name != null ? currentMorph.name : "";

        String recorded = currentMorphs.get(uuid);
        if (!morphName.equals(recorded != null ? recorded : "")) {
            handleMorphChange(player, recorded, morphName, currentMorph);
        }

        List<IPassiveAbility> passives = activePassives.get(uuid);
        if (passives != null && !passives.isEmpty()) {
            MorphContext context = new MorphContext(player, currentMorph);
            for (IPassiveAbility p : passives) {
                try {
                    p.onUpdate(player, context);
                } catch (Throwable t) {
                    LOGGER.error("[morphext] Error onUpdate for ability {} on player {}", p.getId(), player.getName(), t);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            List<IPassiveAbility> passives = activePassives.get(player.getUniqueID());
            if (passives != null && !passives.isEmpty()) {
                MorphContext context = new MorphContext(player, null, currentMorphs.get(player.getUniqueID()), null);
                for (IPassiveAbility p : passives) {
                    try {
                        if (p.onAttacked(player, event.getSource(), event.getAmount(), context)) {
                            event.setCanceled(true);
                            return;
                        }
                    } catch (Throwable t) {
                        LOGGER.error("[morphext] Error in onAttacked for ability {}", p.getId(), t);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        // 1. 受击防御/免疫分发
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            List<IPassiveAbility> passives = activePassives.get(player.getUniqueID());
            if (passives != null && !passives.isEmpty()) {
                MorphContext context = new MorphContext(player, null, currentMorphs.get(player.getUniqueID()), null);
                for (IPassiveAbility p : passives) {
                    try {
                        if (p.onAttacked(player, event.getSource(), event.getAmount(), context)) {
                            event.setCanceled(true);
                            event.setAmount(0.0F);
                            return;
                        }
                    } catch (Throwable t) {
                        LOGGER.error("[morphext] Error in onAttacked for ability {}", p.getId(), t);
                    }
                }

                // 受到伤害数值的修饰与减免计算
                float currentAmount = event.getAmount();
                for (IPassiveAbility p : passives) {
                    try {
                        currentAmount = p.onHurt(player, event.getSource(), currentAmount, context);
                    } catch (Throwable t) {
                        LOGGER.error("[morphext] Error in onHurt for ability {}", p.getId(), t);
                    }
                }
                if (currentAmount <= 0.0F) {
                    event.setCanceled(true);
                    event.setAmount(0.0F);
                    return;
                } else if (currentAmount != event.getAmount()) {
                    event.setAmount(currentAmount);
                }
            }
        }

        // 2. 攻击命中特效/判定分发
        DamageSource src = event.getSource();
        if (src != null && src.getTrueSource() instanceof EntityPlayer) {
            EntityPlayer attacker = (EntityPlayer) src.getTrueSource();
            List<IPassiveAbility> passives = activePassives.get(attacker.getUniqueID());
            if (passives != null && !passives.isEmpty()) {
                MorphContext context = new MorphContext(attacker, null, currentMorphs.get(attacker.getUniqueID()), null);
                for (IPassiveAbility p : passives) {
                    try {
                        p.onAttack(attacker, event.getEntityLiving(), src, event.getAmount(), context);
                    } catch (Throwable t) {
                        LOGGER.error("[morphext] Error in onAttack for ability {}", p.getId(), t);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            List<IPassiveAbility> passives = activePassives.get(player.getUniqueID());
            if (passives != null && !passives.isEmpty()) {
                MorphContext context = new MorphContext(player, null, currentMorphs.get(player.getUniqueID()), null);
                for (IPassiveAbility p : passives) {
                    try {
                        if (p.onPotionApplicable(player, event.getPotionEffect(), context)) {
                            event.setResult(Event.Result.DENY);
                            return;
                        }
                    } catch (Throwable t) {
                        LOGGER.error("[morphext] Error in onPotionApplicable for ability {}", p.getId(), t);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            handleMorphChange(event.player, currentMorphs.get(event.player.getUniqueID()), null, null);
        }
    }

    /**
     * 兼容旧版 IAbility 被动包装器
     */
    private static class LegacyPassiveWrapper extends AbstractPassiveAbility {
        private final IAbility delegate;

        LegacyPassiveWrapper(IAbility delegate) {
            super(delegate.getId());
            this.delegate = delegate;
        }

        @Override
        public String getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public ItemStack getIcon() {
            return delegate.getIcon();
        }
    }
}

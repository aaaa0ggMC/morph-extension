package aaaa0ggmc.morphext.variant;

import aaaa0ggmc.morphext.ability.HumanStatureAbility;
import aaaa0ggmc.morphext.variant.vanilla.AgeableVariantHandler;

import mchorse.metamorph.api.events.AcquireMorphEvent;
import mchorse.metamorph.api.events.MorphEvent;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * 变种生命周期事件监听器：
 * 1. 捕获形态时规范化 NBT 与初始化 scale；
 * 2. 变身/解除变身时动态注入最大生命值、属性修改器；
 * 3. 动态维护玩家的缩放碰撞箱与视线高度（EyeHeight）。
 */
public final class VariantEventListener {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new VariantEventListener());
        LOGGER.info("[morphext] VariantEventListener registered to EVENT_BUS");
    }

    /**
     * 捕获形态预处理：清洗脏 NBT，同步变种 Scale。
     */
    @SubscribeEvent
    public void onAcquireMorphPre(AcquireMorphEvent.Pre event) {
        AbstractMorph morph = event.morph;
        if (morph == null || !(morph instanceof EntityMorph)) {
            return;
        }
        EntityMorph em = (EntityMorph) morph;
        IVariantHandler handler = VariantRegistry.getHandler(em);
        NBTTagCompound data = em.getEntityData();
        if (data != null && handler != null) {
            handler.sanitizeEntityData(data);
        }
        float scale = 1.0F;
        if (handler != null) {
            String variantKey = handler.getVariantKey(em.getEntity(), data);
            scale = handler.getScale(variantKey, data);
        } else if (data != null && data.hasKey("morphext_scale")) {
            scale = data.getFloat("morphext_scale");
        } else if (em.scale > 0.0F && Math.abs(em.scale - 1.0F) > 0.001F) {
            scale = em.scale;
        }

        if (scale > 0.0F && Math.abs(scale - 1.0F) > 0.001F) {
            em.scale = scale;
            LOGGER.debug("[morphext] Applied variant scale {} to acquired morph {}", scale, morph.name);
        }
    }

    /**
     * 变身发生与解除变身处理：动态属性修改器与生命值。
     */
    @SubscribeEvent
    public void onMorphPost(MorphEvent.Post event) {
        EntityPlayer player = event.player;
        if (player == null) {
            return;
        }

        // 无论变身为新形态还是 demorph，先安全移除既有的变种修改器
        clearModifiers(player);

        AbstractMorph morph = event.morph;
        if (morph == null) {
            // 解除变身：恢复默认尺寸与进入变身前的移速 base（否则 morph 的 settings.speed 会永久残留）
            player.eyeHeight = player.getDefaultEyeHeight();
            restorePreMorphSpeed(player);
            return;
        }

        // 记录进入变身前的 MOVEMENT_SPEED base（仅首次记录，morph 间切换不覆盖），demorph 时恢复
        notePreMorphSpeed(player);

        // 全面同步生物的攻击力、护甲、韧性、击退抗性等战斗属性 (可通过 morph_abilities.json 自定义)
        aaaa0ggmc.morphext.ability.morph.MorphAttributeSyncHandler.applyAttributes(player, morph);

        // 潜影贝防原地定死与 0 移速生物保底修复
        mchorse.metamorph.api.MorphSettings morphSettings = morph.getSettings();
        if (morphSettings != null) {
            aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition def =
                    aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig.get(morph.name);
            if (def != null && def.speed != null) {
                morphSettings.speed = def.speed;
                morphSettings.hasSpeed = true;
            } else if (morphSettings.speed <= 0.001F) {
                morphSettings.speed = 0.08F;
                morphSettings.hasSpeed = true;
            }
            if (morphSettings.speed != 0.1F) {
                player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(morphSettings.speed);
            }
        }
        if (morph instanceof mchorse.vanilla_pack.morphs.ShulkerMorph) {
            ((mchorse.vanilla_pack.morphs.ShulkerMorph) morph).blockPos = null;
        }

        IVariantHandler handler = VariantRegistry.getHandler(morph);
        NBTTagCompound data = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = MorphVariantHelper.getVariantKey(morph);

        // 1. 血量权威解析（绝对覆盖 > 变体动态 > settings.health）：一次性应用并保持血量比例换算；
        //    此后由 MorphAbilityManager 每 tick 权威收敛，metamorph Morphing.update 的每 tick 写入与之同值。
        if (!player.world.isRemote && morph.getSettings() != null) {
            aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition def =
                    aaaa0ggmc.morphext.integration.metamorph.MorphJsonConfig.get(morph.name);
            float targetHealth = resolveTargetMaxHealth(player, morph, def);
            applyDynamicHealth(player, morph, targetHealth);
        }

        if (handler != null) {
            // 2. 属性修改器注入（如移速加成）
            Map<IAttribute, AttributeModifier> modifiers = handler.getAttributeModifiers(variantKey, data);
            if (modifiers != null && !modifiers.isEmpty()) {
                for (Map.Entry<IAttribute, AttributeModifier> entry : modifiers.entrySet()) {
                    IAttributeInstance inst = player.getEntityAttribute(entry.getKey());
                    if (inst != null && !inst.hasModifier(entry.getValue())) {
                        inst.applyModifier(entry.getValue());
                    }
                }
            }

            // 3. 初始碰撞箱与 EyeHeight 校准
            float scale = handler.getScale(variantKey, data);
            if (scale > 0.0F && Math.abs(scale - 1.0F) > 0.001F) {
                updatePlayerScale(player, morph, scale);
            }
        } else if (morph instanceof EntityMorph) {
            EntityMorph em = (EntityMorph) morph;
            float scale = 1.0F;
            if (data != null && data.hasKey("morphext_scale")) {
                scale = data.getFloat("morphext_scale");
                em.scale = scale;
            } else if (em.scale > 0.0F && Math.abs(em.scale - 1.0F) > 0.001F) {
                scale = em.scale;
            }
            if (scale > 0.0F && Math.abs(scale - 1.0F) > 0.001F) {
                updatePlayerScale(player, morph, scale);
            }
        }
    }

    /**
     * 每 tick 校准变种缩放后的碰撞箱与 EyeHeight。
     */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null) {
            return;
        }

        IMorphing morphing = Morphing.get(player);
        if (morphing == null) {
            return;
        }
        AbstractMorph current = morphing.getCurrentMorph();
        if (current == null) {
            return;
        }

        // 身形规整 (Human Stature) 对所有变身生效，无论该生物是否有变种处理器
        HumanStatureAbility.Mode statureMode = getStatureMode(player);
        if (statureMode == HumanStatureAbility.Mode.HUMAN_MODEL
                || statureMode == HumanStatureAbility.Mode.SMART_SCALE) {
            // 唯一写入方：与渲染帧节奏的 pinStatureBox 同值（LOWEST 收尾覆盖 Metamorph 的形态尺寸写入）
            pinStatureBox(player);
            return;
        }

        IVariantHandler handler = VariantRegistry.getHandler(current);
        NBTTagCompound data = (current instanceof EntityMorph) ? ((EntityMorph) current).getEntityData() : null;
        String variantKey = MorphVariantHelper.getVariantKey(current);
        float scale = 1.0F;

        if (handler != null) {
            scale = handler.getScale(variantKey, data);
        } else if (current instanceof EntityMorph) {
            EntityMorph em = (EntityMorph) current;
            if (data != null && data.hasKey("morphext_scale")) {
                scale = data.getFloat("morphext_scale");
                em.scale = scale;
            } else if (em.scale > 0.0F && Math.abs(em.scale - 1.0F) > 0.001F) {
                scale = em.scale;
            }
        }

        if (scale > 0.0F && Math.abs(scale - 1.0F) > 0.001F) {
            updatePlayerScale(player, current, scale);
        }
    }

    private static void updatePlayerScale(EntityPlayer player, AbstractMorph morph, float scale) {
        HumanStatureAbility.Mode statureMode = getStatureMode(player);
        if (statureMode != HumanStatureAbility.Mode.OFF) {
            // 单一写入方：HUMAN_MODEL / SMART_SCALE 统一走 pinStatureBox（含姿态感知尺寸）
            pinStatureBox(player);
            return;
        }

        if (scale <= 0.0F || Math.abs(scale - 1.0F) < 0.001F) {
            return;
        }
        // 基于变身实体的实际尺寸缩放 Hitbox
        float baseWidth = morph.getWidth(player);
        float baseHeight = morph.getHeight(player);

        float newWidth = Math.max(0.2F, baseWidth * scale);
        float newHeight = Math.max(0.4F, baseHeight * scale);

        setBox(player, newWidth, newHeight, Math.max(0.2F, morph.getEyeHeight(player) * scale));
    }

    /**
     * 每 tick（PlayerTickEvent.END，LOWEST 收尾）与每渲染帧（RenderTickEvent）双节奏钉住
     * HUMAN_MODEL / SMART_SCALE 模式下的碰撞箱与视线高度，作为该模式下唯一写入方；
     * Metamorph AbstractMorph.update 每 tick 写入的形态实体尺寸被本方法条件覆盖。
     * <p>
     * 姿态感知（对齐原版 EntityPlayer.updateSize）：潜行 1.65 高、鞘翅 0.6×0.6、睡眠 0.2×0.2，
     * 不再恒写 0.6×1.8 覆盖潜行/鞘翅尺寸（CORE-02 残余收口）。
     */
    public static void pinStatureBox(EntityPlayer player) {
        if (player == null) {
            return;
        }
        HumanStatureAbility.Mode mode = getStatureMode(player);
        float width = 0.6F;
        float height = 1.8F;
        if (mode == HumanStatureAbility.Mode.HUMAN_MODEL
                || mode == HumanStatureAbility.Mode.SMART_SCALE) {
            if (mode == HumanStatureAbility.Mode.SMART_SCALE) {
                IMorphing morphing = Morphing.get(player);
                AbstractMorph current = morphing == null ? null : morphing.getCurrentMorph();
                float baseH = current == null ? 1.8F : current.getHeight(player);
                float baseW = current == null ? 0.6F : current.getWidth(player);
                float ratio = baseH > 0.1F ? (baseW / baseH) : 0.33F;
                width = Math.max(0.6F, Math.min(1.0F, 1.8F * ratio));
            }
            if (player.isElytraFlying()) {
                width = 0.6F;
                height = 0.6F;
            } else if (player.isPlayerSleeping()) {
                width = 0.2F;
                height = 0.2F;
            } else if (player.isSneaking()) {
                height = 1.65F;
            }
            setBox(player, width, height, player.getDefaultEyeHeight());
        }
    }

    static void setBox(EntityPlayer player, float w, float h, float eyeHeight) {
        if (Math.abs(player.width - w) > 0.01F || Math.abs(player.height - h) > 0.01F) {
            player.width = w;
            player.height = h;
            double halfW = w / 2.0D;
            player.setEntityBoundingBox(new net.minecraft.util.math.AxisAlignedBB(
                    player.posX - halfW, player.posY, player.posZ - halfW,
                    player.posX + halfW, player.posY + h, player.posZ + halfW));
        }
        player.eyeHeight = eyeHeight;
    }

    public static HumanStatureAbility.Mode getStatureMode(EntityPlayer player) {
        if (player == null) {
            return HumanStatureAbility.Mode.OFF;
        }
        if (player.world.isRemote) {
            return HumanStatureAbility.getClientMode();
        }
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            com.google.gson.JsonObject state = aaaa0ggmc.morphext.ability.PlayerAbilityStore.getLoadedState(
                    (net.minecraft.entity.player.EntityPlayerMP) player, HumanStatureAbility.ID);
            if (state.has("mode") && state.get("mode").isJsonPrimitive()) {
                return HumanStatureAbility.Mode.fromId(state.get("mode").getAsInt());
            }
        }
        return HumanStatureAbility.Mode.OFF;
    }

    /**
     * 血量权威解析：最终最大生命 = def.attributes.maxHealth（绝对覆盖）→ 变体动态血量 → settings.health。
     * 供变身一次性应用（onMorphPost）与 MorphAbilityManager 每 tick 权威收敛共用，保证两条路径同值。
     */
    public static float resolveTargetMaxHealth(EntityPlayer player, AbstractMorph morph,
                                               aaaa0ggmc.morphext.integration.metamorph.MorphAbilityDefinition def) {
        Double override = aaaa0ggmc.morphext.ability.morph.MorphAttributeSyncHandler.getMaxHealthOverride(def);
        if (override != null) {
            return (float) Math.round(override);
        }
        Float dynamic = resolveVariantMaxHealth(player, morph);
        if (dynamic != null) {
            return dynamic;
        }
        return morph.getSettings() != null ? morph.getSettings().health : 20.0F;
    }

    /**
     * 变体动态血量解析（如幼年 60%、变体固定血量）；无变体或换算结果与原始一致时返回 null。
     * 换算基准必须是形态原始血量——已收敛的 settings.health 不能重复代入（60% 变体会逐级塌缩），
     * 故 EntityMorph 优先取捕获实体的最大生命作为原始基准。
     */
    public static Float resolveVariantMaxHealth(EntityPlayer player, AbstractMorph morph) {
        if (player == null || morph == null) {
            return null;
        }
        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return null;
        }
        NBTTagCompound data = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = MorphVariantHelper.getVariantKey(morph);
        float pristine = pristineMaxHealth(player, morph);
        float dynamic = handler.getMaxHealth(variantKey, data, pristine);
        return (dynamic > 0.0F && dynamic != pristine) ? dynamic : null;
    }

    /** 形态原始血量（变体换算基准）：EntityMorph 取捕获实体最大生命，其余回退 settings.health。 */
    private static float pristineMaxHealth(EntityPlayer player, AbstractMorph morph) {
        if (morph instanceof EntityMorph) {
            EntityLivingBase dummy = ((EntityMorph) morph).getEntity(player.world);
            if (dummy != null && dummy.getMaxHealth() > 0.5F) {
                return dummy.getMaxHealth();
            }
        }
        return morph.getSettings() != null ? morph.getSettings().health : 20.0F;
    }

    /**
     * 应用目标最大生命：同时写 settings.health 与 MAX_HEALTH base（metamorph 每 tick 写入同一值），
     * 当前血量按既有比例语义换算并钳制在新上限内（防血条溢出）。
     */
    private static void applyDynamicHealth(EntityPlayer player, AbstractMorph morph, float targetMaxHealth) {
        float oldMax = player.getMaxHealth();
        float current = player.getHealth();
        float ratio = oldMax > 0.0F ? (current / oldMax) : 1.0F;

        mchorse.metamorph.api.MorphSettings settings = morph.getSettings();
        if (settings != null) {
            settings.health = Math.round(targetMaxHealth);
        }
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(targetMaxHealth);
        player.setHealth(Math.max(1.0F, Math.min(targetMaxHealth, targetMaxHealth * ratio)));
    }

    private static void clearModifiers(EntityPlayer player) {
        aaaa0ggmc.morphext.ability.morph.MorphAttributeSyncHandler.clearAttributes(player);
        IAttributeInstance speedInst = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedInst != null) {
            speedInst.removeModifier(AgeableVariantHandler.BABY_SPEED_MODIFIER_UUID);
        }
    }

    /** 进入变身前的 MOVEMENT_SPEED base 备份（仅记录第一次，demorph 时恢复）。 */
    private static final Map<java.util.UUID, Float> PRE_MORPH_SPEED = new java.util.concurrent.ConcurrentHashMap<>();

    /** 记录进入变身前的移速 base；已记录（morph 间切换）时不覆盖，保住真正的变身前值。 */
    public static void notePreMorphSpeed(EntityPlayer player) {
        if (player == null) {
            return;
        }
        IAttributeInstance inst = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (inst != null) {
            PRE_MORPH_SPEED.putIfAbsent(player.getUniqueID(), (float) inst.getBaseValue());
        }
    }

    /**
     * demorph 恢复移速 base（CORE-12）：变身期间 morph settings.speed 直写 base，
     * 不复位会让 shulker(0.08)/villager(0.133) 等永久残留。无备份时回退原版玩家 0.1。
     */
    public static void restorePreMorphSpeed(EntityPlayer player) {
        if (player == null) {
            return;
        }
        Float pre = PRE_MORPH_SPEED.remove(player.getUniqueID());
        IAttributeInstance inst = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.setBaseValue(pre != null ? pre : 0.1D);
        }
    }

    /** 登出清理，避免静态备份表按 UUID 泄漏。 */
    @SubscribeEvent
    public void onPlayerLogout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            PRE_MORPH_SPEED.remove(event.player.getUniqueID());
        }
    }
}

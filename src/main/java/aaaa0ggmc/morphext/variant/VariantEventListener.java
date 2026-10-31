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
            // 解除变身：恢复默认尺寸
            player.eyeHeight = player.getDefaultEyeHeight();
            return;
        }

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

        if (handler != null) {
            // 1. 动态生命值调整
            if (!player.world.isRemote && morph.getSettings() != null) {
                float defHealth = morph.getSettings().health;
                float dynamicHealth = handler.getMaxHealth(variantKey, data, defHealth);
                if (dynamicHealth > 0.0F && dynamicHealth != defHealth) {
                    applyDynamicHealth(player, dynamicHealth);
                }
            }

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
        if (statureMode == HumanStatureAbility.Mode.HUMAN_MODEL) {
            setBox(player, 0.6F, 1.8F, player.getDefaultEyeHeight());
            return;
        }
        if (statureMode == HumanStatureAbility.Mode.SMART_SCALE) {
            float baseH = current.getHeight(player);
            float baseW = current.getWidth(player);
            float ratio = baseH > 0.1F ? (baseW / baseH) : 0.33F;
            float smartW = Math.max(0.6F, Math.min(1.0F, 1.8F * ratio));
            setBox(player, smartW, 1.8F, 1.62F);
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
        if (statureMode == HumanStatureAbility.Mode.HUMAN_MODEL) {
            setBox(player, 0.6F, 1.8F, player.getDefaultEyeHeight());
            return;
        }

        if (statureMode == HumanStatureAbility.Mode.SMART_SCALE) {
            float baseH = morph.getHeight(player);
            float baseW = morph.getWidth(player);
            float ratio = baseH > 0.1F ? (baseW / baseH) : 0.33F;
            float smartW = Math.max(0.6F, Math.min(1.0F, 1.8F * ratio));
            setBox(player, smartW, 1.8F, 1.62F);
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

    private static void setBox(EntityPlayer player, float w, float h, float eyeHeight) {
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

    private static void applyDynamicHealth(EntityPlayer player, float dynamicMaxHealth) {
        float oldMax = player.getMaxHealth();
        float current = player.getHealth();
        float ratio = oldMax > 0.0F ? (current / oldMax) : 1.0F;

        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(dynamicMaxHealth);
        player.setHealth(Math.max(1.0F, dynamicMaxHealth * ratio));
    }

    private static void clearModifiers(EntityPlayer player) {
        aaaa0ggmc.morphext.ability.morph.MorphAttributeSyncHandler.clearAttributes(player);
        IAttributeInstance speedInst = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedInst != null) {
            speedInst.removeModifier(AgeableVariantHandler.BABY_SPEED_MODIFIER_UUID);
        }
    }
}

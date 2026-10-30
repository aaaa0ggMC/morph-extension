package aaaa0ggmc.morphext.variant;

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
        if (handler == null) {
            return;
        }
        NBTTagCompound data = em.getEntityData();
        if (data != null) {
            handler.sanitizeEntityData(data);
        }
        String variantKey = handler.getVariantKey(em.getEntity(), data);
        float scale = handler.getScale(variantKey, data);
        if (scale > 0.0F && scale != 1.0F) {
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

        IVariantHandler handler = VariantRegistry.getHandler(morph);
        if (handler == null) {
            return;
        }

        NBTTagCompound data = (morph instanceof EntityMorph) ? ((EntityMorph) morph).getEntityData() : null;
        String variantKey = MorphVariantHelper.getVariantKey(morph);

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
        updatePlayerScale(player, morph, handler, variantKey, data);
    }

    /**
     * 每 tick 校准变种缩放后的碰撞箱与 EyeHeight。
     */
    @SubscribeEvent
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

        IVariantHandler handler = VariantRegistry.getHandler(current);
        if (handler == null) {
            return;
        }

        NBTTagCompound data = (current instanceof EntityMorph) ? ((EntityMorph) current).getEntityData() : null;
        String variantKey = MorphVariantHelper.getVariantKey(current);
        float scale = handler.getScale(variantKey, data);

        if (scale > 0.0F && scale != 1.0F) {
            updatePlayerScale(player, current, handler, variantKey, data);
        }
    }

    private static void updatePlayerScale(EntityPlayer player, AbstractMorph morph,
                                         IVariantHandler handler, String variantKey, NBTTagCompound data) {
        float scale = handler.getScale(variantKey, data);
        if (scale <= 0.0F || scale == 1.0F) {
            return;
        }
        // 基于变身实体的实际尺寸缩放 Hitbox
        float baseWidth = morph.getWidth(player);
        float baseHeight = morph.getHeight(player);

        float newWidth = Math.max(0.2F, baseWidth * scale);
        float newHeight = Math.max(0.4F, baseHeight * scale);

        // 如果尺寸有变化，更新玩家判定箱
        if (Math.abs(player.width - newWidth) > 0.01F || Math.abs(player.height - newHeight) > 0.01F) {
            player.width = newWidth;
            player.height = newHeight;
            double halfW = newWidth / 2.0D;
            player.setEntityBoundingBox(new net.minecraft.util.math.AxisAlignedBB(
                    player.posX - halfW, player.posY, player.posZ - halfW,
                    player.posX + halfW, player.posY + newHeight, player.posZ + halfW));
        }

        // 校准视线高度（EyeHeight）
        float defEye = morph.getEyeHeight(player);
        player.eyeHeight = Math.max(0.2F, defEye * scale);
    }

    private static void applyDynamicHealth(EntityPlayer player, float dynamicMaxHealth) {
        float oldMax = player.getMaxHealth();
        float current = player.getHealth();
        float ratio = oldMax > 0.0F ? (current / oldMax) : 1.0F;

        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(dynamicMaxHealth);
        player.setHealth(Math.max(1.0F, dynamicMaxHealth * ratio));
    }

    private static void clearModifiers(EntityPlayer player) {
        IAttributeInstance speedInst = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedInst != null) {
            speedInst.removeModifier(AgeableVariantHandler.BABY_SPEED_MODIFIER_UUID);
        }
    }
}

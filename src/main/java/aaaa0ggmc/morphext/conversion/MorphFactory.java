package aaaa0ggmc.morphext.conversion;

import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * 实体 / 形态 id -&gt; Metamorph 形态（通用工厂）。
 *
 * 与 Metamorph 自身「击杀获取形态」同一套路径（morphNameFromEntity +
 * morphFromNBT），只是把入口从「击杀者实体」推广到任意实体或形态 id。
 */
public final class MorphFactory {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");

    private MorphFactory() {
    }

    /** 从实体构建形态；失败返回 null（不抛）。 */
    public static AbstractMorph buildFromEntity(Entity source) {
        if (source == null) {
            return null;
        }
        try {
            String name = MorphManager.INSTANCE.morphNameFromEntity(source);
            if (name == null || !MorphManager.INSTANCE.hasMorph(name)) {
                return null;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Name", name);
            tag.setTag("EntityData", EntityUtils.stripEntityNBT(source.serializeNBT()));
            return MorphManager.INSTANCE.morphFromNBT(tag);
        } catch (Throwable t) {
            LOGGER.warn("[morphext] Failed to build morph from entity: {}", t.toString());
            return null;
        }
    }

    /** 从形态 id 构建（临时造一个实体取默认数据）；失败返回 null。 */
    public static AbstractMorph buildFromFormId(String formId, World world) {
        ResourceLocation id = normalize(formId);
        if (id == null || world == null) {
            return null;
        }
        try {
            Entity temp = EntityList.createEntityByIDFromName(id, world);
            return temp == null ? null : buildFromEntity(temp);
        } catch (Throwable t) {
            LOGGER.warn("[morphext] Failed to build morph from form id {}: {}", formId, t.toString());
            return null;
        }
    }

    /** 形态 id 归一：去空白、小写、无命名空间补 minecraft:。 */
    public static ResourceLocation normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return new ResourceLocation(s.contains(":") ? s : "minecraft:" + s);
    }
}

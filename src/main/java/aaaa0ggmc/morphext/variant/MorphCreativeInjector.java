package aaaa0ggmc.morphext.variant;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.events.ReloadMorphs;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 创造模式形态选单与检索增强注入器 (Morph Creative Search & Variant Injector)。
 *
 * 架构规范：
 * 1. 采用插件式 SPI 架构：通过 registerProvider() 接收各补丁模组（如 morphext-patch-abyssalcraft）注入的专属复杂变种；
 * 2. 统一管理并注入到 Metamorph 创造模式选单列表与检索系统；
 * 3. 增强搜索关键词匹配，变种形态拥有清晰的中英文双语 DisplayName；
 * 4. 防御性安全检查，规避 GUI 模型渲染异常。
 */
@SideOnly(Side.CLIENT)
public final class MorphCreativeInjector {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static boolean registered = false;

    // 变种提供者注册列表
    private static final List<ICreativeVariantProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private MorphCreativeInjector() {
    }

    /**
     * 注册自定义变种提供者（供 addon/patch 模组调用）
     */
    public static void registerProvider(ICreativeVariantProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
            LOGGER.info("[morphext] Registered creative variant provider: {}", provider.getClass().getName());
        }
    }

    /**
     * 注册总线监听
     */
    public static synchronized void register() {
        if (registered) return;
        registered = true;

        // 注册原版自带幼年变体提供者
        registerProvider(new VanillaBabyVariantProvider());

        MinecraftForge.EVENT_BUS.register(new MorphCreativeInjector());
        LOGGER.info("[morphext] MorphCreativeInjector registered into Forge EVENT_BUS");
    }

    @SubscribeEvent
    public void onReloadMorphs(ReloadMorphs event) {
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) {
            return;
        }

        try {
            injectAllVariants();
        } catch (Throwable t) {
            LOGGER.error("[morphext] Error occurred while injecting creative variants", t);
        }
    }

    private static void injectAllVariants() {
        List<MorphSection> sections = MorphManager.INSTANCE.list.sections;
        if (sections == null || sections.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc != null ? mc.world : null;

        // 聚合所有 provider 提供的变体定义
        List<CreativeVariantDef> allDefs = new ArrayList<>();
        for (ICreativeVariantProvider provider : PROVIDERS) {
            try {
                List<CreativeVariantDef> defs = provider.getVariants(world);
                if (defs != null) {
                    allDefs.addAll(defs);
                }
            } catch (Throwable t) {
                LOGGER.error("[morphext] Error getting variants from provider " + provider.getClass().getName(), t);
            }
        }

        if (allDefs.isEmpty()) {
            return;
        }

        for (MorphSection section : sections) {
            if (section.categories == null) continue;

            // 查找或创建变种专属集中展示分类 "variants"
            MorphCategory variantCategory = null;
            for (MorphCategory cat : section.categories) {
                if ("variants".equals(cat.title) || "morphext_variants".equals(cat.title)) {
                    variantCategory = cat;
                    break;
                }
            }
            if (variantCategory == null) {
                variantCategory = new MorphCategory(section, "variants");
                section.categories.add(variantCategory);
            }

            Map<String, MorphCategory> catMap = new HashMap<>();
            for (MorphCategory cat : section.categories) {
                catMap.put(cat.title, cat);
            }

            int injectedCount = 0;

            for (CreativeVariantDef def : allDefs) {
                String entityId = def.getEntityId();
                ResourceLocation rl = new ResourceLocation(entityId);
                if (!EntityList.isRegistered(rl)) {
                    continue;
                }

                EntityMorph morph = new EntityMorph();
                morph.name = entityId;
                morph.setEntityData(def.getEntityData().copy());
                morph.displayName = def.getDisplayName();

                // 注入专属集中分类
                if (!hasMorphWithDisplayName(variantCategory, morph.displayName)) {
                    variantCategory.add(morph);
                    injectedCount++;
                }

                // 同步注入宿主分类（如 animal/hostile/abyssalcraft）
                MorphCategory targetCat = catMap.get(def.getTargetCategory());
                if (targetCat != null && !hasMorphWithDisplayName(targetCat, morph.displayName)) {
                    targetCat.add(morph);
                }
            }

            if (injectedCount > 0) {
                LOGGER.info("[morphext] Successfully injected {} variant morphs into creative section '{}'",
                        injectedCount, section.title);
            }
        }
    }

    private static boolean hasMorphWithDisplayName(MorphCategory cat, String displayName) {
        if (cat == null || displayName == null) return false;
        for (AbstractMorph m : cat.getMorphs()) {
            if (displayName.equals(m.displayName) || displayName.equals(m.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 内置的原版幼年变体提供者
     */
    private static class VanillaBabyVariantProvider implements ICreativeVariantProvider {
        private static final Map<String, String[]> VANILLA_BABIES = new LinkedHashMap<>();

        static {
            // 幼年动物
            VANILLA_BABIES.put("minecraft:cow", new String[]{"牛", "Cow", "animal"});
            VANILLA_BABIES.put("minecraft:sheep", new String[]{"羊", "Sheep", "animal"});
            VANILLA_BABIES.put("minecraft:pig", new String[]{"猪", "Pig", "animal"});
            VANILLA_BABIES.put("minecraft:chicken", new String[]{"鸡", "Chicken", "animal"});
            VANILLA_BABIES.put("minecraft:horse", new String[]{"马", "Horse", "animal"});
            VANILLA_BABIES.put("minecraft:donkey", new String[]{"驴", "Donkey", "animal"});
            VANILLA_BABIES.put("minecraft:mule", new String[]{"骡", "Mule", "animal"});
            VANILLA_BABIES.put("minecraft:llama", new String[]{"羊驼", "Llama", "animal"});
            VANILLA_BABIES.put("minecraft:ocelot", new String[]{"豹猫", "Ocelot", "animal"});
            VANILLA_BABIES.put("minecraft:wolf", new String[]{"狼", "Wolf", "animal"});
            VANILLA_BABIES.put("minecraft:rabbit", new String[]{"兔子", "Rabbit", "animal"});
            VANILLA_BABIES.put("minecraft:mooshroom", new String[]{"哞菇", "Mooshroom", "animal"});
            VANILLA_BABIES.put("minecraft:villager", new String[]{"村民", "Villager", "animal"});
            VANILLA_BABIES.put("minecraft:polar_bear", new String[]{"北极熊", "Polar Bear", "animal"});

            // 幼年敌对怪物
            VANILLA_BABIES.put("minecraft:zombie", new String[]{"僵尸", "Zombie", "hostile"});
            VANILLA_BABIES.put("minecraft:husk", new String[]{"尸壳", "Husk", "hostile"});
            VANILLA_BABIES.put("minecraft:zombie_pigman", new String[]{"僵尸猪人", "Zombie Pigman", "hostile"});
            VANILLA_BABIES.put("minecraft:zombie_villager", new String[]{"僵尸村民", "Zombie Villager", "hostile"});
        }

        @Override
        public List<CreativeVariantDef> getVariants(World world) {
            List<CreativeVariantDef> list = new ArrayList<>();
            for (Map.Entry<String, String[]> e : VANILLA_BABIES.entrySet()) {
                list.add(CreativeVariantDef.createBaby(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
            }
            return list;
        }
    }
}

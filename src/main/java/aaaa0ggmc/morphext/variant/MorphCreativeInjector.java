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
 * 1. 采用插件式 SPI 架构：通过 registerProvider() 接收各补丁模组（如 morphext-patch-abyssalcraft, quark, modpack）注入的专属变体；
 * 2. 具备智能实体 ID 回退与阶数重映射机制：当某变体形态（如巨型修格斯、中型修格斯）未被底层注册为独立 Entity 时，
 *    自动将其解析映射至宿主基础实体（如 lessershoggoth）并施加精确的 scale 缩放与 NBT 标记；
 * 3. 目标分类自适应拓扑生成：若目标分类（如 abyssalcraft, quark, animal, hostile）在选单 Section 中尚未存在，自动予以初始化；
 * 4. 增强搜索关键词匹配：变体形态均配备详尽的中英文双语 DisplayName。
 */
@SideOnly(Side.CLIENT)
public final class MorphCreativeInjector {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static boolean registered = false;

    // 变种提供者注册列表
    private static final List<ICreativeVariantProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    // 实体回退元数据映射
    public static class FallbackEntityInfo {
        public final String baseId;
        public final float defaultScale;

        public FallbackEntityInfo(String baseId, float defaultScale) {
            this.baseId = baseId;
            this.defaultScale = defaultScale;
        }
    }

    private static final Map<String, FallbackEntityInfo> KNOWN_FALLBACKS = new HashMap<>();
    static {
        // 修格斯阶数重映射 (AbyssalCraft 1.10.3 统一依托 EntityLesserShoggoth 渲染模型)
        KNOWN_FALLBACKS.put("abyssalcraft:greatershoggoth", new FallbackEntityInfo("abyssalcraft:lessershoggoth", 1.85F));
        KNOWN_FALLBACKS.put("abyssalcraft:shoggoth", new FallbackEntityInfo("abyssalcraft:lessershoggoth", 1.25F));
        KNOWN_FALLBACKS.put("abyssalcraft.greatershoggoth", new FallbackEntityInfo("abyssalcraft:lessershoggoth", 1.85F));
        KNOWN_FALLBACKS.put("abyssalcraft.shoggoth", new FallbackEntityInfo("abyssalcraft:lessershoggoth", 1.25F));
        KNOWN_FALLBACKS.put("abyssalcraft:demonschicken", new FallbackEntityInfo("abyssalcraft:demonchicken", 1.0F));
    }

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

        // 注册原版全量变体提供者
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
                String baseEntityId = def.getBaseEntityId();
                String actualEntityId = entityId;
                float appliedScale = def.getScale();

                ResourceLocation rl = new ResourceLocation(entityId);
                if (!EntityList.isRegistered(rl)) {
                    // 1. 优先回退到显式声明的基础实体
                    if (baseEntityId != null && EntityList.isRegistered(new ResourceLocation(baseEntityId))) {
                        actualEntityId = baseEntityId;
                    }
                    // 2. 检查已知虚拟变种回退表 (如 greatershoggoth -> lessershoggoth)
                    else if (KNOWN_FALLBACKS.containsKey(entityId)) {
                        FallbackEntityInfo fb = KNOWN_FALLBACKS.get(entityId);
                        if (EntityList.isRegistered(new ResourceLocation(fb.baseId))) {
                            actualEntityId = fb.baseId;
                            if (appliedScale <= 0.001F || Math.abs(appliedScale - 1.0F) < 0.001F) {
                                appliedScale = fb.defaultScale;
                            }
                        }
                    }
                    // 3. 检查 Metamorph remap
                    else {
                        String remapped = MorphManager.INSTANCE.remap(entityId);
                        if (!remapped.equals(entityId) && EntityList.isRegistered(new ResourceLocation(remapped))) {
                            actualEntityId = remapped;
                        }
                    }
                }

                // 若最终仍无法解析到合法实体，则防御性跳过
                if (!EntityList.isRegistered(new ResourceLocation(actualEntityId))) {
                    LOGGER.debug("[morphext] Skipping variant '{}' (id: {}): entity not registered",
                            def.getDisplayName(), entityId);
                    continue;
                }

                EntityMorph morph = new EntityMorph();
                morph.name = actualEntityId;
                NBTTagCompound data = def.getEntityData().copy();
                if (appliedScale > 0.001F) {
                    morph.scale = appliedScale;
                    data.setFloat("morphext_scale", appliedScale);
                }
                morph.setEntityData(data);
                morph.displayName = def.getDisplayName();

                // 注入专属集中分类 "variants"
                if (!hasMorphWithDisplayName(variantCategory, morph.displayName)) {
                    variantCategory.add(morph);
                    injectedCount++;
                }

                // 同步注入宿主分类（如 animal / hostile / abyssalcraft / quark 等）
                String targetCatName = def.getTargetCategory();
                MorphCategory targetCat = catMap.get(targetCatName);
                if (targetCat == null) {
                    targetCat = new MorphCategory(section, targetCatName);
                    section.categories.add(targetCat);
                    catMap.put(targetCatName, targetCat);
                }
                if (!hasMorphWithDisplayName(targetCat, morph.displayName)) {
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
     * 内置原版全量变体提供者（覆盖全部原版幼年、16色绵羊、16色潜影贝、6职业村民/僵尸村民、高压苦力怕、杀手兔、微/中/大/巨型史莱姆等）。
     */
    private static class VanillaBabyVariantProvider implements ICreativeVariantProvider {
        private static final Map<String, String[]> VANILLA_BABIES = new LinkedHashMap<>();

        // 16 种标准原版染料色彩映射: {zh, en}
        private static final String[][] DYE_COLORS = new String[][]{
                {"白色", "White"},
                {"橙色", "Orange"},
                {"品红", "Magenta"},
                {"浅蓝", "Light Blue"},
                {"黄色", "Yellow"},
                {"黄绿", "Lime"},
                {"粉红", "Pink"},
                {"灰色", "Gray"},
                {"浅灰", "Light Gray"},
                {"青色", "Cyan"},
                {"紫色", "Purple"},
                {"蓝色", "Blue"},
                {"棕色", "Brown"},
                {"绿色", "Green"},
                {"红色", "Red"},
                {"黑色", "Black"}
        };

        // 6 种村民职业映射: {zh, en}
        private static final String[][] VILLAGER_PROFESSIONS = new String[][]{
                {"农夫", "Farmer"},
                {"图书管理员", "Librarian"},
                {"牧师", "Priest"},
                {"铁匠", "Blacksmith"},
                {"屠夫", "Butcher"},
                {"傻子", "Nitwit"}
        };

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

            // 幼年怪物
            VANILLA_BABIES.put("minecraft:zombie", new String[]{"僵尸", "Zombie", "hostile"});
            VANILLA_BABIES.put("minecraft:husk", new String[]{"尸壳", "Husk", "hostile"});
            VANILLA_BABIES.put("minecraft:zombie_pigman", new String[]{"僵尸猪人", "Zombie Pigman", "hostile"});
            VANILLA_BABIES.put("minecraft:zombie_villager", new String[]{"僵尸村民", "Zombie Villager", "hostile"});
        }

        @Override
        public List<CreativeVariantDef> getVariants(World world) {
            List<CreativeVariantDef> list = new ArrayList<>();

            // 1. 原版幼年全家桶
            for (Map.Entry<String, String[]> e : VANILLA_BABIES.entrySet()) {
                list.add(CreativeVariantDef.createBaby(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
            }

            // 2. 原版 16 色羊毛绵羊 (除默认白色外其余 15 种彩色羊)
            for (int i = 1; i < DYE_COLORS.length; i++) {
                NBTTagCompound sheepData = new NBTTagCompound();
                sheepData.setByte("Color", (byte) i);
                sheepData.setString("morphext_variant", "color_" + i);
                String display = "羊 (" + DYE_COLORS[i][0] + ") - " + DYE_COLORS[i][1] + " Sheep";
                list.add(new CreativeVariantDef("minecraft:sheep", display, "animal", sheepData));
            }

            // 3. 原版 16 种染色潜影贝
            for (int i = 0; i < DYE_COLORS.length; i++) {
                NBTTagCompound shulkerData = new NBTTagCompound();
                shulkerData.setByte("Color", (byte) i);
                shulkerData.setString("morphext_variant", "shulker_color_" + i);
                String display = "潜影贝 (" + DYE_COLORS[i][0] + ") - " + DYE_COLORS[i][1] + " Shulker";
                list.add(new CreativeVariantDef("minecraft:shulker", display, "hostile", shulkerData));
            }

            // 4. 原版不同职业村民与僵尸村民
            for (int i = 0; i < VILLAGER_PROFESSIONS.length; i++) {
                // 普通村民
                NBTTagCompound villagerData = new NBTTagCompound();
                villagerData.setInteger("Profession", i);
                villagerData.setString("morphext_variant", "profession_" + i);
                String display = "村民 (" + VILLAGER_PROFESSIONS[i][0] + ") - " + VILLAGER_PROFESSIONS[i][1] + " Villager";
                list.add(new CreativeVariantDef("minecraft:villager", display, "animal", villagerData));

                // 僵尸村民职业
                NBTTagCompound zVillagerData = new NBTTagCompound();
                zVillagerData.setInteger("Profession", i);
                zVillagerData.setString("morphext_variant", "zombie_profession_" + i);
                String zDisplay = "僵尸村民 (" + VILLAGER_PROFESSIONS[i][0] + ") - Zombie " + VILLAGER_PROFESSIONS[i][1];
                list.add(new CreativeVariantDef("minecraft:zombie_villager", zDisplay, "hostile", zVillagerData));
            }

            // 5. 高压苦力怕 (Charged Creeper)
            NBTTagCompound chargedCreeper = new NBTTagCompound();
            chargedCreeper.setBoolean("powered", true);
            chargedCreeper.setString("morphext_variant", "charged_creeper");
            list.add(new CreativeVariantDef("minecraft:creeper", "苦力怕 (高压充能) - Charged Creeper", "hostile", chargedCreeper));

            // 6. 兔子全色系与杀手兔
            NBTTagCompound killerBunny = new NBTTagCompound();
            killerBunny.setInteger("RabbitType", 99);
            killerBunny.setString("morphext_variant", "killer_bunny");
            list.add(new CreativeVariantDef("minecraft:rabbit", "兔子 (杀手兔) - Killer Bunny", "hostile", killerBunny));

            String[][] rabbitTypes = new String[][]{
                    {"棕色", "Brown"},
                    {"纯白", "White"},
                    {"纯黑", "Black"},
                    {"黑白斑点", "Black & White"},
                    {"金黄", "Gold"},
                    {"椒盐", "Salt & Pepper"}
            };
            for (int i = 1; i < rabbitTypes.length; i++) {
                NBTTagCompound rabbitData = new NBTTagCompound();
                rabbitData.setInteger("RabbitType", i);
                rabbitData.setString("morphext_variant", "rabbit_type_" + i);
                list.add(new CreativeVariantDef("minecraft:rabbit", "兔子 (" + rabbitTypes[i][0] + ") - " + rabbitTypes[i][1] + " Rabbit", "animal", rabbitData));
            }

            // 7. 史莱姆与岩浆怪体型变体 (微型 1、大型 4、巨型 8)
            int[] slimeSizes = new int[]{1, 4, 8};
            float[] slimeScales = new float[]{0.5F, 2.0F, 3.5F};
            String[] sizeNamesZh = new String[]{"微型", "大型", "泰坦超巨型"};
            String[] sizeNamesEn = new String[]{"Tiny", "Big", "Colossal"};
            for (int s = 0; s < slimeSizes.length; s++) {
                // 史莱姆
                NBTTagCompound sData = new NBTTagCompound();
                sData.setInteger("Size", slimeSizes[s]);
                sData.setString("morphext_variant", "slime_size_" + slimeSizes[s]);
                list.add(new CreativeVariantDef("minecraft:slime", "史莱姆 (" + sizeNamesZh[s] + ") - " + sizeNamesEn[s] + " Slime", "hostile", sData, slimeScales[s]));

                // 岩浆怪
                NBTTagCompound mData = new NBTTagCompound();
                mData.setInteger("Size", slimeSizes[s]);
                mData.setString("morphext_variant", "magmacube_size_" + slimeSizes[s]);
                list.add(new CreativeVariantDef("minecraft:magma_cube", "岩浆怪 (" + sizeNamesZh[s] + ") - " + sizeNamesEn[s] + " Magma Cube", "hostile", mData, slimeScales[s]));
            }

            // 8. 猫种群变体 (野生豹猫、燕尾服黑猫、红虎斑猫、暹罗猫)
            String[][] catTypes = new String[][]{
                    {"燕尾服黑猫", "Tuxedo Cat"},
                    {"红虎斑猫", "Red Tabby Cat"},
                    {"暹罗猫", "Siamese Cat"}
            };
            for (int c = 0; c < catTypes.length; c++) {
                NBTTagCompound cData = new NBTTagCompound();
                cData.setInteger("CatType", c + 1);
                cData.setString("morphext_variant", "cat_type_" + (c + 1));
                list.add(new CreativeVariantDef("minecraft:ocelot", "猫 (" + catTypes[c][0] + ") - " + catTypes[c][1], "animal", cData));
            }

            // 9. 鹦鹉色彩变种 (红、蓝、绿、青、灰)
            String[][] parrotColors = new String[][]{
                    {"红金刚", "Red Parrot"},
                    {"蓝金刚", "Blue Parrot"},
                    {"绿鹦鹉", "Green Parrot"},
                    {"青色鹦鹉", "Cyan Parrot"},
                    {"灰色鹦鹉", "Gray Parrot"}
            };
            for (int p = 1; p < parrotColors.length; p++) {
                NBTTagCompound pData = new NBTTagCompound();
                pData.setInteger("Variant", p);
                pData.setString("morphext_variant", "parrot_color_" + p);
                list.add(new CreativeVariantDef("minecraft:parrot", "鹦鹉 (" + parrotColors[p][0] + ") - " + parrotColors[p][1], "animal", pData));
            }

            // 10. 羊驼变种 (奶油色、白色、棕色、灰色)
            String[][] llamaColors = new String[][]{
                    {"纯白色", "White Llama"},
                    {"棕褐色", "Brown Llama"},
                    {"高原灰", "Gray Llama"}
            };
            for (int l = 0; l < llamaColors.length; l++) {
                NBTTagCompound lData = new NBTTagCompound();
                lData.setInteger("Variant", l + 1);
                lData.setString("morphext_variant", "llama_variant_" + (l + 1));
                list.add(new CreativeVariantDef("minecraft:llama", "羊驼 (" + llamaColors[l][0] + ") - " + llamaColors[l][1], "animal", lData));
            }

            // 11. 骷髅陷阱马
            NBTTagCompound skelTrap = new NBTTagCompound();
            skelTrap.setBoolean("SkeletonTrap", true);
            skelTrap.setString("morphext_variant", "skeleton_trap_horse");
            list.add(new CreativeVariantDef("minecraft:skeleton_horse", "骷髅马 (陷阱四骑士) - Skeleton Trap Horse", "hostile", skelTrap));

            return list;
        }
    }
}

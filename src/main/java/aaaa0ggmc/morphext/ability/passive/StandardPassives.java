package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import net.minecraft.entity.SharedMonsterAttributes;

import java.util.UUID;

/**
 * 原版与通用标准被动能力注册中心。
 */
public final class StandardPassives {
    private static boolean registered = false;

    private static final UUID GOLEM_KNOCKBACK_UUID = UUID.fromString("1f2e3d4c-5b6a-7f8e-9d0c-1b2a3c4d5e6f");
    private static final UUID FELINE_SPEED_UUID = UUID.fromString("2a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d");
    private static final UUID THICK_HIDE_UUID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e01");
    private static final UUID ARCHER_TRAINING_UUID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e02");
    private static final UUID AXE_MASTERY_UUID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e03");
    private static final UUID IRON_PHYSIQUE_UUID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e04");
    private static final UUID PRISMARINE_SHELL_UUID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e05");
    private static final UUID TITANIC_STATURE_UUID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e06");

    private StandardPassives() {}

    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;

        // 0. 注册细分阵营体系 (Faction Passive System)
        aaaa0ggmc.morphext.faction.FactionRegistry.registerAll();

        // 1. 移动与环境生存原生被动
        register(new FlightPassive());
        register(new FlightPassive("fly"));
        register(new WaterBreathingPassive());
        register(new WaterBreathingPassive("water_breath"));
        register(new PreventFallPassive());
        register(new PreventFallPassive("prevent_fall"));
        register(new ClimbPassive());
        register(new ClimbPassive("morphext:climb"));
        register(new SwimPassive());
        register(new SwimPassive("morphext:swim"));
        register(new NightVisionPassive());
        register(new NightVisionPassive("morphext:night_vision"));

        // 2. 生物生理抗性与环境过敏被动
        register(new SunAllergyPassive());
        register(new SunAllergyPassive("morphext:sun_allergy"));
        register(new WaterAllergyPassive());
        register(new WaterAllergyPassive("morphext:water_allergy"));
        register(new UndeadPassive("hungerless"));
        register(new UndeadPassive("morphext:hungerless"));
        register(new UndeadPassive("rotten"));
        register(new UndeadPassive("morphext:rotten"));
        register(new CookieAllergyPassive());
        register(new CookieAllergyPassive("cookie_allergy"));
        register(new CookieAllergyPassive("morphext:cookie_allergy"));

        // 3. 实体特性光环、反伤与生物专属被动
        register(new CreeperRepelPassive());
        register(new CreeperPanicPassive());
        register(new CreeperPanicPassive("creeper_panic"));
        register(new CreeperPanicPassive("morphext:creeper_panic"));
        register(new PolarBearCubProtectionPassive());
        register(new PolarBearCubProtectionPassive("polar_bear_cub_protection"));
        register(new PolarBearCubProtectionPassive("morphext:polar_bear_cub_protection"));
        register(new LlamaCaravanPassive());
        register(new LlamaCaravanPassive("llama_caravan"));
        register(new LlamaCaravanPassive("morphext:llama_caravan"));
        register(new ThornsPassive("thorns"));
        register(new ThornsPassive("morphext:thorns"));
        register(new WebImmunityPassive("web_immunity"));
        register(new WebImmunityPassive("morphext:web_immunity"));
        register(new CaveSpiderPoisonPassive("cave_spider_poison"));
        register(new CaveSpiderPoisonPassive("morphext:cave_spider_poison"));
        register(new ShulkerPassive("shulker_defense"));
        register(new ShulkerPassive("morphext:shulker_defense"));
        register(new SquidPassive("squid_swim"));
        register(new SquidPassive("morphext:squid_swim"));
        register(new IronGolemPassive("iron_golem_nature"));
        register(new IronGolemPassive("morphext:iron_golem_nature"));
        register(new WitchMagicResistancePassive());
        register(new WitchMagicResistancePassive("witch_magic_resistance"));
        register(new WitchMagicResistancePassive("morphext:witch_magic_resistance"));

        // 末影与下界终极 Boss 及末地专精专属被动
        register(new WitherArmorPassive());
        register(new WitherArmorPassive("wither_armor"));
        register(new WitherArmorPassive("morphext:wither_armor"));
        register(new EndermanArrowDodgePassive());
        register(new EndermanArrowDodgePassive("enderman_arrow_dodge"));
        register(new EndermanArrowDodgePassive("morphext:enderman_arrow_dodge"));
        register(new EndermanWaterStressPassive());
        register(new EndermanWaterStressPassive("enderman_water_stress"));
        register(new EndermanWaterStressPassive("morphext:enderman_water_stress"));
        register(new EndermiteBaitPassive());
        register(new EndermiteBaitPassive("endermite_bait"));
        register(new EndermiteBaitPassive("morphext:endermite_bait"));

        // 极寒射手与不死坐骑专属被动
        register(new StrayFrostAuraPassive());
        register(new StrayFrostAuraPassive("stray_frost_aura"));
        register(new StrayFrostAuraPassive("morphext:stray_frost_aura"));
        register(new SnowmanSnowTrailPassive());
        register(new SnowmanSnowTrailPassive("snowman_snow_trail"));
        register(new SnowmanSnowTrailPassive("morphext:snowman_snow_trail"));
        register(new SnowmanSnowTrailPassive("snow_walk"));
        register(new SnowmanSnowTrailPassive("morphext:snow_walk"));
        register(new UndeadHorseWaterPassive());
        register(new UndeadHorseWaterPassive("undead_horse_water"));
        register(new UndeadHorseWaterPassive("morphext:undead_horse_water"));

        // 4. 通用属性修饰被动
        register(new AttributeModifierPassive("iron_knockback_res",
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE, GOLEM_KNOCKBACK_UUID, 1.0D, 0));
        register(new AttributeModifierPassive("morphext:iron_knockback_res",
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE, GOLEM_KNOCKBACK_UUID, 1.0D, 0));
        register(new AttributeModifierPassive("morphext:feline_speed",
                SharedMonsterAttributes.MOVEMENT_SPEED, FELINE_SPEED_UUID, 0.15D, 2));

        // 5. 标准声明式伤害免疫库
        register(new DamageImmunityPassive("morphext:immunity_fall", false, "fall"));
        register(new DamageImmunityPassive("morphext:immunity_fire", false, "inFire", "onFire", "lava", "hotFloor"));
        register(new DamageImmunityPassive("morphext:immunity_drown", false, "drown"));
        register(new DamageImmunityPassive("morphext:immunity_cactus", false, "cactus"));
        register(new DamageImmunityPassive("morphext:immunity_inwall", false, "inWall"));
        register(new DamageImmunityPassive("inWall", false, "inWall"));
        register(new DamageImmunityPassive("fire_proof", false, "inFire", "onFire", "lava", "hotFloor"));
        register(new DamageImmunityPassive("morphext:fire_proof", false, "inFire", "onFire", "lava", "hotFloor"));

        // 6. 标准声明式药水免疫库
        register(new PotionImmunityPassive("morphext:immunity_poison", "poison", "minecraft:poison"));
        register(new PotionImmunityPassive("morphext:immunity_wither", "wither", "minecraft:wither"));
        register(new PotionImmunityPassive("morphext:immunity_blindness", "blindness", "minecraft:blindness"));
        register(new PotionImmunityPassive("morphext:immunity_slowness", "slowness", "minecraft:slowness"));
        register(new PotionImmunityPassive("morphext:immunity_mining_fatigue", "mining_fatigue", "minecraft:mining_fatigue"));

        // 7. metamorph 原版能力桥接（补 jumping/step_up/glide 的 UAPA 解析缺口，
        //    只委托不覆盖 metamorph 行为，morphs.json 同名授予时不重复执行）
        register(new aaaa0ggmc.morphext.integration.metamorph.MetamorphAbilityAdapter("jumping",
                new mchorse.vanilla_pack.abilities.Jumping(),
                "morphext.passive.jumping.name", "morphext.passive.jumping.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.SLIME_BALL)));
        register(new aaaa0ggmc.morphext.integration.metamorph.MetamorphAbilityAdapter("morphext:jumping",
                new mchorse.vanilla_pack.abilities.Jumping(),
                "morphext.passive.jumping.name", "morphext.passive.jumping.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.SLIME_BALL)));
        register(new aaaa0ggmc.morphext.integration.metamorph.MetamorphAbilityAdapter("step_up",
                new mchorse.vanilla_pack.abilities.StepUp(),
                "morphext.passive.step_up.name", "morphext.passive.step_up.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.LEATHER_BOOTS)));
        register(new aaaa0ggmc.morphext.integration.metamorph.MetamorphAbilityAdapter("morphext:step_up",
                new mchorse.vanilla_pack.abilities.StepUp(),
                "morphext.passive.step_up.name", "morphext.passive.step_up.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.LEATHER_BOOTS)));
        register(new aaaa0ggmc.morphext.integration.metamorph.MetamorphAbilityAdapter("glide",
                new mchorse.vanilla_pack.abilities.Glide(),
                "morphext.passive.glide.name", "morphext.passive.glide.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.FEATHER)));
        register(new aaaa0ggmc.morphext.integration.metamorph.MetamorphAbilityAdapter("morphext:glide",
                new mchorse.vanilla_pack.abilities.Glide(),
                "morphext.passive.glide.name", "morphext.passive.glide.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.FEATHER)));

        // 8. 生物专属新被动（W1：马属强跃 / 水生陆地应激 / 蜘蛛日光中立）
        register(new HorseJumpPassive());
        register(new HorseJumpPassive("horse_jump"));
        register(new LandStressPassive());
        register(new LandStressPassive("land_stress"));
        register(new SpiderSunPassive());
        register(new SpiderSunPassive("morphext:spider_sun_passive"));

        // 9. 生物专属新被动（E1：声呐视界 / 毒液反噬 / 厚皮 / 黏胶之躯 / 冰霜之触 /
        //    墨汁护体 / 熔岩灼体 / 狼群狂怒 / 怯懦疾走 / 暴怒加速 / 光环系三件套 /
        //    凋灵之触 / 奥术屏障 / 钢铁之躯 / 棱甲 / 泰坦之躯 / 射手训练 / 斧刃专精 / 岩石拟态）
        register(new PotionImmunityPassive("morphext:sonar_sense", "morphext.passive.morphext.sonar_sense.name",
                "morphext.passive.morphext.sonar_sense.desc", new net.minecraft.item.ItemStack(net.minecraft.init.Items.FEATHER),
                "blindness", "minecraft:blindness"));
        register(new PotionImmunityPassive("sonar_sense", "morphext.passive.sonar_sense.name",
                "morphext.passive.sonar_sense.desc", new net.minecraft.item.ItemStack(net.minecraft.init.Items.FEATHER),
                "blindness", "minecraft:blindness"));
        register(new MeleeRetaliatePassive("morphext:venom_hide", MeleeRetaliatePassive.Effect.POISON, 0, 80));
        register(new MeleeRetaliatePassive("venom_hide", MeleeRetaliatePassive.Effect.POISON, 0, 80));
        register(new MeleeRetaliatePassive("morphext:sticky_gel", MeleeRetaliatePassive.Effect.SLOWNESS, 1, 60));
        register(new MeleeRetaliatePassive("sticky_gel", MeleeRetaliatePassive.Effect.SLOWNESS, 1, 60));
        register(new MeleeRetaliatePassive("morphext:chill_touch", MeleeRetaliatePassive.Effect.SLOWNESS, 0, 100));
        register(new MeleeRetaliatePassive("chill_touch", MeleeRetaliatePassive.Effect.SLOWNESS, 0, 100));
        register(new MeleeRetaliatePassive("morphext:ink_veil", MeleeRetaliatePassive.Effect.BLINDNESS, 0, 60));
        register(new MeleeRetaliatePassive("ink_veil", MeleeRetaliatePassive.Effect.BLINDNESS, 0, 60));
        register(new MeleeRetaliatePassive("morphext:magma_burn", MeleeRetaliatePassive.Effect.BURN, 0, 60));
        register(new MeleeRetaliatePassive("magma_burn", MeleeRetaliatePassive.Effect.BURN, 0, 60));
        register(new LowHealthEffectPassive("morphext:pack_fury", net.minecraft.init.MobEffects.STRENGTH, 0, 0.5F));
        register(new LowHealthEffectPassive("pack_fury", net.minecraft.init.MobEffects.STRENGTH, 0, 0.5F));
        register(new LowHealthEffectPassive("morphext:coward_flight", net.minecraft.init.MobEffects.SPEED, 0, 0.5F));
        register(new LowHealthEffectPassive("coward_flight", net.minecraft.init.MobEffects.SPEED, 0, 0.5F));
        register(new OnHurtEffectPassive("morphext:rage_speed", net.minecraft.init.MobEffects.SPEED, 0, 100));
        register(new OnHurtEffectPassive("rage_speed", net.minecraft.init.MobEffects.SPEED, 0, 100));
        register(new OnAttackEffectPassive("morphext:withering_touch", net.minecraft.init.MobEffects.WITHER, 0, 80));
        register(new OnAttackEffectPassive("withering_touch", net.minecraft.init.MobEffects.WITHER, 0, 80));
        register(new PotionAuraPassive("morphext:ancient_curse_aura", net.minecraft.init.MobEffects.MINING_FATIGUE, 0, 8.0D, 40, 80));
        register(new PotionAuraPassive("ancient_curse_aura", net.minecraft.init.MobEffects.MINING_FATIGUE, 0, 8.0D, 40, 80));
        register(new PotionAuraPassive("morphext:withering_presence", net.minecraft.init.MobEffects.WITHER, 0, 6.0D, 60, 60));
        register(new PotionAuraPassive("withering_presence", net.minecraft.init.MobEffects.WITHER, 0, 6.0D, 60, 60));
        register(new PotionAuraPassive("morphext:hungering_aura", net.minecraft.init.MobEffects.HUNGER, 0, 6.0D, 60, 80));
        register(new PotionAuraPassive("hungering_aura", net.minecraft.init.MobEffects.HUNGER, 0, 6.0D, 60, 80));
        register(new MagicWardPassive("morphext:arcane_ward"));
        register(new MagicWardPassive("arcane_ward"));
        register(new StoneCamouflagePassive("morphext:stone_camouflage"));
        register(new StoneCamouflagePassive("stone_camouflage"));
        register(new AttributeModifierPassive("morphext:thick_hide",
                "morphext.passive.morphext.thick_hide.name", "morphext.passive.morphext.thick_hide.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.LEATHER),
                SharedMonsterAttributes.ARMOR, THICK_HIDE_UUID, "morphext.thick_hide", 2.0D, 0));
        register(new AttributeModifierPassive("thick_hide",
                "morphext.passive.thick_hide.name", "morphext.passive.thick_hide.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.LEATHER),
                SharedMonsterAttributes.ARMOR, THICK_HIDE_UUID, "morphext.thick_hide", 2.0D, 0));
        register(new AttributeModifierPassive("morphext:archer_training",
                "morphext.passive.morphext.archer_training.name", "morphext.passive.morphext.archer_training.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.BOW),
                SharedMonsterAttributes.ATTACK_SPEED, ARCHER_TRAINING_UUID, "morphext.archer_training", 0.15D, 2));
        register(new AttributeModifierPassive("archer_training",
                "morphext.passive.archer_training.name", "morphext.passive.archer_training.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.BOW),
                SharedMonsterAttributes.ATTACK_SPEED, ARCHER_TRAINING_UUID, "morphext.archer_training", 0.15D, 2));
        register(new AttributeModifierPassive("morphext:axe_mastery",
                "morphext.passive.morphext.axe_mastery.name", "morphext.passive.morphext.axe_mastery.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_AXE),
                SharedMonsterAttributes.ATTACK_DAMAGE, AXE_MASTERY_UUID, "morphext.axe_mastery", 2.0D, 0));
        register(new AttributeModifierPassive("axe_mastery",
                "morphext.passive.axe_mastery.name", "morphext.passive.axe_mastery.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_AXE),
                SharedMonsterAttributes.ATTACK_DAMAGE, AXE_MASTERY_UUID, "morphext.axe_mastery", 2.0D, 0));
        register(new AttributeModifierPassive("morphext:iron_physique",
                "morphext.passive.morphext.iron_physique.name", "morphext.passive.morphext.iron_physique.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT),
                SharedMonsterAttributes.MAX_HEALTH, IRON_PHYSIQUE_UUID, "morphext.iron_physique", 10.0D, 0));
        register(new AttributeModifierPassive("iron_physique",
                "morphext.passive.iron_physique.name", "morphext.passive.iron_physique.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT),
                SharedMonsterAttributes.MAX_HEALTH, IRON_PHYSIQUE_UUID, "morphext.iron_physique", 10.0D, 0));
        register(new AttributeModifierPassive("morphext:prismarine_shell",
                "morphext.passive.morphext.prismarine_shell.name", "morphext.passive.morphext.prismarine_shell.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.PRISMARINE_SHARD),
                SharedMonsterAttributes.ARMOR, PRISMARINE_SHELL_UUID, "morphext.prismarine_shell", 2.0D, 0));
        register(new AttributeModifierPassive("prismarine_shell",
                "morphext.passive.prismarine_shell.name", "morphext.passive.prismarine_shell.desc",
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.PRISMARINE_SHARD),
                SharedMonsterAttributes.ARMOR, PRISMARINE_SHELL_UUID, "morphext.prismarine_shell", 2.0D, 0));
        register(new AttributeModifierPassive("morphext:titanic_stature",
                "morphext.passive.morphext.titanic_stature.name", "morphext.passive.morphext.titanic_stature.desc",
                new net.minecraft.item.ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.OBSIDIAN)),
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE, TITANIC_STATURE_UUID, "morphext.titanic_stature", 0.5D, 0));
        register(new AttributeModifierPassive("titanic_stature",
                "morphext.passive.titanic_stature.name", "morphext.passive.titanic_stature.desc",
                new net.minecraft.item.ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.OBSIDIAN)),
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE, TITANIC_STATURE_UUID, "morphext.titanic_stature", 0.5D, 0));
    }

    private static void register(aaaa0ggmc.morphext.ability.IAbility ability) {
        AbilityRegistry.register(ability);
    }
}

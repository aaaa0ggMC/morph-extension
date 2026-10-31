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

    private StandardPassives() {}

    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;

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
    }

    private static void register(aaaa0ggmc.morphext.ability.IAbility ability) {
        AbilityRegistry.register(ability);
    }
}

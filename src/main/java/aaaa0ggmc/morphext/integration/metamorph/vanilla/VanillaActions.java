package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.abilities.IAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原版生物专属动作统一注册入口。
 * 遵循 DI 与模块化规范，将所有原版生物主动动作注册至 MorphManager.INSTANCE.actions。
 */
public final class VanillaActions {
    private static final Logger LOGGER = LogManager.getLogger("morph-extension");
    private static boolean registered = false;

    private VanillaActions() {
    }

    public static synchronized void registerAll() {
        if (registered) {
            return;
        }
        registered = true;

        ServerActionScheduler.init();

        // 基础动作
        register(EcholocationAction.ID, new EcholocationAction());
        register(ShulkerBulletAction.ID, new ShulkerBulletAction());
        register("morphext:shulker_bullet", new ShulkerBulletAction());
        register(ShulkerShellAction.ID, new ShulkerShellAction());
        register("morphext:shulker_shell", new ShulkerShellAction());
        register(ShulkerTeleportAction.ID, new ShulkerTeleportAction());
        register(ShulkerTeleportAction.ALIAS_ID, new ShulkerTeleportAction());
        register(PrismarineLaserAction.ID, new PrismarineLaserAction());
        register(ElderCurseAction.ID, new ElderCurseAction());
        register(GroundSlamAction.ID, new GroundSlamAction());
        register(RapidShotAction.ID, new RapidShotAction());
        register(FrostShotAction.ID, new FrostShotAction());
        register(InkJetAction.ID, new InkJetAction());
        register(WolfBattleHowlAction.ID, new WolfBattleHowlAction());
        register(WolfBattleHowlAction.ALIAS_ID, new WolfBattleHowlAction());
        register(FelinePounceAction.ID, new FelinePounceAction());
        register(SnowstormVolleyAction.ID, new SnowstormVolleyAction());
        register(BlazeFireRingAction.ID, new BlazeFireRingAction());
        register(WebTrapAction.ID, new WebTrapAction());
        register(PigmanRageAction.ID, new PigmanRageAction());

        register(IronFortitudeAction.ID, new IronFortitudeAction());
        register(HeavyTossAction.ID, new HeavyTossAction());
        register(UltrasonicShriekAction.ID, new UltrasonicShriekAction());
        register(SpikeBurstAction.ID, new SpikeBurstAction());
        register(SpectralDazeAction.ID, new SpectralDazeAction());
        register(WolfFuriousBiteAction.ID, new WolfFuriousBiteAction());
        register(WolfFuriousBiteAction.ALIAS_ID, new WolfFuriousBiteAction());
        register(BlazeFireballAction.ID, new BlazeFireballAction());
        register(VenomStrikeAction.ID, new VenomStrikeAction());
        register(StrafeDodgeAction.ID, new StrafeDodgeAction());
        register(FrostAuraAction.ID, new FrostAuraAction());
        register(SpiderLeapAction.ID, new SpiderLeapAction());

        // 原版牲畜与被动动作
        register(CowMilkAction.ID, new CowMilkAction());
        register(MooshroomHarvestAction.ID, new MooshroomHarvestAction());
        register(SheepGrazeAction.ID, new SheepGrazeAction());
        register(SheepGrazeAction.ALIAS_ID, new SheepGrazeAction());
        register(SheepShearAction.ID, new SheepShearAction());
        register(SheepShearAction.ALIAS_ID, new SheepShearAction());
        register(ChickenLayEggAction.ID, new ChickenLayEggAction());
        register(PigRootingAction.ID, new PigRootingAction());
        register(IronRepairAction.ID, new IronRepairAction());

        // 新增敌对与高级生物技能动作
        register(CreeperFuseAction.ID, new CreeperFuseAction());
        register(CreeperHissAction.ID, new CreeperHissAction());
        register(WitherStrikeAction.ID, new WitherStrikeAction());
        register(WitherSkullThrowAction.ID, new WitherSkullThrowAction());
        register(WitchThrowPotionAction.ID, new WitchThrowPotionAction());
        register(WitchDrinkPotionAction.ID, new WitchDrinkPotionAction());
        register(EvokerFangsAction.ID, new EvokerFangsAction());
        register(EvokerVexSummonAction.ID, new EvokerVexSummonAction());
        register(EvokerWololoAction.ID, new EvokerWololoAction());
        register(VexPhaseAction.ID, new VexPhaseAction());
        register(VindicatorAxeChargeAction.ID, new VindicatorAxeChargeAction());
        register(IllusionBlindnessAction.ID, new IllusionBlindnessAction());
        register(IllusionMirrorAction.ID, new IllusionMirrorAction());
        register(IllusionArrowVolleyAction.ID, new IllusionArrowVolleyAction());
        register(EndermanTeleportAction.ID, new EndermanTeleportAction());
        register(EndermanScreechAction.ID, new EndermanScreechAction());
        register(SlimeBounceAction.ID, new SlimeBounceAction());
        register(MagmaSlamAction.ID, new MagmaSlamAction());
        register(LlamaSpitBarrageAction.ID, new LlamaSpitBarrageAction());
        register(LlamaSpitBarrageAction.ALIAS_ID, new LlamaSpitBarrageAction());
        register(RabbitHopAction.ID, new RabbitHopAction());
        register(PolarBearMaulAction.ID, new PolarBearMaulAction());
        register("polar_maul", new PolarBearMaulAction());
        register(GhastFireballAction.ID, new GhastFireballAction());
        register(GhastScreechAction.ID, new GhastScreechAction());
        register(BatRoostAction.ID, new BatRoostAction());
        register(ParrotMimicAction.ID, new ParrotMimicAction());
        register(ParrotDanceAction.ID, new ParrotDanceAction());
        register(ZombieInfectiousStrikeAction.ID, new ZombieInfectiousStrikeAction());
        register(HuskSandStrikeAction.ID, new HuskSandStrikeAction());

        // 末影与下界终极 Boss 及末地专精技能动作
        register(DragonBreathAction.ID, new DragonBreathAction());
        register(DragonWingFlapAction.ID, new DragonWingFlapAction());
        register(DragonDiveBombAction.ID, new DragonDiveBombAction());
        register(WitherSkullAction.ID, new WitherSkullAction());
        register(WitherBlueSkullAction.ID, new WitherBlueSkullAction());
        register(WitherBirthBlastAction.ID, new WitherBirthBlastAction());
        register(EndermiteBurrowAction.ID, new EndermiteBurrowAction());
        register(EndermiteBiteAction.ID, new EndermiteBiteAction());

        // 极寒射手与不死坐骑专精技能动作
        register(StrayFrostArrowAction.ID, new StrayFrostArrowAction());
        register(SkeletonRapidShotAction.ID, new SkeletonRapidShotAction());
        register(SkeletonMeleeJabAction.ID, new SkeletonMeleeJabAction());
        register(SnowmanBlizzardAction.ID, new SnowmanBlizzardAction());
        register(SkeletonTrapAction.ID, new SkeletonTrapAction());

        // 村民社会与构装傀儡生态专精技能动作
        register(VillagerHarvestAction.ID, new VillagerHarvestAction());
        register(VillagerHarvestAction.ALIAS_ID, new VillagerHarvestAction());
        register(VillagerPanicAlarmAction.ID, new VillagerPanicAlarmAction());
        register(VillagerPanicAlarmAction.ALIAS_ID, new VillagerPanicAlarmAction());
        register(GolemFlowerGiftAction.ID, new GolemFlowerGiftAction());
        register(GolemFlowerGiftAction.ALIAS_ID, new GolemFlowerGiftAction());
        register(GolemTauntRoarAction.ID, new GolemTauntRoarAction());
        register(GolemTauntRoarAction.ALIAS_ID, new GolemTauntRoarAction());
        register(SnowmanFrostBarrierAction.ID, new SnowmanFrostBarrierAction());
        register(SnowmanFrostBarrierAction.ALIAS_ID, new SnowmanFrostBarrierAction());

        // 节肢潜伏与坐骑自愈专精技能动作
        register("silverfish_swarm_call", new SilverfishSwarmCallAction());
        register("morphext:silverfish_swarm_call", new SilverfishSwarmCallAction());
        register("donkey_rear_kick", new DonkeyRearKickAction());
        register("morphext:donkey_rear_kick", new DonkeyRearKickAction());
        register("zombie_villager_cure_plea", new ZombieVillagerCurePleaAction());
        register("morphext:zombie_villager_cure_plea", new ZombieVillagerCurePleaAction());

        // 远古巨人与隐藏狂暴生灵动作
        register("giant_titanic_slam", new GiantTitanicSlamAction());
        register("morphext:giant_titanic_slam", new GiantTitanicSlamAction());
        register("giant_colossal_sweep", new GiantColossalSweepAction());
        register("morphext:giant_colossal_sweep", new GiantColossalSweepAction());
        register("pigman_wrath_shout", new PigmanWrathShoutAction());
        register("morphext:pigman_wrath_shout", new PigmanWrathShoutAction());
        register("killer_bunny_blood_frenzy", new KillerBunnyBloodFrenzyAction());
        register("morphext:killer_bunny_blood_frenzy", new KillerBunnyBloodFrenzyAction());

        // W1 新增：村民易物 / 尸潮增援 / 恼鬼冲刺 / 强尼狂暴 / 尸壳沙暴
        register(VillagerBarterAction.ID, new VillagerBarterAction());
        register(VillagerBarterAction.ALIAS_ID, new VillagerBarterAction());
        register(ZombieReinforceAction.ID, new ZombieReinforceAction());
        register(ZombieReinforceAction.ALIAS_ID, new ZombieReinforceAction());
        register(VexChargeAction.ID, new VexChargeAction());
        register(VexChargeAction.ALIAS_ID, new VexChargeAction());
        register(VindicatorJohnnyRampageAction.ID, new VindicatorJohnnyRampageAction());
        register(VindicatorJohnnyRampageAction.ALIAS_ID, new VindicatorJohnnyRampageAction());
        register(HuskSandStormAction.ID, new HuskSandStormAction());
        register(HuskSandStormAction.ALIAS_ID, new HuskSandStormAction());

        // E1 新增：生物身份动作（倔强嘶鸣 / 扬蹄疾驰 / 漂浮力场 / 公羊冲撞 /
        //          吐网缠缚 / 警戒鸣叫 / 缠怨 / 破门而入）
        register(StubbornBrayAction.ID, new StubbornBrayAction());
        register(StubbornBrayAction.ALIAS_ID, new StubbornBrayAction());
        register(EquineGallopAction.ID, new EquineGallopAction());
        register(EquineGallopAction.ALIAS_ID, new EquineGallopAction());
        register(ShulkerLevitationFieldAction.ID, new ShulkerLevitationFieldAction());
        register(ShulkerLevitationFieldAction.ALIAS_ID, new ShulkerLevitationFieldAction());
        register(RamChargeAction.ID, new RamChargeAction());
        register(RamChargeAction.ALIAS_ID, new RamChargeAction());
        register(SpiderWebSlingAction.ID, new SpiderWebSlingAction());
        register(SpiderWebSlingAction.ALIAS_ID, new SpiderWebSlingAction());
        register(ParrotAlarmCallAction.ID, new ParrotAlarmCallAction());
        register(ParrotAlarmCallAction.ALIAS_ID, new ParrotAlarmCallAction());
        register(VexHauntAction.ID, new VexHauntAction());
        register(VexHauntAction.ALIAS_ID, new VexHauntAction());
        register(ZombieDoorBreakAction.ID, new ZombieDoorBreakAction());
        register(ZombieDoorBreakAction.ALIAS_ID, new ZombieDoorBreakAction());

        // E1 新增：metamorph 原版闲置动作冷却代理 —— 同 id 覆盖注册
        // （与 shulker_bullet / mob_act / ai_target 的既有覆盖惯例一致），
        // 执行逻辑 1:1 委托原版实现，仅补上 ICooldownAction 权威冷却（6-25s）。
        register("fireball", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Fireball(), 200));
        register("small_fireball", new CooldownActionProxy(new mchorse.vanilla_pack.actions.SmallFireball(), 120));
        register("teleport", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Teleport(), 160));
        register("potions", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Potions(), 200));
        register("spit", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Spit(), 120));
        register("explode", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Explode(), 300));
        register("fire_breath", new CooldownActionProxy(new mchorse.vanilla_pack.actions.FireBreath(), 300));
        // 增补轮补漏：W1 引用的 4 个 metamorph 原语义动作同样补权威冷却（jump 强跳/拟态类防刷）
        register("jump", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Jump(), 80));
        register("snowball", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Snowball(), 40));
        register("endermite", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Endermite(), 100));
        register("silverfish", new CooldownActionProxy(new mchorse.vanilla_pack.actions.Sliverfish(), 100));

        // 生物自主 AI 切换动作
        register(aaaa0ggmc.morphext.integration.metamorph.MobAiToggleAction.ID, new aaaa0ggmc.morphext.integration.metamorph.MobAiToggleAction());
        register(aaaa0ggmc.morphext.integration.metamorph.MobAiToggleAction.ALIAS_ID, new aaaa0ggmc.morphext.integration.metamorph.MobAiToggleAction());

        LOGGER.info("[morphext] Registered {} vanilla mob actions into MorphManager", MorphManager.INSTANCE.actions.size());
    }

    private static void register(String id, IAction action) {
        MorphManager.INSTANCE.actions.put(id, action);
    }
}

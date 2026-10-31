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
        register(ShulkerShellAction.ID, new ShulkerShellAction());
        register(PrismarineLaserAction.ID, new PrismarineLaserAction());
        register(ElderCurseAction.ID, new ElderCurseAction());
        register(GroundSlamAction.ID, new GroundSlamAction());
        register(RapidShotAction.ID, new RapidShotAction());
        register(FrostShotAction.ID, new FrostShotAction());
        register(InkJetAction.ID, new InkJetAction());
        register(WolfHowlAction.ID, new WolfHowlAction());
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
        register(FuriousBiteAction.ID, new FuriousBiteAction());
        register(BlazeFireballAction.ID, new BlazeFireballAction());
        register(VenomStrikeAction.ID, new VenomStrikeAction());
        register(StrafeDodgeAction.ID, new StrafeDodgeAction());
        register(FrostAuraAction.ID, new FrostAuraAction());
        register(SpiderLeapAction.ID, new SpiderLeapAction());

        // 原版牲畜与被动动作
        register(CowMilkAction.ID, new CowMilkAction());
        register(MooshroomHarvestAction.ID, new MooshroomHarvestAction());
        register(SheepGrazeAction.ID, new SheepGrazeAction());
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
        register(VexPhaseAction.ID, new VexPhaseAction());
        register(VindicatorAxeChargeAction.ID, new VindicatorAxeChargeAction());
        register(IllusionBlindnessAction.ID, new IllusionBlindnessAction());
        register(EndermanTeleportAction.ID, new EndermanTeleportAction());
        register(EndermanScreechAction.ID, new EndermanScreechAction());
        register(SlimeBounceAction.ID, new SlimeBounceAction());
        register(MagmaSlamAction.ID, new MagmaSlamAction());
        register(LlamaSpitAction.ID, new LlamaSpitAction());
        register(RabbitHopAction.ID, new RabbitHopAction());
        register(PolarMaulAction.ID, new PolarMaulAction());
        register(GhastFireballAction.ID, new GhastFireballAction());
        register(GhastScreechAction.ID, new GhastScreechAction());
        register(BatRoostAction.ID, new BatRoostAction());
        register(ParrotMimicAction.ID, new ParrotMimicAction());
        register(ParrotDanceAction.ID, new ParrotDanceAction());
        register(ZombieInfectiousStrikeAction.ID, new ZombieInfectiousStrikeAction());
        register(HuskSandStrikeAction.ID, new HuskSandStrikeAction());

        LOGGER.info("[morphext] Registered {} vanilla mob actions into MorphManager", 57);
    }

    private static void register(String id, IAction action) {
        MorphManager.INSTANCE.actions.put(id, action);
    }
}

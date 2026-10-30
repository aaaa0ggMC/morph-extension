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

        LOGGER.info("[morphext] Registered {} vanilla mob actions into MorphManager", 15);
    }

    private static void register(String id, IAction action) {
        MorphManager.INSTANCE.actions.put(id, action);
    }
}

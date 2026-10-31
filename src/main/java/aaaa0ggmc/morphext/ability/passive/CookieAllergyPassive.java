package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphAbilityManager;
import aaaa0ggmc.morphext.ability.MorphContext;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 鹦鹉专属机制：曲奇饼干致命剧毒过敏 (Cookie Allergy Passive)。
 * <p>
 * 严谨复刻原版 {@link net.minecraft.entity.passive.EntityParrot#DEADLY_ITEM} 机制：
 * 原版 Minecraft 中，曲奇中的可可碱对鹦鹉具有极强毒性。向鹦鹉喂食曲奇会导致其获得 900 Tick 剧毒 II 并暴毙。
 * 当玩家变身鹦鹉形态并食用曲奇时：
 * 1. 立即触发致死级剧毒过敏反应；
 * 2. 施加 45 秒 (900 Tick) 剧毒 II 药水效果；
 * 3. 造成 19 点巨额魔法破防伤害（留存丝血警戒），并播放鹦鹉悲鸣哀嚎 {@link SoundEvents#ENTITY_PARROT_DEATH}；
 * 4. 迸发墨绿色毒雾粒子并弹出过敏提示。
 */
public class CookieAllergyPassive extends AbstractPassiveAbility {
    public static final String ID = "cookie_allergy";
    private static boolean eventRegistered = false;

    public CookieAllergyPassive(String id) {
        super(id, "morphext.passive.cookie_allergy.name", "morphext.passive.cookie_allergy.desc", new ItemStack(Items.COOKIE));
        registerEventBus();
    }

    public CookieAllergyPassive() {
        this(ID);
    }

    private static synchronized void registerEventBus() {
        if (!eventRegistered) {
            eventRegistered = true;
            MinecraftForge.EVENT_BUS.register(new CookieEventListener());
        }
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (player.world.isRemote || player.isDead || player.capabilities.isCreativeMode) {
            return;
        }

        // 当玩家正咬合曲奇时，从嘴部飘出警示毒雾粒子
        if (player.isHandActive()) {
            ItemStack using = player.getActiveItemStack();
            if (!using.isEmpty() && using.getItem() == Items.COOKIE) {
                if (player.world instanceof WorldServer && player.ticksExisted % 5 == 0) {
                    ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                            player.posX, player.posY + player.getEyeHeight() - 0.1D, player.posZ,
                            3, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
    }

    public static class CookieEventListener {
        @SubscribeEvent
        public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
            if (event.getEntityLiving() instanceof EntityPlayer && !event.getEntityLiving().world.isRemote) {
                EntityPlayer player = (EntityPlayer) event.getEntityLiving();
                if (player.capabilities.isCreativeMode) {
                    return;
                }

                ItemStack item = event.getItem();
                if (!item.isEmpty() && item.getItem() == Items.COOKIE) {
                    // 检测是否处于鹦鹉形态
                    IMorphing morphing = Morphing.get(player);
                    if (morphing != null && morphing.getCurrentMorph() != null) {
                        String name = morphing.getCurrentMorph().name;
                        if ("parrot".equalsIgnoreCase(name)) {
                            triggerFatalAllergy(player);
                        }
                    }
                }
            }
        }

        private void triggerFatalAllergy(EntityPlayer player) {
            // 1. 附加 45 秒 (900 Tick) 剧毒 II
            player.addPotionEffect(new PotionEffect(MobEffects.POISON, 900, 1));

            // 2. 造成 19 点魔法伤害（濒死残血）
            player.attackEntityFrom(DamageSource.MAGIC, 19.0F);

            // 3. 鹦鹉死亡哀鸣声
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PARROT_DEATH, SoundCategory.PLAYERS, 1.4F, 1.0F);

            // 4. 毒雾粒子炸裂
            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                        player.posX, player.posY + player.getEyeHeight(), player.posZ,
                        30, 0.3, 0.3, 0.3, 0.0);
            }

            // 5. 危险过敏警报信息
            player.sendStatusMessage(new TextComponentTranslation("morphext.passive.cookie_allergy.warning"), true);
        }
    }
}

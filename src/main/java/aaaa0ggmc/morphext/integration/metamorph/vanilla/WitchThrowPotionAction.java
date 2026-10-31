package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 女巫主动技能：喷溅有害药水投掷 (Witch Splash Potion Toss)。
 * 顺应女巫原版远程掷药攻击体系：
 * 1. 四档有害药水循环：轮流发射迟缓 (SLOWNESS)、剧毒 (POISON)、虚弱 (WEAKNESS)、瞬伤 (HARMING)；
 * 2. 目标智能博弈适配：若准星锁定目标，按原版 AI 规则智能匹配最优药剂（远距迟缓、高血施毒、近身虚弱、残血瞬伤）；
 * 3. 发射起点安全前移 1.5 格，彻底规避药水炸裂自砸反噬。
 */
public class WitchThrowPotionAction implements IAction, ICooldownAction {
    public static final String ID = "witch_throw_potion";
    public static final int COOLDOWN = 120; // 6 秒

    private static final PotionType[] HARMFUL_POTIONS = new PotionType[] {
            PotionTypes.SLOWNESS,
            PotionTypes.POISON,
            PotionTypes.WEAKNESS,
            PotionTypes.HARMING
    };

    private static final AtomicInteger CYCLE_COUNTER = new AtomicInteger(0);

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        return COOLDOWN;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }

        World world = entity.world;
        entity.swingArm(EnumHand.MAIN_HAND);

        // 准星锁定与环境判定
        EntityLivingBase target = VanillaActionHelper.findTarget(entity, 24.0D, 0.4D);
        PotionType selected;

        if (target != null) {
            // 原版女巫源码 AI 攻击逻辑：
            double dist = entity.getDistance(target);
            if (dist >= 8.0D && !target.isPotionActive(MobEffects.SLOWNESS)) {
                selected = PotionTypes.SLOWNESS;
            } else if (target.getHealth() >= 8.0F && !target.isPotionActive(MobEffects.POISON)) {
                selected = PotionTypes.POISON;
            } else if (dist <= 4.0D && !target.isPotionActive(MobEffects.WEAKNESS) && world.rand.nextFloat() < 0.35F) {
                selected = PotionTypes.WEAKNESS;
            } else {
                selected = PotionTypes.HARMING;
            }
        } else {
            // 无特定锁定目标时：平滑循环发射四类有害药水
            int index = Math.abs(CYCLE_COUNTER.getAndIncrement()) % HARMFUL_POTIONS.length;
            selected = HARMFUL_POTIONS[index];
        }

        ItemStack potionStack = PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), selected);

        // 发射起点前移 1.5 格彻底规避自砸
        Vec3d spawnPos = VanillaActionHelper.getSafeSpawnPos(entity, 1.5D);

        EntityPotion potion = new EntityPotion(world, entity, potionStack);
        potion.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        // 沿准星视线带有 -12° 优雅仰角抛出
        potion.shoot(entity, entity.rotationPitch, entity.rotationYaw, -12.0F, 0.9F, 1.0F);
        world.spawnEntity(potion);

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, spawnPos.x, spawnPos.y, spawnPos.z, 12, 0.2, 0.2, 0.2, 0.05);
        }

        world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_WITCH_THROW, SoundCategory.PLAYERS, 1.4F, 0.9F + world.rand.nextFloat() * 0.2F);
    }
}

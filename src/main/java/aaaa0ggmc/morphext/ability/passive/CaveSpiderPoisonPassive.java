package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

/**
 * 洞穴蜘蛛致命剧毒被动 (Cave Spider Venom Passive)。
 * 深度还原原版 EntityCaveSpider.attackEntityAsMob 机制：
 * 洞穴蜘蛛近战普通攻击命中存活实体时，向目标注入致命剧毒 (Poison II 持续 7 秒 / 140 ticks)，
 * 伴随节肢嘶鸣与绿色剧毒微粒。
 */
public class CaveSpiderPoisonPassive extends AbstractPassiveAbility {
    public static final String ID = "cave_spider_poison";

    public CaveSpiderPoisonPassive(String id) {
        super(id, "morphext.passive.cave_spider_poison.name", "morphext.passive.cave_spider_poison.desc", ItemStack.EMPTY);
    }

    public CaveSpiderPoisonPassive() {
        this(ID);
    }

    @Override
    public void onAttack(EntityPlayer player, EntityLivingBase target, DamageSource source, float amount, MorphContext context) {
        if (target != null && target.isEntityAlive()) {
            // 还原原版 140 ticks (7 秒) 剧毒 II (amplifier 1)
            target.addPotionEffect(new PotionEffect(MobEffects.POISON, 140, 1));

            player.world.playSound(null, target.posX, target.posY, target.posZ,
                    SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.2F, 1.35F);

            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                        target.posX, target.posY + target.height * 0.5D, target.posZ,
                        15, 0.25D, 0.25D, 0.25D, 0.05D);
            }
        }
    }
}

package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * 潜影贝主动技能：合壳坚守 (Shell Armor)。
 * 闭合坚硬紫珀壳体，获得极高伤害减免（抗性提升 IV，减少 80% 受击伤害），
 * 但在此期间移速大幅降低（缓慢 IV），持续 5 秒。
 */
public class ShulkerShellAction implements IAction, ICooldownAction {
    public static final String ID = "shulker_shell";
    public static final int COOLDOWN = 180; // 9 秒

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
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;

        // 合壳音效
        world.playSound(null, x, y, z, SoundEvents.BLOCK_SHULKER_BOX_CLOSE, SoundCategory.PLAYERS, 1.5F, 0.85F);

        // 壳体碎屑/防护粒子效果
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            int purpurState = Block.getStateId(Blocks.PURPUR_BLOCK.getDefaultState());
            ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, y + 0.6, z, 30, 0.4, 0.4, 0.4, 0.1, purpurState);
        }

        // 赋予极高抗性与缓慢状态（5 秒）
        entity.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 100, 3, false, true));
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 3, false, true));
    }
}

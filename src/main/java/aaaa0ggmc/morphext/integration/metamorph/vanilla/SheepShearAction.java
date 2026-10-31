package aaaa0ggmc.morphext.integration.metamorph.vanilla;

import aaaa0ggmc.morphext.integration.metamorph.ICooldownAction;
import aaaa0ggmc.morphext.integration.metamorph.IMorphAnimationAction;
import aaaa0ggmc.morphext.integration.metamorph.MorphSyncHelper;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.survival.PacketMorphPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 羊专属主动技能：自主剪羊毛 (Sheep Shear Action)。
 * 1. 变身羊后可按键主动脱落/修剪自身羊毛；
 * 2. 状态校验：若当前处于秃毛状态 (isSheared)，提示“羊毛尚未长出，快去吃草吧！”；
 * 3. 产物与颜色契合：掉落 1-3 团对应自身毛色的羊毛方块；
 * 4. 剪刀联动：若玩家身上或手中有剪刀，优先消耗 1 点剪刀耐久并将冷却缩短至 15 秒；
 *    若无剪刀也能自然脱落羊毛，冷却为 30 秒；
 * 5. 状态同步：调用 setSheared(true) 并广播 PacketMorphPlayer，播放剪毛音效与飞散绒毛粒子。
 */
public class SheepShearAction implements IAction, ICooldownAction, IMorphAnimationAction {
    public static final String ID = "sheep_shear";
    public static final String ALIAS_ID = "morphext:sheep_shear";
    public static final int COOLDOWN_TOOL = 300;     // 15 秒（持剪刀精修）
    public static final int COOLDOWN_NATURAL = 600;  // 30 秒（自然脱落）
    public static final int COOLDOWN_PROMPT = 20;    // 1 秒防连点

    private final DynamicActionCooldowns dynamicCooldowns = new DynamicActionCooldowns();

    @Override
    public int getCooldownTicks(EntityLivingBase entity) {
        // 非破坏查询（CORE-17）：任何额外查询不再消费 execute 写入的冷却值
        int dynamic = this.dynamicCooldowns.get(entity);
        return dynamic > 0 ? dynamic : COOLDOWN_NATURAL;
    }

    @Override
    public void execute(EntityLivingBase entity, AbstractMorph morph) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        if (!(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) entity;
        World world = player.world;

        EntitySheep sheep = null;
        NBTTagCompound entityData = null;
        if (morph instanceof EntityMorph) {
            EntityMorph em = (EntityMorph) morph;
            EntityLivingBase mob = em.getEntity(world);
            if (mob instanceof EntitySheep) {
                sheep = (EntitySheep) mob;
            }
            entityData = em.getEntityData();
        }

        // 校验秃毛状态
        boolean isSheared = false;
        if (sheep != null) {
            isSheared = sheep.getSheared();
        } else if (entityData != null && entityData.hasKey("Sheared")) {
            isSheared = entityData.getBoolean("Sheared");
        }

        if (isSheared) {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            player.sendStatusMessage(new TextComponentTranslation("morphext.action.sheep_shear.no_wool"), true);
            this.dynamicCooldowns.set(player, COOLDOWN_PROMPT);
            return;
        }

        // 检查剪刀消耗
        ItemStack shears = ItemStack.EMPTY;
        if (!player.getHeldItemMainhand().isEmpty() && player.getHeldItemMainhand().getItem() == Items.SHEARS) {
            shears = player.getHeldItemMainhand();
        } else if (!player.getHeldItemOffhand().isEmpty() && player.getHeldItemOffhand().getItem() == Items.SHEARS) {
            shears = player.getHeldItemOffhand();
        } else {
            for (ItemStack stack : player.inventory.mainInventory) {
                if (!stack.isEmpty() && stack.getItem() == Items.SHEARS) {
                    shears = stack;
                    break;
                }
            }
        }

        int cd = COOLDOWN_NATURAL;
        if (!shears.isEmpty()) {
            if (!player.isCreative()) {
                shears.damageItem(1, player);
            }
            cd = COOLDOWN_TOOL;
        }

        // 获取羊毛色彩 Metadata
        int colorMeta = 0;
        if (sheep != null) {
            EnumDyeColor dye = sheep.getFleeceColor();
            if (dye != null) {
                colorMeta = dye.getMetadata();
            }
        } else if (entityData != null && entityData.hasKey("Color")) {
            colorMeta = entityData.getByte("Color") & 15;
        }

        // 生成 1-3 团对应色彩的羊毛
        int count = 1 + world.rand.nextInt(3);
        ItemStack woolStack = new ItemStack(Blocks.WOOL, count, colorMeta);
        EntityItem drop = player.dropItem(woolStack, false);
        if (drop != null) {
            drop.motionX = player.getLookVec().x * 0.3D;
            drop.motionY = 0.25D;
            drop.motionZ = player.getLookVec().z * 0.3D;
        }

        // 切换为秃毛状态
        if (sheep != null) {
            sheep.setSheared(true);
        }
        if (entityData != null) {
            entityData.setBoolean("Sheared", true);
        }

        // 状态全网广播同步 (平滑更新秃毛外观，不触发 20-tick 翻转爆炸动画)
        MorphSyncHelper.syncMorph(player, morph);

        // 剪毛音效与飞散绒毛粒子
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.2F, 1.0F);

        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.ITEM_CRACK,
                    player.posX, player.posY + 0.6D, player.posZ,
                    30, 0.35D, 0.35D, 0.35D, 0.05D,
                    Item.getIdFromItem(Item.getItemFromBlock(Blocks.WOOL)), colorMeta);
        }

        player.swingArm(EnumHand.MAIN_HAND);
        this.dynamicCooldowns.set(player, cd);
    }
}

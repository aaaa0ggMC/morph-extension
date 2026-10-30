package aaaa0ggmc.morphext.variant;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 成长型生物形态接口（专为后续引入的 Ice and Fire 冰火龙等成长体系设计）。
 * 支持基于成长阶段（Stage / Age）或形态绑定的能力熟练度动态演进形态。
 */
public interface IGrowthVariantHandler extends IVariantHandler {

    /**
     * 根据成长进度（如能力等级、经验值、天数等）计算所处的阶段。
     */
    int getStage(int growthProgress);

    /**
     * 最大阶段数（例如冰火龙共 5 个阶段）。
     */
    int getMaxStage();

    /**
     * 获取对应阶段的变种键（例如 "stage_1", "stage_2", ..., "stage_5"）。
     */
    String getVariantForStage(int stage);

    /**
     * 构造对应阶段的实体 NBT 数据。
     */
    NBTTagCompound createEntityDataForStage(int stage, NBTTagCompound baseData);

    /**
     * 当成长阶段发生跃迁时的回调处理（如提示音、特效、刷新技能栏）。
     */
    default void onGrowthProgression(EntityPlayer player, AbstractMorph currentMorph, int oldStage, int newStage) {
    }
}

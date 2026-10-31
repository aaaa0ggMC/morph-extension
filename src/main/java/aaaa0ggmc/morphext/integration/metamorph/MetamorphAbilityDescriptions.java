package aaaa0ggmc.morphext.integration.metamorph;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Metamorph 能力详情文案门面。
 * 遵循数据驱动与国际化标准：不硬编码文本，完全由独立语言文件（.lang）驱动。
 * 默认索引键：morphext.metamorph.desc.<key>
 */
public final class MetamorphAbilityDescriptions {

    /**
     * 获取形态能力的本地化描述。
     * 若语言文件中不存在对应键，回退到自动美化的名称。
     */
    public static String get(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String langKey = "morphext.metamorph.desc." + key;

        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            String clientText = getClientLocalized(langKey);
            if (clientText != null) {
                return clientText;
            }
        }

        // 服务端或无本地化键时的优雅回退
        return MorphGrantedAbility.prettify(key);
    }

    /**
     * 获取形态动作（主动技能）的本地化名称。
     * 遵循 morphext.metamorph.action.<key> 规范，无本地化时返回 null。
     */
    public static String getActionName(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String langKey = "morphext.metamorph.action." + key;

        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            String clientText = getClientLocalized(langKey);
            if (clientText != null) {
                return clientText;
            }
        }

        return null;
    }

    @SideOnly(Side.CLIENT)
    private static String getClientLocalized(String langKey) {
        try {
            if (net.minecraft.client.resources.I18n.hasKey(langKey)) {
                return net.minecraft.client.resources.I18n.format(langKey);
            }
            // id 带命名空间前缀（如 morphext:villager_barter）时，lang 键惯例为 ':'→'.' 规范化形式
            // （与 morphext.passive.morphext.* 键一致），此处做规范化回退。
            if (langKey.indexOf(':') >= 0) {
                String normalized = langKey.replace(':', '.');
                if (net.minecraft.client.resources.I18n.hasKey(normalized)) {
                    return net.minecraft.client.resources.I18n.format(normalized);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private MetamorphAbilityDescriptions() {
    }
}

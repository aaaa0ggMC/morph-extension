package aaaa0ggmc.morphext.ability.passive;

import aaaa0ggmc.morphext.ability.AbstractPassiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/**
 * 蛛网免疫被动 (Web Immunity Passive)。
 * 深度还原原版 EntitySpider.setInWeb() 空实现：
 * 蜘蛛穿行在蜘蛛网 (Cobweb) 中完全不受阻碍，始终保持全速移动，不会被蛛网缠绕减速。
 */
public class WebImmunityPassive extends AbstractPassiveAbility {
    public static final String ID = "web_immunity";
    private static final Field IS_IN_WEB;

    static {
        Field field = null;
        try {
            field = ReflectionHelper.findField(Entity.class, "isInWeb", "field_70134_J");
            field.setAccessible(true);
        } catch (Throwable ignored) {
        }
        IS_IN_WEB = field;
    }

    public WebImmunityPassive(String id) {
        super(id, "morphext.passive.web_immunity.name", "morphext.passive.web_immunity.desc", ItemStack.EMPTY);
    }

    public WebImmunityPassive() {
        this(ID);
    }

    @Override
    public void onUpdate(EntityPlayer player, MorphContext context) {
        if (IS_IN_WEB != null) {
            try {
                IS_IN_WEB.setBoolean(player, false);
            } catch (Throwable ignored) {
            }
        }
    }
}

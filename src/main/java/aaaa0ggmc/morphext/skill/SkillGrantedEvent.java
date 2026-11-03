package aaaa0ggmc.morphext.skill;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * 技能被授予事件（指令授予与自动评估授予都发）。
 *
 * 监听方按需解锁能力、发进度、放表现——核心只负责「技能被授予了」这件事，
 * 不关心是哪个技能线、哪条整合包逻辑。发布在 MinecraftForge.EVENT_BUS。
 */
public final class SkillGrantedEvent extends Event {
    public final EntityPlayerMP player;
    /** 技能 id（已归一化）。 */
    public final String skillId;

    public SkillGrantedEvent(EntityPlayerMP player, String skillId) {
        this.player = player;
        this.skillId = skillId;
    }
}

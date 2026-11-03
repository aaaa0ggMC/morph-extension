package aaaa0ggmc.morphext.client.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 状态页区块注册表（客户端专用 SPI 入口）。
 *
 * <p>线程安全：注册可在任意线程发生，{@link #getSections()} 返回注册顺序的不可变快照，
 * 渲染侧遍历快照不受并发注册影响。</p>
 *
 * <p>用法（addon 客户端初始化时）：</p>
 * <pre>
 *     StatusSectionRegistry.register(new IStatusSection() {
 *         public String getTitle() { return I18n.format("mymod.status.team"); }
 *         public java.util.List&lt;String&gt; getLines() { return mySnapshot(); }
 *     });
 * </pre>
 */
public final class StatusSectionRegistry {
    private static final CopyOnWriteArrayList<IStatusSection> SECTIONS = new CopyOnWriteArrayList<>();

    private StatusSectionRegistry() {
    }

    /** 注册一个区块；null 与重复注册同一实例会被忽略。 */
    public static void register(IStatusSection section) {
        if (section != null && !SECTIONS.contains(section)) {
            SECTIONS.add(section);
        }
    }

    /** 当前所有已注册区块（注册顺序、不可变快照）。 */
    public static java.util.List<IStatusSection> getSections() {
        return Collections.unmodifiableList(new ArrayList<IStatusSection>(SECTIONS));
    }
}

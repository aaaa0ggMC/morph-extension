package aaaa0ggmc.morphext.client.status;

/**
 * 状态页（{@code client/gui/GuiAbilityStatus}）通用区块 SPI。
 *
 * <p>外部模组（addon）实现本接口并通过 {@link StatusSectionRegistry#register(IStatusSection)}
 * 注册后，区块会渲染在状态页现有 Passives 区域之后。核心不感知任何具体模组/整合包内容，
 * 只负责按注册顺序渲染「标题 + 逐行文本」。</p>
 *
 * <p>契约（调用方为客户端渲染线程，可能每帧调用）：</p>
 * <ul>
 *   <li>{@link #getTitle()}：已本地化的标题；返回 null/空则只渲染行内容，不画标题行。</li>
 *   <li>{@link #getLines()}：客户端可读的行文本；返回空列表（或 null）= 整个区块不显示、不占位。</li>
 *   <li>实现应便宜、无副作用、线程安全；{@code getLines()} 应返回稳定快照
 *       （如 {@code new ArrayList<>(...)} 或不可变列表），不要返回正在被其它线程修改的集合。</li>
 *   <li>客户端专用：请在客户端初始化阶段注册，服务端不要引用本包。</li>
 * </ul>
 */
public interface IStatusSection {

    /** 已本地化标题；null/空表示不渲染标题行。 */
    String getTitle();

    /** 客户端可读的行；空列表=不显示（不占位）。 */
    java.util.List<String> getLines();
}

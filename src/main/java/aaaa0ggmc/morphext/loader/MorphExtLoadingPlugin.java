package aaaa0ggmc.morphext.loader;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FML coremod 插件 + MixinBooter 早期 mixin 加载。
 *
 * <p>为什么走 {@link IEarlyMixinLoader}（MixinBooter 标了弃用的那个）：
 * <ul>
 * <li>manifest {@code MixinConfigs} 的早期扫描：容器能读到 manifest 但配置资源走
 *     LaunchClassLoader，dedicated server 上 jar 还没进类路径 → 读不到资源 → 直接终止启动；</li>
 * <li>{@code ILateMixinLoader}：依赖 FML 1.12.2 的 ASMDataTable 发现，键格式对不上，
 *     在 Forge 侧静默不触发（mixin 从未真正加载过）；</li>
 * <li>本整合包里 fastquit / jeid / vintagefix 都走 IFMLLoadingPlugin + IEarlyMixinLoader，
 *     客户端与 dedicated server 实测均可用。</li>
 * </ul>
 *
 * <p>注意：loader 类必须放在 mixin package 之外（本包），避免被 MixinTransformer 拦截。
 */
@Name("MorphExtCore")
@MCVersion("1.12.2")
@SortingIndex(Integer.MIN_VALUE)
public class MorphExtLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.morphext.json");
    }
}

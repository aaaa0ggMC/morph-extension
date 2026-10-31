package aaaa0ggmc.morphext.loader;

import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * MixinBooter 晚期注入加载器。
 * 放置在独立于 mixin package 的 loader 包中，避免被 MixinTransformer 拦截报错。
 */
@MixinLoader
public class MorphExtLateMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.morphext.json");
    }
}

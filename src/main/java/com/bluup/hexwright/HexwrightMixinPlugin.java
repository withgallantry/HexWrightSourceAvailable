package com.bluup.hexwright;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HexwrightMixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> DISABLED = parse();
    private static final boolean DISABLE_ALL = DISABLED.contains("*");

    private static Set<String> parse() {
        String raw = System.getProperty("hexwright.mixin.disable", "");
        if (raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>();
        for (String name : raw.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    @Override
    public void onLoad(String mixinPackage) {
        if (!DISABLED.isEmpty()) {
            System.out.println("[hexwright] mixins disabled by system property: " + DISABLED);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (DISABLE_ALL) {
            return false;
        }
        int dot = mixinClassName.lastIndexOf('.');
        String simple = dot < 0 ? mixinClassName : mixinClassName.substring(dot + 1);
        if (DISABLED.contains(simple)) {
            return false;
        }
        if (simple.startsWith("Sodium")) {
            return FabricLoader.getInstance().isModLoaded("sodium");
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}

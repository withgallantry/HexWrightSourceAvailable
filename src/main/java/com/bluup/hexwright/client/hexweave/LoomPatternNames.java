package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
final class LoomPatternNames {

    private static @Nullable Map<String, String> bySignature;
    private static int registrySize = -1;

    private LoomPatternNames() {
    }

    static @Nullable Component name(HexPattern pattern) {
        String signature = pattern.anglesSignature();
        String key = keys().get(signature);
        if (key != null) {
            return Component.translatable(key);
        }
        Double number = number(signature);
        if (number != null) {
            String shown = number == Math.rint(number) ? String.valueOf(number.longValue()) : number.toString();
            return Component.translatable("hexcasting.special.hexcasting:number", shown);
        }
        return null;
    }

    private static Map<String, String> keys() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        if (bySignature == null || registrySize != registry.size()) {
            Map<String, String> built = new HashMap<>();
            for (ResourceKey<ActionRegistryEntry> key : registry.registryKeySet()) {
                ActionRegistryEntry entry = registry.get(key);
                if (entry != null && !HexUtils.isOfTag(registry, key, HexTags.Actions.PER_WORLD_PATTERN)) {
                    built.putIfAbsent(entry.prototype().anglesSignature(), "hexcasting.action." + key.location());
                }
            }
            bySignature = built;
            registrySize = registry.size();
        }
        return bySignature;
    }

    private static @Nullable Double number(String signature) {
        boolean negative = signature.startsWith("dedd");
        if (!negative && !signature.startsWith("aqaa")) {
            return null;
        }
        double value = 0;
        for (char c : signature.substring(4).toCharArray()) {
            switch (c) {
                case 'w' -> value += 1;
                case 'q' -> value += 5;
                case 'e' -> value += 10;
                case 'a' -> value *= 2;
                case 'd' -> value /= 2;
                default -> {
                }
            }
        }
        return negative ? -value : value;
    }
}

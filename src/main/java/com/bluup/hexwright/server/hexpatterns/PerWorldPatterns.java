package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.common.casting.PatternRegistryManifest;
import at.petrak.hexcasting.server.ScrungledPatternsSave;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PerWorldPatterns {

    private PerWorldPatterns() {
    }

    public static void prime(MinecraftServer server) {
        ScrungledPatternsSave.open(server.overworld());
    }

    public static @Nullable HexPattern canonical(ResourceLocation action, ServerLevel level) {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        ResourceKey<ActionRegistryEntry> key = ResourceKey.create(registry.key(), action);
        if (registry.get(key) == null) {
            return null;
        }
        return PatternRegistryManifest.getCanonicalStrokesPerWorld(key, level.getServer().overworld());
    }

    public static @Nullable List<Iota> rescramble(List<Iota> hex, ServerLevel level) {
        Map<String, HexPattern> scrambles = scrambles(level);
        if (scrambles.isEmpty()) {
            return null;
        }
        List<Iota> out = new ArrayList<>(hex.size());
        boolean changed = false;
        for (Iota iota : hex) {
            HexPattern fixed = iota instanceof PatternIota pattern
                ? scrambles.get(pattern.getPattern().anglesSignature())
                : null;
            if (fixed == null) {
                out.add(iota);
            } else {
                out.add(new PatternIota(fixed));
                changed = true;
            }
        }
        return changed ? out : null;
    }

    public static @Nullable ListIota rescramble(ListIota hex, ServerLevel level) {
        List<Iota> decoded = StoredHex.decode(hex);
        if (decoded == null) {
            return null;
        }
        List<Iota> fixed = rescramble(decoded, level);
        return fixed == null ? null : new ListIota(fixed);
    }

    private static Map<String, HexPattern> scrambles(ServerLevel level) {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        ServerLevel overworld = level.getServer().overworld();
        Map<String, HexPattern> out = new HashMap<>();
        for (ResourceKey<ActionRegistryEntry> key : perWorldKeys()) {
            ActionRegistryEntry entry = registry.get(key);
            if (entry == null) {
                continue;
            }
            HexPattern here = PatternRegistryManifest.getCanonicalStrokesPerWorld(key, overworld);
            if (here == null) {
                Hexwright.LOGGER.warn("No per-world drawing for {} in this world; "
                    + "run '/hexcasting recalcPatterns' if inscribed Great Spells stop resolving", key.location());
                continue;
            }
            String prototype = entry.prototype().anglesSignature();
            if (!prototype.equals(here.anglesSignature())) {
                out.put(prototype, here);
            }
        }
        return out;
    }

    private static List<ResourceKey<ActionRegistryEntry>> perWorldKeys() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        List<ResourceKey<ActionRegistryEntry>> keys = new ArrayList<>();
        for (ResourceKey<ActionRegistryEntry> key : registry.registryKeySet()) {
            if (HexUtils.isOfTag(registry, key, HexTags.Actions.PER_WORLD_PATTERN)) {
                keys.add(key);
            }
        }
        return keys;
    }
}

package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RecipeTablets {

    private static final String CLASSPATH_DEFAULT = "/assets/hexwright/progression/recipe_tablets.json";
    private static final String OVERRIDE_FILE_NAME = "hexwright-recipe-tablets.json";

    private static final Gson GSON = new Gson();

    private static final long NO_OVERRIDE = -1L;

    private static final long STAT_INTERVAL_NANOS = 2_000_000_000L;

    private static Config cached;

    private static long cachedStamp = NO_OVERRIDE;

    private static long lastStatNanos;

    private RecipeTablets() {
    }

    public record LootEntry(String recipe, int weight) {
    }

    public record LootDrop(String id, ResourceLocation table, float chance, int rolls, List<LootEntry> entries) {
    }

    public record Config(Set<String> gatedRecipes, Set<String> siteRecipes, List<LootDrop> drops) {
    }

    public static synchronized Config get() {
        Config current = cached;

        long now = System.nanoTime();
        if (current != null && now - lastStatNanos < STAT_INTERVAL_NANOS) {
            return current;
        }
        lastStatNanos = now;

        Path override = overridePath();
        long stamp = stampOf(override);

        if (current != null && stamp == cachedStamp) {
            return current;
        }

        Config loaded = read(override, stamp != NO_OVERRIDE);
        cached = loaded;
        cachedStamp = stamp;
        return loaded;
    }

    public static boolean requiresTablet(String recipeNameKey) {
        return get().gatedRecipes().contains(recipeNameKey);
    }

    public static boolean requiresSite(String recipeNameKey) {
        return get().siteRecipes().contains(recipeNameKey);
    }

    public static boolean requiresDiscovery(String recipeNameKey) {
        Config config = get();
        return config.gatedRecipes().contains(recipeNameKey) || config.siteRecipes().contains(recipeNameKey);
    }

    public static void validate(Set<String> knownRecipeKeys) {
        if (knownRecipeKeys.isEmpty()) {
            return;
        }
        Config config = get();

        Set<String> unknownGates = new LinkedHashSet<>(config.gatedRecipes());
        unknownGates.addAll(config.siteRecipes());
        unknownGates.removeAll(knownRecipeKeys);
        if (!unknownGates.isEmpty()) {
            Hexwright.LOGGER.warn(
                "Stone Tablet config gates {} recipe(s) the Essence Forge doesn't have: {}",
                unknownGates.size(), unknownGates
            );
        }

        Set<String> dropped = new HashSet<>();
        for (LootDrop drop : config.drops()) {
            for (LootEntry entry : drop.entries()) {
                dropped.add(entry.recipe());
                if (!config.gatedRecipes().contains(entry.recipe())) {
                    Hexwright.LOGGER.warn(
                        "Stone Tablet loot '{}' drops a tablet for '{}', which isn't gated - it will unlock nothing",
                        drop.id(), entry.recipe()
                    );
                }
            }
        }

        Set<String> unobtainable = new LinkedHashSet<>(config.gatedRecipes());
        unobtainable.retainAll(knownRecipeKeys);
        unobtainable.removeAll(dropped);
        unobtainable.removeAll(config.siteRecipes());
        if (!unobtainable.isEmpty()) {
            Hexwright.LOGGER.warn(
                "{} gated recipe(s) have no tablet in any loot table and cannot be discovered: {}",
                unobtainable.size(), unobtainable
            );
        }
    }

    private static Path overridePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(OVERRIDE_FILE_NAME);
    }

    private static long stampOf(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : NO_OVERRIDE;
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to stat Stone Tablet config override at {}", path, t);
            return NO_OVERRIDE;
        }
    }

    private static Config read(Path override, boolean useOverride) {
        String source = useOverride ? override.toString() : CLASSPATH_DEFAULT;
        try (Reader reader = openReader(override, useOverride)) {
            if (reader == null) {
                Hexwright.LOGGER.error("Stone Tablet config missing at {}; no recipe will be gated", CLASSPATH_DEFAULT);
                return empty();
            }
            Config parsed = parse(GSON.fromJson(reader, Document.class));
            HexwrightDebug.log(HexwrightDebug.CONTENT,
                "Loaded Stone Tablet config from {}: {} gated, {} site recipe(s), {} loot source(s)",
                source, parsed.gatedRecipes().size(), parsed.siteRecipes().size(), parsed.drops().size()
            );
            return parsed;
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to read Stone Tablet config from {}; no recipe will be gated", source, t);
            return empty();
        }
    }

    private static Reader openReader(Path override, boolean useOverride) throws Exception {
        if (useOverride) {
            return Files.newBufferedReader(override, StandardCharsets.UTF_8);
        }
        InputStream stream = RecipeTablets.class.getResourceAsStream(CLASSPATH_DEFAULT);
        return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static Config parse(Document document) {
        if (document == null) {
            return empty();
        }

        Set<String> gated = names(document.recipes);
        Set<String> sites = names(document.site_recipes);
        sites.removeAll(gated);

        List<LootDrop> drops = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        if (document.loot != null) {
            for (Loot loot : document.loot) {
                LootDrop drop = parseDrop(loot, seenIds);
                if (drop != null) {
                    drops.add(drop);
                }
            }
        }

        return new Config(
            Collections.unmodifiableSet(gated),
            Collections.unmodifiableSet(sites),
            Collections.unmodifiableList(drops)
        );
    }

    private static Set<String> names(List<String> raw) {
        Set<String> names = new LinkedHashSet<>();
        if (raw != null) {
            for (String name : raw) {
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static LootDrop parseDrop(Loot loot, Set<String> seenIds) {
        if (loot == null) {
            return null;
        }
        String id = loot.id == null || loot.id.isBlank() ? loot.table : loot.id;
        if (id == null || id.isBlank()) {
            Hexwright.LOGGER.warn("Stone Tablet loot entry has neither an id nor a table; skipping");
            return null;
        }
        if (!seenIds.add(id)) {
            Hexwright.LOGGER.warn("Duplicate Stone Tablet loot id '{}'; keeping the first", id);
            return null;
        }
        ResourceLocation table = loot.table == null ? null : ResourceLocation.tryParse(loot.table);
        if (table == null) {
            Hexwright.LOGGER.warn("Stone Tablet loot '{}' names no valid loot table; skipping", id);
            return null;
        }

        float chance = Math.max(0f, Math.min(1f, loot.chance == null ? 1f : loot.chance));
        int rolls = Math.max(1, loot.rolls == null ? 1 : loot.rolls);

        List<LootEntry> entries = new ArrayList<>();
        if (loot.entries != null) {
            for (Entry entry : loot.entries) {
                if (entry == null || entry.recipe == null || entry.recipe.isBlank()) {
                    continue;
                }
                int weight = Math.max(1, entry.weight == null ? 1 : entry.weight);
                entries.add(new LootEntry(entry.recipe, weight));
            }
        }
        if (entries.isEmpty()) {
            Hexwright.LOGGER.warn("Stone Tablet loot '{}' lists no tablets; skipping", id);
            return null;
        }

        return new LootDrop(id, table, chance, rolls, Collections.unmodifiableList(entries));
    }

    private static Config empty() {
        return new Config(Set.of(), Set.of(), List.of());
    }

    private static final class Document {
        List<String> recipes;
        List<String> site_recipes;
        List<Loot> loot;
    }

    private static final class Loot {
        String id;
        String table;
        Float chance;
        Integer rolls;
        List<Entry> entries;
    }

    private static final class Entry {
        String recipe;
        Integer weight;
    }
}

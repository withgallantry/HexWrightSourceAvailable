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

    public record TabletDrop(String id, List<ResourceLocation> tables, float chance, int minCount, int maxCount) {
    }

    public record Config(
        Set<String> gatedRecipes,
        Set<String> siteRecipes,
        Set<String> artifactRecipes,
        List<TabletDrop> ruinedDrops,
        List<TabletDrop> artifactDrops
    ) {
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

    public static boolean requiresArtifactTablet(String recipeNameKey) {
        return get().artifactRecipes().contains(recipeNameKey);
    }

    public static boolean requiresDiscovery(String recipeNameKey) {
        Config config = get();
        return config.gatedRecipes().contains(recipeNameKey)
            || config.siteRecipes().contains(recipeNameKey)
            || config.artifactRecipes().contains(recipeNameKey);
    }

    public static void validate(Set<String> knownRecipeKeys, Set<String> artifactRecipeKeys) {
        if (knownRecipeKeys.isEmpty()) {
            return;
        }
        Config config = get();

        Set<String> unknownGates = new LinkedHashSet<>(config.gatedRecipes());
        unknownGates.addAll(config.siteRecipes());
        unknownGates.addAll(config.artifactRecipes());
        unknownGates.removeAll(knownRecipeKeys);
        if (!unknownGates.isEmpty()) {
            Hexwright.LOGGER.warn(
                "recipe tablet config gates {} recipe(s) the Essence Forge doesn't have: {}",
                unknownGates.size(), unknownGates
            );
        }

        if (!config.artifactRecipes().isEmpty() && config.artifactDrops().isEmpty()) {
            Hexwright.LOGGER.warn(
                "{} artifact recipe(s) are gated but no chest drops Artifact Ruined Tablets - only a "
                    + "hand-placed one can teach them",
                config.artifactRecipes().size()
            );
        }

        Set<String> ungatedArtifacts = new LinkedHashSet<>(artifactRecipeKeys);
        ungatedArtifacts.removeIf(RecipeTablets::requiresDiscovery);
        if (!ungatedArtifacts.isEmpty()) {
            Hexwright.LOGGER.warn(
                "{} Essence Forge recipe(s) forge an artifact but aren't gated - anyone can forge them: {}",
                ungatedArtifacts.size(), ungatedArtifacts
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
            Hexwright.LOGGER.error("Failed to stat recipe tablet config override at {}", path, t);
            return NO_OVERRIDE;
        }
    }

    private static Config read(Path override, boolean useOverride) {
        String source = useOverride ? override.toString() : CLASSPATH_DEFAULT;
        try (Reader reader = openReader(override, useOverride)) {
            if (reader == null) {
                Hexwright.LOGGER.error("recipe tablet config missing at {}; no recipe will be gated", CLASSPATH_DEFAULT);
                return empty();
            }
            Config parsed = parse(GSON.fromJson(reader, Document.class));
            HexwrightDebug.log(HexwrightDebug.CONTENT,
                "Loaded recipe tablet config from {}: {} gated, {} site, {} artifact recipe(s), "
                    + "{} ruined tablet tier(s), {} artifact tablet tier(s)",
                source, parsed.gatedRecipes().size(), parsed.siteRecipes().size(),
                parsed.artifactRecipes().size(), parsed.ruinedDrops().size(), parsed.artifactDrops().size()
            );
            return parsed;
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to read recipe tablet config from {}; no recipe will be gated", source, t);
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
        Set<String> artifacts = names(document.artifact_recipes);
        sites.removeAll(gated);
        sites.removeAll(artifacts);

        return new Config(
            Collections.unmodifiableSet(gated),
            Collections.unmodifiableSet(sites),
            Collections.unmodifiableSet(artifacts),
            parseDrops(document.ruined_tablet_loot, "Ruined Tablet"),
            parseDrops(document.artifact_tablet_loot, "Artifact Ruined Tablet")
        );
    }

    private static List<TabletDrop> parseDrops(List<LootTier> tiers, String what) {
        List<TabletDrop> drops = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        if (tiers != null) {
            for (LootTier tier : tiers) {
                TabletDrop drop = parseDrop(tier, seenIds, what);
                if (drop != null) {
                    drops.add(drop);
                }
            }
        }
        return Collections.unmodifiableList(drops);
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

    private static TabletDrop parseDrop(LootTier loot, Set<String> seenIds, String what) {
        if (loot == null || loot.id == null || loot.id.isBlank()) {
            Hexwright.LOGGER.warn("{} loot tier has no id; skipping", what);
            return null;
        }
        if (!seenIds.add(loot.id)) {
            Hexwright.LOGGER.warn("Duplicate {} loot tier '{}'; keeping the first", what, loot.id);
            return null;
        }

        List<ResourceLocation> tables = new ArrayList<>();
        if (loot.tables != null) {
            for (String name : loot.tables) {
                ResourceLocation table = name == null ? null : ResourceLocation.tryParse(name);
                if (table == null) {
                    Hexwright.LOGGER.warn("{} loot tier '{}' names an invalid loot table '{}'; skipping it", what, loot.id, name);
                    continue;
                }
                tables.add(table);
            }
        }
        if (tables.isEmpty()) {
            Hexwright.LOGGER.warn("{} loot tier '{}' names no loot tables; skipping", what, loot.id);
            return null;
        }

        float chance = Math.max(0f, Math.min(1f, loot.chance == null ? 1f : loot.chance));
        int min = Math.max(1, loot.count == null || loot.count.min == null ? 1 : loot.count.min);
        int max = Math.max(min, loot.count == null || loot.count.max == null ? min : loot.count.max);

        return new TabletDrop(loot.id, Collections.unmodifiableList(tables), chance, min, max);
    }

    private static Config empty() {
        return new Config(Set.of(), Set.of(), Set.of(), List.of(), List.of());
    }

    private static final class Document {
        List<String> recipes;
        List<String> site_recipes;
        List<String> artifact_recipes;
        List<LootTier> ruined_tablet_loot;
        List<LootTier> artifact_tablet_loot;
    }

    private static final class LootTier {
        String id;
        List<String> tables;
        Float chance;
        Count count;
    }

    private static final class Count {
        Integer min;
        Integer max;
    }
}

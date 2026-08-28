package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.google.gson.Gson
import com.google.gson.JsonParseException
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object EssenceForgeCategories {

    data class ForgeCategory(
        val id: String,
        val name: String,
        val recipeKeys: Set<String>?
    ) {
        fun label(): String =
            if (Language.getInstance().has(name)) Component.translatable(name).string else name

        fun lists(recipeNameKey: String): Boolean = recipeKeys == null || recipeNameKey in recipeKeys
    }

    private const val CLASSPATH_DEFAULT = "/assets/hexwright/essence_forge/recipe_categories.json"
    private const val OVERRIDE_FILE_NAME = "hexwright-essence-forge-categories.json"

    private const val ALL_NAME_KEY = "gui.hexwright.worktable.category.all"

    private val GSON = Gson()

    private var cached: List<ForgeCategory>? = null

    private var cachedStamp: Long = NO_OVERRIDE

    private const val NO_OVERRIDE = -1L

    @Synchronized
    fun get(knownRecipeKeys: Set<String>): List<ForgeCategory> {
        val override = overridePath()
        val stamp = stampOf(override)

        val current = cached
        if (current != null && stamp == cachedStamp) {
            return current
        }

        val loaded = read(override, stamp != NO_OVERRIDE)
        validate(loaded, knownRecipeKeys)
        cached = loaded
        cachedStamp = stamp
        return loaded
    }

    private fun overridePath(): Path =
        FabricLoader.getInstance().configDir.resolve(OVERRIDE_FILE_NAME)

    private fun stampOf(path: Path): Long = try {
        if (Files.exists(path)) Files.getLastModifiedTime(path).toMillis() else NO_OVERRIDE
    } catch (t: Throwable) {
        Hexwright.LOGGER.error("Failed to stat Essence Forge category override at {}", path, t)
        NO_OVERRIDE
    }

    private fun read(override: Path, useOverride: Boolean): List<ForgeCategory> {
        val source = if (useOverride) override.toString() else CLASSPATH_DEFAULT
        try {
            openReader(override, useOverride).use { reader ->
                if (reader == null) {
                    Hexwright.LOGGER.error("Essence Forge categories missing at $CLASSPATH_DEFAULT; listing every recipe")
                    return fallback()
                }
                val parsed = parse(GSON.fromJson(reader, Document::class.java))
                if (parsed.isEmpty()) {
                    Hexwright.LOGGER.error("Essence Forge categories at {} define no usable category; listing every recipe", source)
                    return fallback()
                }
                Hexwright.LOGGER.info("Loaded {} Essence Forge recipe categories from {}", parsed.size, source)
                return parsed
            }
        } catch (t: Throwable) {
            Hexwright.LOGGER.error("Failed to read Essence Forge categories from {}; listing every recipe", source, t)
            return fallback()
        }
    }

    private fun openReader(override: Path, useOverride: Boolean): Reader? {
        if (useOverride) {
            return Files.newBufferedReader(override, StandardCharsets.UTF_8)
        }
        val stream = EssenceForgeCategories::class.java.getResourceAsStream(CLASSPATH_DEFAULT) ?: return null
        return InputStreamReader(stream, StandardCharsets.UTF_8)
    }

    private fun parse(document: Document?): List<ForgeCategory> {
        val entries = document?.categories ?: return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<ForgeCategory>(entries.size)
        for (entry in entries) {
            val id = entry?.id?.takeIf { it.isNotBlank() } ?: continue
            val name = entry.name?.takeIf { it.isNotBlank() } ?: id
            if (!seen.add(id)) {
                Hexwright.LOGGER.warn("Duplicate Essence Forge category id '{}'; keeping the first", id)
                continue
            }
            val recipes = entry.recipes?.filterNotNull()?.toSet()
            result.add(ForgeCategory(id, name, recipes))
        }
        return result
    }

    private fun validate(categories: List<ForgeCategory>, knownRecipeKeys: Set<String>) {
        if (knownRecipeKeys.isEmpty()) return

        val listed = HashSet<String>()
        for (category in categories) {
            val keys = category.recipeKeys ?: continue
            listed.addAll(keys)
            val unknown = keys - knownRecipeKeys
            if (unknown.isNotEmpty()) {
                Hexwright.LOGGER.warn(
                    "Essence Forge category '{}' names {} unknown recipe(s): {}",
                    category.id, unknown.size, unknown.sorted()
                )
            }
        }

        if (categories.any { it.recipeKeys != null }) {
            val uncovered = knownRecipeKeys - listed
            if (uncovered.isNotEmpty()) {
                Hexwright.LOGGER.warn(
                    "{} Essence Forge recipe(s) are in no category and will only show under an unfiltered entry: {}",
                    uncovered.size, uncovered.sorted()
                )
            }
        }
    }

    private fun fallback(): List<ForgeCategory> = listOf(ForgeCategory("all", ALL_NAME_KEY, null))

    private class Document {
        var categories: List<Entry?>? = null
    }

    private class Entry {
        var id: String? = null
        var name: String? = null
        var recipes: List<String?>? = null
    }
}

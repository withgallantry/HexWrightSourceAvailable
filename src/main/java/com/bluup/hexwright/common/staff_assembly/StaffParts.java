package com.bluup.hexwright.common.staff_assembly;

import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class StaffParts {
    public static final String NONE_ID = "none";

    private static final Set<String> BOOK_TAGS = Set.of(StaffPart.TAG_BOOK, StaffPart.TAG_NAME_OMITS_STAFF);

    private static final List<StaffPart> MODEL = List.of(
        modelOption("blossom_staff", EfficiencyRating.FINE),
        modelOption("dark_forest_staff", EfficiencyRating.FINE),
        modelOption("eclipse_staff", EfficiencyRating.EXQUISITE),
        modelOption("end_staff", EfficiencyRating.EXQUISITE),
        modelOption("fern_staff", EfficiencyRating.FINE),
        modelOption("geode_staff", EfficiencyRating.FINE),
        modelOption("hydra_staff", EfficiencyRating.FINE),
        modelOption("light_staff", EfficiencyRating.EXQUISITE),
        modelOption("nether_staff", EfficiencyRating.EXQUISITE),
        modelOption("ocean_staff", EfficiencyRating.FINE),
        modelOption("penitence_staff", EfficiencyRating.FINE),
        modelOption("redstone_staff", EfficiencyRating.EXQUISITE),
        modelOption("shadow_staff", EfficiencyRating.EXQUISITE),
        modelOption("totem_staff", EfficiencyRating.EXQUISITE),
        modelOption("wind_staff", EfficiencyRating.EXQUISITE),
        modelOption("bermundes_hermit_staff", EfficiencyRating.SOUND),
        modelOption("black_snake_staff", EfficiencyRating.SOUND),
        modelOption("divine_staff", EfficiencyRating.SOUND),
        modelOption("goblin_witch_staff", EfficiencyRating.SOUND),
        modelOption("golden_crook_staff", EfficiencyRating.SOUND, Set.of(StaffPart.TAG_NAME_OMITS_STAFF)),
        modelOption("holy_celtic_staff", EfficiencyRating.SOUND),
        modelOption("lich_staff", EfficiencyRating.SOUND),
        modelOption("night_staff", EfficiencyRating.SOUND),
        modelOption("northgate_guardian_staff", EfficiencyRating.SOUND),
        modelOption("olwen_staff", EfficiencyRating.SOUND),
        modelOption("sagrada_winged_staff", EfficiencyRating.SOUND),
        modelOption("unholy_wisdom_staff", EfficiencyRating.SOUND),
        modelOption("arcane_staff", EfficiencyRating.EXQUISITE),
        modelOption("dark_staff", EfficiencyRating.EXQUISITE),
        modelOption("fire_staff", EfficiencyRating.FINE),
        modelOption("gale_staff", EfficiencyRating.FINE),
        modelOption("gravity_staff", EfficiencyRating.EXQUISITE),
        modelOption("great_staff", EfficiencyRating.FINE),
        modelOption("heal_staff", EfficiencyRating.EXQUISITE),
        modelOption("ice_staff", EfficiencyRating.EXQUISITE),
        modelOption("mage_staff", EfficiencyRating.EXQUISITE),
        modelOption("mana_staff", EfficiencyRating.EXQUISITE),
        modelOption("master_staff", EfficiencyRating.EXQUISITE),
        modelOption("royal_mana_staff", EfficiencyRating.EXQUISITE),
        modelOption("simple_fire_staff", EfficiencyRating.FINE),
        modelOption("simple_ice_staff", EfficiencyRating.FINE),
        modelOption("simple_mana_staff", EfficiencyRating.FINE),
        modelOption("simple_staff", EfficiencyRating.CRUDE),
        modelOption("solar_staff", EfficiencyRating.EXQUISITE),
        modelOption("thunder_staff", EfficiencyRating.EXQUISITE),
        modelOption("verdant_staff", EfficiencyRating.EXQUISITE),
        modelOption("vile_staff", EfficiencyRating.EXQUISITE),
        modelOption("hexicon_staff", EfficiencyRating.EXQUISITE, BOOK_TAGS),
        modelOption("ocular_hexicon_staff", EfficiencyRating.EXQUISITE, BOOK_TAGS),
        modelOption("emerald_hexicon_staff", EfficiencyRating.EXQUISITE, BOOK_TAGS),
        modelOption("obsidian_hexicon_staff", EfficiencyRating.EXQUISITE, BOOK_TAGS),
        modelOption("azure_hexicon_staff", EfficiencyRating.EXQUISITE, BOOK_TAGS),
        modelOption("crystal_staff", EfficiencyRating.MASTERWORK),
        modelOption("darkness_staff", EfficiencyRating.MASTERWORK),
        modelOption("earth_staff", EfficiencyRating.MASTERWORK),
        modelOption("evil_staff", EfficiencyRating.MASTERWORK),
        modelOption("great_mage_staff", EfficiencyRating.MASTERWORK),
        modelOption("ice_crystal_staff", EfficiencyRating.MASTERWORK),
        modelOption("moon_staff", EfficiencyRating.MASTERWORK),
        modelOption("nature_staff", EfficiencyRating.MASTERWORK),
        modelOption("red_royal_staff", EfficiencyRating.MASTERWORK),
        modelOption("royal_staff", EfficiencyRating.MASTERWORK),
        modelOption("shrine_staff", EfficiencyRating.MASTERWORK),
        modelOption("skull_staff", EfficiencyRating.MASTERWORK),
        modelOption("soul_fire_staff", EfficiencyRating.MASTERWORK),
        modelOption("soul_staff", EfficiencyRating.MASTERWORK),
        modelOption("sun_staff", EfficiencyRating.MASTERWORK),
        modelOption("bone_staff", EfficiencyRating.FINE),
        modelOption("celestial_staff", EfficiencyRating.FINE),
        modelOption("clockwork_staff", EfficiencyRating.FINE),
        modelOption("dragonfire_staff", EfficiencyRating.FINE),
        modelOption("stormcaller_staff", EfficiencyRating.FINE),
        modelOption("sunforge_staff", EfficiencyRating.FINE),
        modelOption("void_staff", EfficiencyRating.FINE),
        modelOption("coral_staff", EfficiencyRating.FINE),
        modelOption("bloom_stone_staff", EfficiencyRating.FINE),
        modelOption("dark_forest_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("eclipse_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("end_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("engineers_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("geode_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("holy_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("hydra_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("light_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("lovers_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("nature_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("nether_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("ocean_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("shadow_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("totem_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS),
        modelOption("weather_tome_staff", EfficiencyRating.MASTERWORK, BOOK_TAGS)
    );

    private static final List<StaffPart> CORE = List.of(
        placeholderOption(StaffPartCategory.CORE, "amethyst_core", HexwrightItems.AMETHYST_CORE),
        placeholderOption(StaffPartCategory.CORE, "quartz_core", HexwrightItems.QUARTZ_CORE),
        placeholderOption(StaffPartCategory.CORE, "scribe_core", HexwrightItems.SCRIBE_CORE),
        placeholderOption(StaffPartCategory.CORE, "traveller_core", HexwrightItems.TRAVELLER_CORE),
        placeholderOption(StaffPartCategory.CORE, "echo_core", HexwrightItems.ECHO_CORE)
    );

    private static final List<StaffPart> BINDING = List.of(
        placeholderOption(StaffPartCategory.BINDING, "leather", Items.LEATHER),
        placeholderOption(StaffPartCategory.BINDING, "chain", Items.CHAIN),
        placeholderOption(StaffPartCategory.BINDING, "gold", Items.GOLD_INGOT)
    );

    private static final List<StaffPart> FOCUS = List.of(
        placeholderOption(StaffPartCategory.FOCUS, "glass", Items.GLASS),
        placeholderOption(StaffPartCategory.FOCUS, "amethyst_cluster", Items.AMETHYST_CLUSTER),
        placeholderOption(StaffPartCategory.FOCUS, "blaze", Items.BLAZE_POWDER)
    );

    private static final List<StaffPart> CATALYST = List.of(
        placeholderOption(StaffPartCategory.CATALYST, NONE_ID, Items.BARRIER),
        placeholderOption(StaffPartCategory.CATALYST, "echo", Items.ECHO_SHARD),
        placeholderOption(StaffPartCategory.CATALYST, "blaze", Items.BLAZE_POWDER)
    );

    private StaffParts() {
    }

    public static List<StaffPart> options(StaffPartCategory category) {
        return switch (category) {
            case MODEL -> MODEL;
            case CORE -> CORE;
            case BINDING -> BINDING;
            case FOCUS -> FOCUS;
            case CATALYST -> CATALYST;
        };
    }

    public static Optional<StaffPart> find(StaffPartCategory category, String id) {
        return options(category).stream().filter(part -> part.id().equals(id)).findFirst();
    }

    public static boolean isBookModel(ItemStack stack) {
        String modelId = StaffAssemblyData.getPart(stack, StaffPartCategory.MODEL);
        if (modelId == null) {
            return false;
        }
        return find(StaffPartCategory.MODEL, modelId)
            .map(part -> part.hasTag(StaffPart.TAG_BOOK))
            .orElse(false);
    }

    public static boolean isUnlocked(StaffPart part, EfficiencyRating currentQuality) {
        return currentQuality.ordinal() >= part.requiredQuality().ordinal();
    }

    private static StaffPart modelOption(String modelId, EfficiencyRating requiredQuality) {
        return modelOption(modelId, requiredQuality, Set.of());
    }

    private static StaffPart modelOption(String modelId, EfficiencyRating requiredQuality, Set<String> tags) {
        return new StaffPart(
            StaffPartCategory.MODEL,
            modelId,
            Component.translatable("item.hexwright." + modelId),
            previewIcon(modelId),
            requiredQuality,
            tags
        );
    }

    private static ItemStack previewIcon(String modelId) {
        ItemStack icon = new ItemStack(HexwrightItems.CONFIGURABLE_STAFF);
        StaffAssemblyData.setPart(icon, StaffPartCategory.MODEL, modelId);
        return icon;
    }

    private static StaffPart placeholderOption(StaffPartCategory category, String id, ItemLike icon) {
        Component displayName = Component.translatable(
            "staff_part.hexwright." + category.name().toLowerCase(Locale.ROOT) + "." + id);
        return new StaffPart(category, id, displayName, new ItemStack(icon), EfficiencyRating.CRUDE, Set.of());
    }
}

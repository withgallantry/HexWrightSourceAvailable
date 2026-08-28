package com.bluup.hexwright.common.staff_assembly.calc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory.*;

public final class IngredientRegistry {

    private static final Map<Item, IngredientData> INGREDIENT_OVERRIDES = Map.ofEntries(
        entry(Items.LEATHER, "leather", "Leather", 3, 2, LEATHER, ORGANIC, FLEXIBLE),
        entry(Items.STRING, "string", "String", 2, 1, THREAD, FLEXIBLE, LIGHT),
        entry(Items.PHANTOM_MEMBRANE, "phantom_membrane", "Phantom Membrane", 12, 7, LIGHT, FLEXIBLE, ARCANE),
        entry(Items.FEATHER, "feather", "Feather", 3, 1, LIGHT, ORGANIC, FLEXIBLE),
        entry(Items.CHAIN, "chain", "Chain", 6, 4, METALLIC, FLEXIBLE),

        entry(Items.GLASS, "glass", "Glass", 2, 1, CRYSTAL, LIGHT),
        entry(Items.AMETHYST_SHARD, "amethyst_shard", "Amethyst Shard", 10, 6, CRYSTAL, ARCANE, RADIANT),
        entry(Items.AMETHYST_CLUSTER, "amethyst_cluster", "Amethyst Cluster", 14, 8, CRYSTAL, ARCANE, RADIANT),
        entry(Items.QUARTZ, "quartz", "Quartz", 9, 5, CRYSTAL, NETHER, ARCANE),

        entry(Items.ENDER_PEARL, "ender_pearl", "Ender Pearl", 20, 12, SPATIAL, END, ARCANE),
        entry(Items.ENDER_EYE, "ender_eye", "Eye of Ender", 26, 16, SPATIAL, END, ARCANE, PRESTIGE),
        entry(Items.CHORUS_FRUIT, "chorus_fruit", "Chorus Fruit", 12, 7, SPATIAL, END, ORGANIC),
        entry(Items.POPPED_CHORUS_FRUIT, "popped_chorus_fruit", "Popped Chorus Fruit", 14, 8, SPATIAL, END),

        entry(Items.REDSTONE, "redstone", "Redstone Dust", 5, 3, ENERGETIC, ARCANE),
        entry(Items.GLOWSTONE_DUST, "glowstone_dust", "Glowstone Dust", 12, 7, RADIANT, ENERGETIC, NETHER),
        entry(Items.GLOWSTONE, "glowstone", "Glowstone", 18, 11, RADIANT, ENERGETIC, NETHER),
        entry(Items.BLAZE_POWDER, "blaze_powder", "Blaze Powder", 16, 10, FIRE, ENERGETIC, NETHER),
        entry(Items.BLAZE_ROD, "blaze_rod", "Blaze Rod", 22, 14, FIRE, ENERGETIC, NETHER),
        entry(Items.FIRE_CHARGE, "fire_charge", "Fire Charge", 12, 8, FIRE, ENERGETIC),

        entry(Items.GHAST_TEAR, "ghast_tear", "Ghast Tear", 24, 15, NETHER, ARCANE, ORGANIC),
        entry(Items.MAGMA_CREAM, "magma_cream", "Magma Cream", 14, 9, FIRE, NETHER, ORGANIC),
        entry(Items.NETHER_WART, "nether_wart", "Nether Wart", 8, 5, NETHER, ORGANIC, ARCANE),
        entry(Items.NETHERITE_SCRAP, "netherite_scrap", "Netherite Scrap", 30, 20, METALLIC, NETHER, PRESTIGE),
        entry(Items.NETHERITE_INGOT, "netherite_ingot", "Netherite Ingot", 42, 28, METALLIC, NETHER, PRESTIGE),

        entry(Items.ECHO_SHARD, "echo_shard", "Echo Shard", 28, 18, ECHO, END, SPATIAL),
        entry(Items.SCULK, "sculk", "Sculk", 10, 7, ECHO, ARCANE, ORGANIC),
        entry(Items.SCULK_SENSOR, "sculk_sensor", "Sculk Sensor", 18, 12, ECHO, ARCANE, ENERGETIC),
        entry(Items.SCULK_CATALYST, "sculk_catalyst", "Sculk Catalyst", 26, 17, ECHO, ARCANE, ENERGETIC),
        entry(Items.SCULK_SHRIEKER, "sculk_shrieker", "Sculk Shrieker", 24, 16, ECHO, ARCANE),

        entry(Items.DIAMOND, "diamond", "Diamond", 22, 14, CRYSTAL, RADIANT, PRESTIGE),
        entry(Items.EMERALD, "emerald", "Emerald", 18, 11, CRYSTAL, RADIANT, PRESTIGE),
        entry(Items.LAPIS_LAZULI, "lapis_lazuli", "Lapis Lazuli", 8, 5, CRYSTAL, ARCANE),
        entry(Items.GOLD_INGOT, "gold_ingot", "Gold Ingot", 10, 6, METALLIC, RADIANT, PRESTIGE),
        entry(Items.IRON_INGOT, "iron_ingot", "Iron Ingot", 8, 5, METALLIC),
        entry(Items.COPPER_INGOT, "copper_ingot", "Copper Ingot", 5, 3, METALLIC, ENERGETIC),
        entry(Items.COAL, "coal", "Coal", 4, 2, ENERGETIC, ORGANIC),
        entry(Items.CHARCOAL, "charcoal", "Charcoal", 3, 2, ENERGETIC, ORGANIC),

        entry(Items.PRISMARINE_SHARD, "prismarine_shard", "Prismarine Shard", 12, 7, CRYSTAL, SPATIAL),
        entry(Items.PRISMARINE_CRYSTALS, "prismarine_crystals", "Prismarine Crystals", 16, 10, CRYSTAL, RADIANT, SPATIAL),
        entry(Items.HEART_OF_THE_SEA, "heart_of_the_sea", "Heart of the Sea", 36, 24, SPATIAL, ARCANE, PRESTIGE),
        entry(Items.NAUTILUS_SHELL, "nautilus_shell", "Nautilus Shell", 18, 12, SPATIAL, ORGANIC, ARCANE),
        entry(Items.CONDUIT, "conduit", "Conduit", 42, 30, SPATIAL, ARCANE, PRESTIGE),

        entry(Items.TOTEM_OF_UNDYING, "totem_of_undying", "Totem of Undying", 38, 26, ARCANE, ORGANIC, PRESTIGE),
        entry(Items.ENCHANTED_GOLDEN_APPLE, "enchanted_golden_apple", "Enchanted Golden Apple", 40, 28, ARCANE, RADIANT, PRESTIGE),
        entry(Items.GOLDEN_APPLE, "golden_apple", "Golden Apple", 20, 13, ARCANE, RADIANT, ORGANIC),
        entry(Items.EXPERIENCE_BOTTLE, "experience_bottle", "Bottle o' Enchanting", 18, 12, ARCANE, ENERGETIC, RADIANT),

        entry(Items.DRAGON_BREATH, "dragon_breath", "Dragon's Breath", 44, 30, END, ARCANE, ENERGETIC, PRESTIGE),
        entry(Items.END_CRYSTAL, "end_crystal", "End Crystal", 48, 32, END, CRYSTAL, ENERGETIC, PRESTIGE),
        entry(Items.DRAGON_EGG, "dragon_egg", "Dragon Egg", 80, 50, END, ARCANE, PRESTIGE),
        entry(Items.ELYTRA, "elytra", "Elytra", 45, 30, LIGHT, FLEXIBLE, END, PRESTIGE),
        entry(Items.NETHER_STAR, "nether_star", "Nether Star", 60, 35, NETHER, ARCANE, ENERGETIC, PRESTIGE)
    );

    private static final Map<Item, IngredientData> AUTO_CACHE = new ConcurrentHashMap<>();

    private IngredientRegistry() {
    }

    public static Optional<IngredientData> lookup(Item item) {
        if (item == Items.AIR) {
            return Optional.empty();
        }
        IngredientData mapped = com.bluup.hexwright.common.aspects.AspectMappings.lookupData(item);
        if (mapped != null) {
            return Optional.of(mapped);
        }
        IngredientData override = INGREDIENT_OVERRIDES.get(item);
        if (override != null) {
            return Optional.of(override);
        }
        IngredientData derived = com.bluup.hexwright.common.aspects.AspectMappings.derivedData(item);
        if (derived != null) {
            return Optional.of(derived);
        }
        return Optional.of(AUTO_CACHE.computeIfAbsent(item, IngredientRegistry::autoGenerate));
    }

    public static boolean hasBuiltinOverride(Item item) {
        return INGREDIENT_OVERRIDES.containsKey(item);
    }

    private static Map.Entry<Item, IngredientData> entry(Item item, String id, String name, double baseValue, double attunementCost, IngredientCategory... categories) {
        Set<IngredientCategory> categorySet = categories.length == 0 ? Set.of() : EnumSet.copyOf(Set.of(categories));
        return Map.entry(item, new IngredientData(id, Component.literal(name), baseValue, categorySet, attunementCost));
    }

    private static IngredientData autoGenerate(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();

        Set<IngredientCategory> categories = EnumSet.noneOf(IngredientCategory.class);
        double power = 1;
        double attunement = 1;

        if (path.contains("log") || path.contains("wood") || path.contains("planks") || path.contains("stem")) {
            power = 2;
            attunement = 1;
            categories.add(ORGANIC);
            categories.add(FLEXIBLE);
        }

        if (path.contains("leaves") || path.contains("sapling") || path.contains("flower") || path.contains("roots")
                || path.contains("fungus") || path.contains("vine") || path.contains("mushroom") || path.contains("cocoa")
                || path.contains("sugar") || path.contains("wheat") || path.contains("seed") || path.contains("cactus")
                || path.contains("bamboo") || path.contains("dye") || path.contains("pumpkin") || path.contains("melon")
                || path.contains("moss") || path.contains("fern") || path.contains("lily_pad")) {
            power = 1;
            attunement = 1;
            categories.add(ORGANIC);
            categories.add(LIGHT);
        }

        if (path.contains("wool") || path.contains("carpet") || path.contains("bed") || path.contains("banner")) {
            power = 2;
            attunement = 1;
            categories.add(THREAD);
            categories.add(FLEXIBLE);
            categories.add(LIGHT);
        }

        if (path.contains("door") || path.contains("trapdoor") || path.contains("fence") || path.contains("gate")
                || path.contains("sign") || path.contains("boat") || path.contains("button") || path.contains("pressure_plate")
                || path.contains("ladder") || path.contains("bookshelf") || path.contains("scaffolding")) {
            power = Math.max(power, 2);
            attunement = Math.max(attunement, 1);
            categories.add(ORGANIC);
            categories.add(FLEXIBLE);
        }

        if (path.contains("skull") || path.contains("_head")) {
            power = Math.max(power, 10);
            attunement = Math.max(attunement, 6);
            categories.add(ARCANE);
            categories.add(PRESTIGE);
        }

        if (path.contains("stone") || path.contains("cobble") || path.contains("deepslate") || path.contains("tuff")
                || path.contains("granite") || path.contains("diorite") || path.contains("andesite")) {
            power = 1;
            attunement = 1;
        }

        if (path.contains("sand") || path.contains("gravel") || path.contains("clay") || path.contains("mud")
                || path.contains("dirt")) {
            power = 1;
            attunement = 1;
            categories.add(ORGANIC);
        }

        if (path.contains("glass") || path.contains("ice")) {
            power = 2;
            attunement = 1;
            categories.add(CRYSTAL);
            categories.add(LIGHT);
        }

        if (path.contains("copper")) {
            power = 5;
            attunement = 3;
            categories.add(METALLIC);
            categories.add(ENERGETIC);
        }

        if (path.contains("iron")) {
            power = 8;
            attunement = 5;
            categories.add(METALLIC);
        }

        if (path.contains("gold")) {
            power = 10;
            attunement = 6;
            categories.add(METALLIC);
            categories.add(RADIANT);
        }

        if (path.contains("diamond")) {
            power = 22;
            attunement = 14;
            categories.add(CRYSTAL);
            categories.add(RADIANT);
            categories.add(PRESTIGE);
        }

        if (path.contains("emerald")) {
            power = 18;
            attunement = 11;
            categories.add(CRYSTAL);
            categories.add(RADIANT);
            categories.add(PRESTIGE);
        }

        if (path.contains("netherite")) {
            power = 42;
            attunement = 28;
            categories.add(METALLIC);
            categories.add(NETHER);
            categories.add(PRESTIGE);
        }

        if (path.contains("redstone") || path.contains("repeater") || path.contains("comparator")
                || path.contains("observer") || path.contains("piston") || path.contains("dispenser")
                || path.contains("dropper") || path.contains("hopper") || path.contains("lever")
                || path.contains("tripwire") || path.contains("daylight_detector") || path.contains("target")
                || path.contains("lightning_rod") || path.contains("rail")) {
            power = Math.max(power, 6);
            attunement = Math.max(attunement, 4);
            categories.add(ENERGETIC);
        }

        if (path.contains("nether") || path.contains("crimson") || path.contains("warped") || path.contains("basalt")
                || path.contains("blackstone") || path.contains("soul") || path.contains("magma")) {
            power = Math.max(power, 6);
            attunement = Math.max(attunement, 4);
            categories.add(NETHER);
        }

        if (path.contains("end_") || path.contains("ender") || path.contains("purpur") || path.contains("chorus")
                || path.contains("shulker")) {
            power = Math.max(power, 12);
            attunement = Math.max(attunement, 8);
            categories.add(END);
            categories.add(SPATIAL);
        }

        if (path.contains("prismarine") || path.contains("kelp") || path.contains("coral") || path.contains("sea")
                || path.contains("nautilus") || path.contains("guster") || path.contains("trident")) {
            power = Math.max(power, 6);
            attunement = Math.max(attunement, 4);
            categories.add(SPATIAL);
        }

        if (path.contains("sculk") || path.contains("echo")) {
            power = Math.max(power, 16);
            attunement = Math.max(attunement, 10);
            categories.add(ECHO);
            categories.add(ARCANE);
        }

        if (path.contains("blaze") || path.contains("fire") || path.contains("lava")
                || path.contains("tnt") || path.contains("gunpowder") || path.contains("campfire")) {
            power = Math.max(power, 10);
            attunement = Math.max(attunement, 7);
            categories.add(FIRE);
            categories.add(ENERGETIC);
        }

        FoodProperties food = item.getFoodProperties();
        if (food != null) {
            power = clamp(food.getNutrition() * (1 + food.getSaturationModifier()), 1, 8);
            attunement = clamp(food.getNutrition() * 0.5, 1, 5);
            categories.add(ORGANIC);
        }

        if (item instanceof TieredItem tiered) {
            int uses = tiered.getTier().getUses();
            power = Math.max(power, 3 + uses / 100.0);
            attunement = Math.max(attunement, 2 + uses / 150.0);
            categories.add(METALLIC);
        }

        if (item instanceof ArmorItem armor) {
            power = Math.max(power, 3 + armor.getDefense() * 1.5 + armor.getToughness());
            attunement = Math.max(attunement, 2 + armor.getDefense() + armor.getToughness());
            categories.add(METALLIC);
        }

        double rarityMultiplier = switch (item.getDefaultInstance().getRarity()) {
            case COMMON -> 1.0;
            case UNCOMMON -> 1.3;
            case RARE -> 1.7;
            case EPIC -> 2.2;
        };
        power *= rarityMultiplier;
        attunement *= rarityMultiplier;

        return new IngredientData(path, Component.literal(prettifyName(path)), power, categories, attunement);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String prettifyName(String path) {
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}

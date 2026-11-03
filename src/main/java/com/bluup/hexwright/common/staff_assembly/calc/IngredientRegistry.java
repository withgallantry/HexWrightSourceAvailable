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
        IngredientData derived = com.bluup.hexwright.common.aspects.AspectMappings.derivedData(item);
        if (derived != null) {
            return Optional.of(derived);
        }
        return Optional.of(AUTO_CACHE.computeIfAbsent(item, IngredientRegistry::autoGenerate));
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
                || path.contains("moss") || path.contains("fern") || path.contains("lily_pad") || path.contains("kelp")
                || path.contains("seagrass") || path.contains("sea_pickle") || path.contains("coral")) {
            power = 1;
            attunement = 1;
            categories.add(ORGANIC);
            categories.add(FLEXIBLE);
        }

        if (path.contains("wool") || path.contains("carpet") || path.contains("bed") || path.contains("banner")) {
            power = 2;
            attunement = 1;
            categories.add(THREAD);
            categories.add(FLEXIBLE);
        }

        if (path.contains("chain")) {
            power = Math.max(power, 6);
            attunement = Math.max(attunement, 4);
            categories.add(THREAD);
            categories.add(METALLIC);
        }

        if (path.contains("torch") || path.contains("lantern") || path.contains("lamp") || path.contains("candle")
                || path.contains("froglight") || path.contains("shroomlight") || path.contains("glow")) {
            power = Math.max(power, 4);
            attunement = Math.max(attunement, 2);
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

        if (path.contains("prismarine") || path.contains("nautilus") || path.contains("guster") || path.contains("trident")) {
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
            categories.add(LEATHER);
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

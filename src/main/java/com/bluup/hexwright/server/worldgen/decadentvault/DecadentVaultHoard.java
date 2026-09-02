package com.bluup.hexwright.server.worldgen.decadentvault;

import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.loot.AddHexToAncientCypherFunc;
import at.petrak.hexcasting.common.loot.AddPerWorldPatternToScrollFunc;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.crucible.EssencePouchData;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.item.StoneTabletItem;
import com.bluup.hexwright.server.progression.RecipeTablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DecadentVaultHoard {

    private static final int TABLETS = 3;

    private static final int POUCHES = 5;

    private static final int FOCI = 6;

    private static final int GREAT_SPELLS = 2;

    private static final int CYPHERS = 2;

    private static final int PHIALS = 2;

    private static final int PHIAL_CRYSTALS_MIN = 3;
    private static final int PHIAL_CRYSTALS_SPREAD = 3;

    private static final int ESSENCE_MIN = 80;
    private static final int ESSENCE_SPREAD = 140;

    private static final int ASPECTS_MIN = 4;
    private static final int ASPECTS_SPREAD = 3;

    private static final List<Item> SEEDS = List.of(
        Items.WHEAT_SEEDS,
        Items.BEETROOT_SEEDS,
        Items.MELON_SEEDS,
        Items.PUMPKIN_SEEDS,
        Items.CARROT,
        Items.POTATO);

    private static final int SEED_KINDS_MIN = 3;
    private static final int SEED_KINDS_SPREAD = 3;

    private DecadentVaultHoard() {
    }

    static void stock(ServerLevel level, List<BlockPos> chests, List<BlockPos> barrels,
                      RandomSource random) {
        List<ItemStack> guaranteed = guaranteed(level, random);
        Collections.shuffle(guaranteed, new java.util.Random(random.nextLong()));
        List<BlockPos> order = new ArrayList<>(chests);
        Collections.shuffle(order, new java.util.Random(random.nextLong()));

        for (int i = 0; i < order.size(); i++) {
            if (!(level.getBlockEntity(order.get(i)) instanceof Container container)) {
                continue;
            }
            List<ItemStack> contents = new ArrayList<>();
            for (int j = i; j < guaranteed.size(); j += order.size()) {
                contents.add(guaranteed.get(j));
            }
            int supplyCount = 1 + random.nextInt(2);
            for (int j = 0; j < supplyCount; j++) {
                contents.add(supplies(random));
            }
            int foodCount = 1 + random.nextInt(2);
            for (int j = 0; j < foodCount; j++) {
                contents.add(food(random));
            }
            scatter(container, contents, random);
        }

        stockBarrels(level, barrels, random);
    }

    private static void stockBarrels(ServerLevel level, List<BlockPos> barrels,
                                     RandomSource random) {
        for (BlockPos pos : barrels) {
            if (!(level.getBlockEntity(pos) instanceof Container barrel)) {
                continue;
            }
            List<Item> seeds = new ArrayList<>(SEEDS);
            Collections.shuffle(seeds, new java.util.Random(random.nextLong()));
            List<ItemStack> contents = new ArrayList<>();
            int kinds = Math.min(seeds.size(), SEED_KINDS_MIN + random.nextInt(SEED_KINDS_SPREAD));
            for (int i = 0; i < kinds; i++) {
                contents.add(new ItemStack(seeds.get(i), 8 + random.nextInt(17)));
            }
            contents.add(new ItemStack(Items.BUCKET, 1 + random.nextInt(3)));
            scatter(barrel, contents, random);
        }
    }

    private static List<ItemStack> guaranteed(ServerLevel level, RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String recipe : recipes(random)) {
            stacks.add(StoneTabletItem.of(recipe));
        }
        for (int i = 0; i < POUCHES; i++) {
            stacks.add(pouch(random));
        }
        for (int i = 0; i < FOCI; i++) {
            stacks.add(new ItemStack(HexItems.FOCUS));
        }
        for (int i = 0; i < GREAT_SPELLS; i++) {
            stacks.add(greatSpellScroll(level, random));
        }
        for (int i = 0; i < CYPHERS; i++) {
            stacks.add(AddHexToAncientCypherFunc.doStatic(
                new ItemStack(HexItems.ANCIENT_CYPHER), random));
        }
        for (int i = 0; i < PHIALS; i++) {
            stacks.add(phial(random));
        }
        stacks.add(new ItemStack(HexItems.SCRYING_LENS));
        stacks.add(new ItemStack(HexItems.ABACUS));
        stacks.add(new ItemStack(HexItems.JEWELER_HAMMER));
        return stacks;
    }

    private static ItemStack greatSpellScroll(ServerLevel level, RandomSource random) {
        return AddPerWorldPatternToScrollFunc.doStatic(
            new ItemStack(HexItems.SCROLL_LARGE), random, level.getServer().overworld());
    }

    private static ItemStack phial(RandomSource random) {
        long media = MediaConstants.CRYSTAL_UNIT
            * (PHIAL_CRYSTALS_MIN + random.nextInt(PHIAL_CRYSTALS_SPREAD));
        return ItemMediaHolder.withMedia(new ItemStack(HexItems.BATTERY), media, media);
    }

    private static List<String> recipes(RandomSource random) {
        List<String> gated = new ArrayList<>(RecipeTablets.get().gatedRecipes());
        Collections.shuffle(gated, new java.util.Random(random.nextLong()));
        return gated.subList(0, Math.min(TABLETS, gated.size()));
    }

    private static ItemStack pouch(RandomSource random) {
        ItemStack stack = new ItemStack(HexwrightItems.ENDLESS_POUCH);
        List<IngredientCategory> aspects = new ArrayList<>(List.of(IngredientCategory.values()));
        Collections.shuffle(aspects, new java.util.Random(random.nextLong()));
        int count = Math.min(aspects.size(), ASPECTS_MIN + random.nextInt(ASPECTS_SPREAD));
        for (int i = 0; i < count; i++) {
            EssencePouchData.add(stack, aspects.get(i), ESSENCE_MIN + random.nextInt(ESSENCE_SPREAD));
        }
        return stack;
    }

    private static ItemStack supplies(RandomSource random) {
        int roll = random.nextInt(24);
        if (roll < 5) {
            return new ItemStack(HexItems.AMETHYST_DUST, 6 + random.nextInt(13));
        }
        if (roll < 7) {
            return new ItemStack(Items.AMETHYST_SHARD, 3 + random.nextInt(6));
        }
        if (roll < 8) {
            return new ItemStack(HexItems.CHARGED_AMETHYST, 1 + random.nextInt(3));
        }
        if (roll < 13) {
            return new ItemStack(HexItems.SLATE, 6 + random.nextInt(11));
        }
        if (roll < 17) {
            return new ItemStack(HexItems.SCROLL_SMOL, 4 + random.nextInt(7));
        }
        if (roll < 18) {
            return new ItemStack(HexItems.SCROLL_MEDIUM, 2 + random.nextInt(3));
        }
        if (roll < 19) {
            return new ItemStack(HexItems.FOCUS, 1 + random.nextInt(2));
        }
        if (roll < 20) {
            return new ItemStack(HexItems.SPELLBOOK);
        }
        return treasure(random);
    }

    private static ItemStack treasure(RandomSource random) {
        return switch (random.nextInt(10)) {
            case 0 -> new ItemStack(Blocks.GOLD_BLOCK, 2 + random.nextInt(5));
            case 1 -> new ItemStack(Items.GOLD_INGOT, 8 + random.nextInt(17));
            case 2 -> new ItemStack(Items.DIAMOND, 2 + random.nextInt(6));
            case 3 -> new ItemStack(Items.AMETHYST_SHARD, 8 + random.nextInt(17));
            case 4 -> new ItemStack(HexItems.CHARGED_AMETHYST, 2 + random.nextInt(4));
            case 5 -> new ItemStack(HexItems.AMETHYST_DUST, 12 + random.nextInt(21));
            case 6 -> new ItemStack(HexwrightItems.CRYSTALITE, 3 + random.nextInt(6));
            case 7 -> new ItemStack(Items.ECHO_SHARD, 2 + random.nextInt(5));
            case 8 -> new ItemStack(Items.EMERALD, 6 + random.nextInt(13));
            default -> new ItemStack(Items.CRAFTING_TABLE);
        };
    }

    private static ItemStack food(RandomSource random) {
        return switch (random.nextInt(6)) {
            case 0 -> new ItemStack(Items.BREAD, 3 + random.nextInt(5));
            case 1 -> new ItemStack(Items.COOKED_BEEF, 3 + random.nextInt(5));
            case 2 -> new ItemStack(Items.COOKED_PORKCHOP, 3 + random.nextInt(5));
            case 3 -> new ItemStack(Items.COOKED_SALMON, 3 + random.nextInt(5));
            case 4 -> new ItemStack(Items.GOLDEN_CARROT, 1 + random.nextInt(3));
            default -> new ItemStack(Items.HONEY_BOTTLE, 1 + random.nextInt(3));
        };
    }

    private static void scatter(Container container, List<ItemStack> contents, RandomSource random) {
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                free.add(slot);
            }
        }
        Collections.shuffle(free, new java.util.Random(random.nextLong()));
        for (int i = 0; i < contents.size() && i < free.size(); i++) {
            container.setItem(free.get(i), contents.get(i));
        }
        container.setChanged();
    }
}

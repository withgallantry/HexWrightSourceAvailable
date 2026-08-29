package com.bluup.hexwright.server.worldgen.decadentvault;

import at.petrak.hexcasting.common.lib.HexItems;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.crucible.EssencePouchData;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.item.StoneTabletItem;
import com.bluup.hexwright.server.progression.RecipeTablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
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

    private static final int ESSENCE_MIN = 80;
    private static final int ESSENCE_SPREAD = 140;

    private static final int ASPECTS_MIN = 4;
    private static final int ASPECTS_SPREAD = 3;

    private DecadentVaultHoard() {
    }

    static void stock(ServerLevel level, List<BlockPos> chests, RandomSource random) {
        List<ItemStack> guaranteed = guaranteed(random);
        Collections.shuffle(guaranteed, new java.util.Random(random.nextLong()));

        for (int i = 0; i < chests.size(); i++) {
            if (!(level.getBlockEntity(chests.get(i)) instanceof Container container)) {
                continue;
            }
            List<ItemStack> contents = new ArrayList<>();
            for (int j = i; j < guaranteed.size(); j += chests.size()) {
                contents.add(guaranteed.get(j));
            }
            int treasureCount = 1 + random.nextInt(2);
            for (int j = 0; j < treasureCount; j++) {
                contents.add(treasure(random));
            }
            int foodCount = 1 + random.nextInt(2);
            for (int j = 0; j < foodCount; j++) {
                contents.add(food(random));
            }
            scatter(container, contents, random);
        }
    }

    private static List<ItemStack> guaranteed(RandomSource random) {
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
        return stacks;
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

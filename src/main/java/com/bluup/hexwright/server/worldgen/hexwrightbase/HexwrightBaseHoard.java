package com.bluup.hexwright.server.worldgen.hexwrightbase;

import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.loot.AddHexToAncientCypherFunc;
import com.bluup.hexwright.server.item.StoneTabletItem;
import com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultHoard;
import com.bluup.hexwright.server.worldgen.decadentvault.MasterworkRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HexwrightBaseHoard {

    private static final int TABLETS_MIN = 1;
    private static final int TABLETS_MAX = 4;
    private static final int ARMOUR_PIECES = 2;
    private static final int PHIALS = 3;
    private static final int FOCI = 4;
    private static final int CYPHERS = 2;

    private static final int SUPPLIES_MIN = 1;
    private static final int SUPPLIES_SPREAD = 2;
    private static final int FOOD_MIN = 3;
    private static final int FOOD_SPREAD = 3;

    private record Stocking(BlockPos pos, List<ItemStack> contents, long slotSeed) {
    }

    private HexwrightBaseHoard() {
    }

    static void stock(WorldGenLevel level, BoundingBox chunkBox, List<BlockPos> chests,
                      List<BlockPos> barrels, RandomSource random) {
        for (Stocking stocking : plan(chests, barrels, random)) {
            if (chunkBox.isInside(stocking.pos())
                && level.getBlockEntity(stocking.pos()) instanceof Container container) {
                DecadentVaultHoard.scatter(container, stocking.contents(), RandomSource.create(stocking.slotSeed()));
            }
        }
    }

    private static List<Stocking> plan(List<BlockPos> chests, List<BlockPos> barrels, RandomSource random) {
        List<ItemStack> finds = finds(random);
        List<BlockPos> order = new ArrayList<>(chests);
        Collections.shuffle(order, new java.util.Random(random.nextLong()));

        List<Stocking> plan = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            List<ItemStack> contents = new ArrayList<>();
            for (int j = i; j < finds.size(); j += order.size()) {
                contents.add(finds.get(j));
            }
            int supplies = SUPPLIES_MIN + random.nextInt(SUPPLIES_SPREAD);
            for (int j = 0; j < supplies; j++) {
                contents.add(DecadentVaultHoard.supplies(random));
            }
            plan.add(new Stocking(order.get(i), contents, random.nextLong()));
        }
        for (BlockPos barrel : barrels) {
            List<ItemStack> contents = new ArrayList<>();
            int food = FOOD_MIN + random.nextInt(FOOD_SPREAD);
            for (int j = 0; j < food; j++) {
                contents.add(DecadentVaultHoard.food(random));
            }
            plan.add(new Stocking(barrel, contents, random.nextLong()));
        }
        return plan;
    }

    private static List<ItemStack> finds(RandomSource random) {
        List<ItemStack> finds = new ArrayList<>();
        int tablets = TABLETS_MIN + random.nextInt(TABLETS_MAX - TABLETS_MIN + 1);
        for (String recipe : DecadentVaultHoard.recipes(random, tablets)) {
            finds.add(StoneTabletItem.of(recipe));
        }
        for (int i = 0; i < ARMOUR_PIECES; i++) {
            finds.add(MasterworkRewards.armourPiece(random));
        }
        finds.add(MasterworkRewards.broom(random));
        for (int i = 0; i < PHIALS; i++) {
            finds.add(DecadentVaultHoard.phial(random));
        }
        for (int i = 0; i < FOCI; i++) {
            finds.add(new ItemStack(HexItems.FOCUS));
        }
        for (int i = 0; i < CYPHERS; i++) {
            finds.add(AddHexToAncientCypherFunc.doStatic(new ItemStack(HexItems.ANCIENT_CYPHER), random));
        }
        finds.add(new ItemStack(HexItems.SCRYING_LENS));
        finds.add(new ItemStack(HexItems.ABACUS));
        finds.add(new ItemStack(HexItems.JEWELER_HAMMER));
        Collections.shuffle(finds, new java.util.Random(random.nextLong()));
        return finds;
    }
}

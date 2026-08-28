package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ArmourCraftLayouts {

    public static final char GEM = 'G';
    public static final char CRYSTALITE = 'C';
    public static final char LEATHER = 'L';
    public static final char EMPTY = ' ';

    public record Layout(ArmourPiece piece, List<String> rows) {

        public int width() {
            return rows.get(0).length();
        }

        public int height() {
            return rows.size();
        }

        public char at(int row, int col) {
            return rows.get(row).charAt(col);
        }
    }

    public static final List<Layout> ALL = List.of(
        new Layout(ArmourPiece.HELMET, List.of("CGC", "L L")),
        new Layout(ArmourPiece.CHESTPLATE, List.of("C C", "CGC", "LLL")),
        new Layout(ArmourPiece.LEGGINGS, List.of("CGC", "C C", "L L")),
        new Layout(ArmourPiece.BOOTS, List.of("C G", "L L"))
    );

    private ArmourCraftLayouts() {
    }

    public static ItemStack displayStack(char symbol, ItemStack gem) {
        return switch (symbol) {
            case GEM -> gem.copy();
            case CRYSTALITE -> new ItemStack(HexwrightItems.CRYSTALITE);
            case LEATHER -> new ItemStack(Items.LEATHER);
            default -> ItemStack.EMPTY;
        };
    }
}

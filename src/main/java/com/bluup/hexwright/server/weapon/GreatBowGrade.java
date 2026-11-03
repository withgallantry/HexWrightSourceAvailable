package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class GreatBowGrade {

    private GreatBowGrade() {
    }

    public static double arrowBaseDamage(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 2.0;
            case SOUND -> 2.5;
            case FINE -> 3.0;
            case EXQUISITE -> 3.5;
            case MASTERWORK -> 4.0;
        };
    }

    public static int hexCooldownTicks(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 400;
            case SOUND -> 300;
            case FINE -> 200;
            case EXQUISITE -> 120;
            case MASTERWORK -> 60;
        };
    }

    public static int durability(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 250;
            case SOUND -> 384;
            case FINE -> 550;
            case EXQUISITE -> 800;
            case MASTERWORK -> 1200;
        };
    }

    public static Rarity rarity(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE, SOUND -> Rarity.UNCOMMON;
            case FINE, EXQUISITE -> Rarity.RARE;
            case MASTERWORK -> Rarity.EPIC;
        };
    }

    public static String itemId(PocketCasterData.Quality quality) {
        return quality.name().toLowerCase(java.util.Locale.ROOT) + "_archer_great_bow";
    }

    public static Item.Properties properties(PocketCasterData.Quality quality) {
        return new Item.Properties()
            .durability(durability(quality))
            .rarity(rarity(quality));
    }
}

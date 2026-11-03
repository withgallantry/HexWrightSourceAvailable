package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import java.util.Locale;

public final class BattleHammerGrade {

    private BattleHammerGrade() {
    }

    public static Tier tier(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE, SOUND -> Tiers.IRON;
            case FINE -> Tiers.GOLD;
            case EXQUISITE -> Tiers.DIAMOND;
            case MASTERWORK -> Tiers.NETHERITE;
        };
    }

    public static String material(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE, SOUND -> "iron";
            case FINE -> "golden";
            case EXQUISITE -> "diamond";
            case MASTERWORK -> "netherite";
        };
    }

    public static int attackDamageModifier(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 5;
            case SOUND -> 6;
            case FINE -> 9;
            case EXQUISITE -> 6;
            case MASTERWORK -> 6;
        };
    }

    public static float attackSpeedModifier(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> -3.2F;
            case SOUND -> -3.2F;
            case FINE -> -3.15F;
            case EXQUISITE -> -3.1F;
            case MASTERWORK -> -3.1F;
        };
    }

    public static float slamRadius(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 3.0F;
            case SOUND -> 3.25F;
            case FINE -> 3.5F;
            case EXQUISITE -> 3.75F;
            case MASTERWORK -> GroundSlam.NETHERITE_RADIUS;
        };
    }

    public static int slamCooldownTicks(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 100;
            case SOUND -> 90;
            case FINE -> 80;
            case EXQUISITE -> 70;
            case MASTERWORK -> GroundSlam.NETHERITE_COOLDOWN_TICKS;
        };
    }

    public static int durability(PocketCasterData.Quality quality) {
        return BattleAxeGrade.durability(quality);
    }

    public static Rarity rarity(PocketCasterData.Quality quality) {
        return BattleAxeGrade.rarity(quality);
    }

    public static String itemId(PocketCasterData.Quality quality) {
        return quality.name().toLowerCase(Locale.ROOT) + "_battle_hammer";
    }

    public static Item.Properties properties(PocketCasterData.Quality quality) {
        Item.Properties properties = new Item.Properties()
            .durability(durability(quality))
            .rarity(rarity(quality));
        return quality == PocketCasterData.Quality.MASTERWORK ? properties.fireResistant() : properties;
    }
}

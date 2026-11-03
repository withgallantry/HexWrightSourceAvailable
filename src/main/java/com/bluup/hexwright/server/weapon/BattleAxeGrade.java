package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import java.util.Locale;

public final class BattleAxeGrade {

    private BattleAxeGrade() {
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
            case CRUDE -> 4;
            case SOUND -> 5;
            case FINE -> 8;
            case EXQUISITE -> 5;
            case MASTERWORK -> 5;
        };
    }

    public static float attackSpeedModifier(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> -3.0F;
            case SOUND -> -3.0F;
            case FINE -> -2.95F;
            case EXQUISITE -> -2.9F;
            case MASTERWORK -> -2.9F;
        };
    }

    public static float cleaveReach(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 2.5F;
            case SOUND -> 2.75F;
            case FINE -> 3.0F;
            case EXQUISITE -> 3.25F;
            case MASTERWORK -> Cleave.NETHERITE_REACH;
        };
    }

    public static int cleaveCooldownTicks(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 90;
            case SOUND -> 80;
            case FINE -> 70;
            case EXQUISITE -> 60;
            case MASTERWORK -> Cleave.NETHERITE_COOLDOWN_TICKS;
        };
    }

    public static int durability(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 400;
            case SOUND -> 600;
            case FINE -> 900;
            case EXQUISITE -> 1400;
            case MASTERWORK -> 2031;
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
        return quality.name().toLowerCase(Locale.ROOT) + "_battle_axe";
    }

    public static Item.Properties properties(PocketCasterData.Quality quality) {
        Item.Properties properties = new Item.Properties()
            .durability(durability(quality))
            .rarity(rarity(quality));
        return quality == PocketCasterData.Quality.MASTERWORK ? properties.fireResistant() : properties;
    }
}

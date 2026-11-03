package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import java.util.Locale;

public final class ShortSwordGrade {

    private static final float BASE_ATTACK_SPEED = 4.0F;

    private ShortSwordGrade() {
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
            case CRUDE -> 1;
            case SOUND -> 2;
            case FINE -> 5;
            case EXQUISITE -> 2;
            case MASTERWORK -> 2;
        };
    }

    public static float attackSpeedModifier(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> -2.30F;
            case SOUND -> -2.25F;
            case FINE -> -2.20F;
            case EXQUISITE -> -2.10F;
            case MASTERWORK -> -2.00F;
        };
    }

    public static int attackIntervalTicks(PocketCasterData.Quality quality) {
        return Mth.ceil(20.0F / (BASE_ATTACK_SPEED + attackSpeedModifier(quality)));
    }

    public static int durability(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 300;
            case SOUND -> 450;
            case FINE -> 700;
            case EXQUISITE -> 1100;
            case MASTERWORK -> 1800;
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
        return quality.name().toLowerCase(Locale.ROOT) + "_duelist_short_sword";
    }

    public static Item.Properties properties(PocketCasterData.Quality quality) {
        Item.Properties properties = new Item.Properties()
            .durability(durability(quality))
            .rarity(rarity(quality));
        return quality == PocketCasterData.Quality.MASTERWORK ? properties.fireResistant() : properties;
    }
}

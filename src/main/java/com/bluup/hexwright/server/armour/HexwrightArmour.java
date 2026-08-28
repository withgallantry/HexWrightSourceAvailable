package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.EnumMap;
import java.util.Map;

public final class HexwrightArmour {

    private static final Map<ArmourSet, Item> GEMS = new EnumMap<>(ArmourSet.class);
    private static final Map<ArmourSet, Map<ArmourTier, Map<ArmourPiece, Item>>> PIECES =
        new EnumMap<>(ArmourSet.class);

    static {
        for (ArmourSet set : ArmourSet.values()) {
            GEMS.put(set, Registry.register(
                BuiltInRegistries.ITEM,
                Hexwright.id(set.gemId()),
                new ArmourGemItem(set, new Item.Properties().rarity(Rarity.UNCOMMON))
            ));

            Map<ArmourTier, Map<ArmourPiece, Item>> byTier = new EnumMap<>(ArmourTier.class);
            for (ArmourTier tier : ArmourTier.values()) {
                Map<ArmourPiece, Item> byPiece = new EnumMap<>(ArmourPiece.class);
                for (ArmourPiece piece : ArmourPiece.values()) {
                    Item.Properties properties = new Item.Properties().rarity(rarityOf(tier));
                    if (tier.fireResistant()) {
                        properties = properties.fireResistant();
                    }
                    byPiece.put(piece, Registry.register(
                        BuiltInRegistries.ITEM,
                        Hexwright.id(set.pieceId(tier, piece)),
                        new HexwrightArmourItem(set, tier, piece, properties)
                    ));
                }
                byTier.put(tier, byPiece);
            }
            PIECES.put(set, byTier);
        }
    }

    private HexwrightArmour() {
    }

    private static Rarity rarityOf(ArmourTier tier) {
        return switch (tier) {
            case IRON -> Rarity.COMMON;
            case GOLDEN -> Rarity.UNCOMMON;
            case DIAMOND -> Rarity.RARE;
            case NETHERITE -> Rarity.EPIC;
        };
    }

    public static Item gem(ArmourSet set) {
        return GEMS.get(set);
    }

    public static Item piece(ArmourSet set, ArmourTier tier, ArmourPiece piece) {
        return PIECES.get(set).get(tier).get(piece);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            for (ArmourSet set : ArmourSet.values()) {
                entries.accept(ArmourGemData.create(gem(set), PocketCasterData.Quality.FINE));
                for (ArmourTier tier : ArmourTier.values()) {
                    for (ArmourPiece piece : ArmourPiece.values()) {
                        entries.accept(piece(set, tier, piece));
                    }
                }
            }
        });
    }
}

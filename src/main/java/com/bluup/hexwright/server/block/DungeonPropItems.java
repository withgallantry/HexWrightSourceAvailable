package com.bluup.hexwright.server.block;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class DungeonPropItems {

    public static final ResourceKey<CreativeModeTab> TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hexwright.id("furniture"));

    private static final Map<DungeonProp, BlockItem> ITEMS = new EnumMap<>(DungeonProp.class);

    public static BlockItem item(DungeonProp prop) {
        return ITEMS.get(prop);
    }

    public static Collection<BlockItem> items() {
        return Collections.unmodifiableCollection(ITEMS.values());
    }

    public static boolean isProp(Item item) {
        return item instanceof BlockItem block && block.getBlock() instanceof DungeonPropBlock;
    }

    public static boolean isFurniture(Item item) {
        return isProp(item) || item == HexwrightBlocks.WEAPON_RACK_ITEM;
    }

    public static void register() {
        for (DungeonProp prop : DungeonProp.values()) {
            ITEMS.put(prop, Registry.register(BuiltInRegistries.ITEM,
                Hexwright.id("prop_" + prop.propName()),
                new BlockItem(prop.block(), new Item.Properties())));
        }
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.hexwright.furniture"))
            .icon(() -> new ItemStack(ITEMS.get(DungeonProp.CANDELABRUM)))
            .displayItems((parameters, output) -> {
                output.accept(HexwrightBlocks.WEAPON_RACK_ITEM);
                ITEMS.values().forEach(output::accept);
            })
            .build());
    }

    private DungeonPropItems() {
    }
}

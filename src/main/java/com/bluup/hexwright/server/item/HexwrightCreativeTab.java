package com.bluup.hexwright.server.item;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.DungeonPropItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class HexwrightCreativeTab {

    private static final Map<ResourceKey<CreativeModeTab>, List<Consumer<CreativeModeTab.Output>>> SECTIONS =
        new LinkedHashMap<>();

    static {
        SECTIONS.put(CreativeModeTabs.TOOLS_AND_UTILITIES, new ArrayList<>());
        SECTIONS.put(CreativeModeTabs.COMBAT, new ArrayList<>());
        SECTIONS.put(CreativeModeTabs.FUNCTIONAL_BLOCKS, new ArrayList<>());
        SECTIONS.put(CreativeModeTabs.BUILDING_BLOCKS, new ArrayList<>());
        SECTIONS.put(CreativeModeTabs.NATURAL_BLOCKS, new ArrayList<>());
        SECTIONS.put(CreativeModeTabs.SPAWN_EGGS, new ArrayList<>());
    }

    public static final ResourceKey<CreativeModeTab> KEY =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hexwright.id("main"));

    public static void addTo(ResourceKey<CreativeModeTab> vanillaTab, Consumer<CreativeModeTab.Output> filler) {
        ItemGroupEvents.modifyEntriesEvent(vanillaTab).register(filler::accept);
        SECTIONS.computeIfAbsent(vanillaTab, key -> new ArrayList<>()).add(filler);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.hexwright.main"))
            .icon(() -> new ItemStack(HexwrightItems.CONFIGURABLE_STAFF))
            .displayItems((parameters, output) -> {
                Set<ItemStack> stacks = ItemStackLinkedSet.createTypeAndTagSet();
                CreativeModeTab.Output collector = (stack, visibility) -> stacks.add(stack);
                for (List<Consumer<CreativeModeTab.Output>> fillers : SECTIONS.values()) {
                    for (Consumer<CreativeModeTab.Output> filler : fillers) {
                        filler.accept(collector);
                    }
                }

                Set<Item> listed = new HashSet<>();
                for (ItemStack stack : stacks) {
                    listed.add(stack.getItem());
                }
                for (Item item : BuiltInRegistries.ITEM) {
                    if (!listed.contains(item)
                        && !DungeonPropItems.isFurniture(item)
                        && Hexwright.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                        stacks.add(new ItemStack(item));
                    }
                }

                output.acceptAll(stacks);
            })
            .build());
    }

    private HexwrightCreativeTab() {
    }
}

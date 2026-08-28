package com.bluup.hexwright.common.aspects;

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientData;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AspectMappings {

    public record AspectProfile(IngredientData data, int burnTicks, double essenceYield) {
    }

    public static final int MIN_BURN_TICKS = 40;
    public static final int MAX_BURN_TICKS = 400;

    private static volatile Map<Item, AspectProfile> active = Map.of();

    private static volatile Map<Item, AspectProfile> derived = Map.of();

    private AspectMappings() {
    }

    public static void setActive(Map<Item, AspectProfile> mappings) {
        active = Map.copyOf(mappings);
    }

    public static void setDerived(Map<Item, AspectProfile> mappings) {
        derived = Map.copyOf(mappings);
    }

    public static IngredientData lookupData(Item item) {
        AspectProfile profile = active.get(item);
        return profile == null ? null : profile.data();
    }

    public static IngredientData derivedData(Item item) {
        AspectProfile profile = derived.get(item);
        return profile == null ? null : profile.data();
    }

    public static Optional<AspectProfile> profileFor(Item item) {
        if (item == Items.AIR) {
            return Optional.empty();
        }
        AspectProfile explicit = active.get(item);
        if (explicit != null) {
            return Optional.of(explicit);
        }
        AspectProfile fromRecipes = derived.get(item);
        if (fromRecipes != null) {
            return Optional.of(fromRecipes);
        }
        return IngredientRegistry.lookup(item)
            .map(data -> new AspectProfile(data, defaultBurnTicks(data.baseValue()), data.baseValue()));
    }

    public static int defaultBurnTicks(double baseValue) {
        int ticks = (int) Math.round(40 + baseValue * 6);
        return Math.max(MIN_BURN_TICKS, Math.min(MAX_BURN_TICKS, ticks));
    }


    public static void write(FriendlyByteBuf buf) {
        writeLayer(buf, active);
    }

    public static Map<Item, AspectProfile> read(FriendlyByteBuf buf) {
        return readLayer(buf);
    }

    private static void writeLayer(FriendlyByteBuf buf, Map<Item, AspectProfile> snapshot) {
        buf.writeVarInt(snapshot.size());
        for (Map.Entry<Item, AspectProfile> entry : snapshot.entrySet()) {
            AspectProfile profile = entry.getValue();
            IngredientData data = profile.data();
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(entry.getKey()));
            buf.writeUtf(data.id());
            buf.writeUtf(data.displayName().getString());
            buf.writeDouble(data.baseValue());
            buf.writeDouble(data.attunementCost());
            buf.writeVarInt(data.categories().size());
            for (IngredientCategory category : data.categories()) {
                buf.writeUtf(category.name());
            }
            buf.writeVarInt(profile.burnTicks());
            buf.writeDouble(profile.essenceYield());
        }
    }

    private static Map<Item, AspectProfile> readLayer(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Item, AspectProfile> result = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            String id = buf.readUtf();
            String name = buf.readUtf();
            double value = buf.readDouble();
            double attunement = buf.readDouble();
            int categoryCount = buf.readVarInt();
            Set<IngredientCategory> categories = EnumSet.noneOf(IngredientCategory.class);
            for (int c = 0; c < categoryCount; c++) {
                categories.add(IngredientCategory.valueOf(buf.readUtf()));
            }
            int burnTicks = buf.readVarInt();
            double essence = buf.readDouble();

            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == Items.AIR) {
                continue;
            }
            result.put(item, new AspectProfile(
                new IngredientData(id, Component.literal(name), value, categories, attunement),
                burnTicks,
                essence
            ));
        }
        return result;
    }
}

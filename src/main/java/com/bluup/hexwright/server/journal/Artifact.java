package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record Artifact(
    String id,
    ResourceLocation item,
    @Nullable CompoundTag nbt,
    String title,
    List<String> foundIn
) {

    public ItemStack displayStack() {
        Item resolved = BuiltInRegistries.ITEM.getOptional(item).orElse(Items.AIR);
        if (resolved == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(resolved);
        if (nbt != null) {
            stack.setTag(nbt.copy());
        }
        return stack;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
            return false;
        }
        if (nbt != null && !NbtUtils.compareNbt(nbt, stack.getTag(), true)) {
            return false;
        }
        return !(stack.getItem() instanceof ArtifactItem) || ArtifactItem.is(stack);
    }

    public Component titleComponent() {
        if (!title.isEmpty()) {
            return Component.translatable(title);
        }
        ItemStack stack = displayStack();
        return stack.isEmpty() ? Component.literal(item.toString()) : stack.getHoverName();
    }

    public List<Component> locationComponents() {
        List<Component> out = new ArrayList<>(foundIn.size());
        for (String location : foundIn) {
            out.add(Component.translatable(location));
        }
        return out;
    }

    static Artifact parse(JsonObject json) {
        String id = GsonHelper.getAsString(json, "id");
        ResourceLocation item = new ResourceLocation(GsonHelper.getAsString(json, "item"));

        CompoundTag nbt = null;
        String nbtText = GsonHelper.getAsString(json, "nbt", "");
        if (!nbtText.isBlank()) {
            try {
                nbt = TagParser.parseTag(nbtText);
            } catch (Exception e) {
                throw new IllegalArgumentException("artifact '" + id + "' has unparseable nbt: " + nbtText, e);
            }
        }

        List<String> foundIn = new ArrayList<>();
        JsonArray locations = GsonHelper.getAsJsonArray(json, "found_in", new JsonArray());
        for (int i = 0; i < locations.size(); i++) {
            foundIn.add(GsonHelper.convertToString(locations.get(i), "found_in[" + i + "]"));
        }

        return new Artifact(id, item, nbt, GsonHelper.getAsString(json, "title", ""), List.copyOf(foundIn));
    }
}

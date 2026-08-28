package com.bluup.hexwright.server.accessory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WornAccessories {

    public interface Provider {
        List<ItemStack> getWorn(LivingEntity entity);
    }

    public interface SlotHandler {
        List<ItemStack> contents(LivingEntity entity, String slot);

        void setBonusSlots(LivingEntity entity, String slot, UUID id, int bonus);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();
    private static final List<SlotHandler> SLOT_HANDLERS = new ArrayList<>();

    private WornAccessories() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    public static void register(SlotHandler handler) {
        SLOT_HANDLERS.add(handler);
    }

    public static List<ItemStack> slotContents(LivingEntity entity, String slot) {
        if (SLOT_HANDLERS.isEmpty()) {
            return List.of();
        }
        List<ItemStack> contents = new ArrayList<>();
        for (SlotHandler handler : SLOT_HANDLERS) {
            contents.addAll(handler.contents(entity, slot));
        }
        return contents;
    }

    public static void setBonusSlots(LivingEntity entity, String slot, UUID id, int bonus) {
        for (SlotHandler handler : SLOT_HANDLERS) {
            handler.setBonusSlots(entity, slot, id, bonus);
        }
    }

    public static boolean hasProvider() {
        return !PROVIDERS.isEmpty();
    }

    public static List<ItemStack> allWorn(LivingEntity entity) {
        if (PROVIDERS.isEmpty()) {
            return List.of();
        }
        List<ItemStack> worn = new ArrayList<>();
        for (Provider provider : PROVIDERS) {
            worn.addAll(provider.getWorn(entity));
        }
        return worn;
    }

    public static boolean isWearing(LivingEntity entity, Item item) {
        for (ItemStack stack : allWorn(entity)) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }
}

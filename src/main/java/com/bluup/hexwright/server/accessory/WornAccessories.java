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

    private static Provider provider;
    private static SlotHandler slotHandler;

    private WornAccessories() {
    }

    public static void register(Provider bridge) {
        provider = bridge;
    }

    public static void register(SlotHandler bridge) {
        slotHandler = bridge;
    }

    public static List<ItemStack> slotContents(LivingEntity entity, String slot) {
        return slotHandler == null ? List.of() : slotHandler.contents(entity, slot);
    }

    public static void setBonusSlots(LivingEntity entity, String slot, UUID id, int bonus) {
        if (slotHandler != null) {
            slotHandler.setBonusSlots(entity, slot, id, bonus);
        }
    }

    public static boolean hasProvider() {
        return provider != null;
    }

    public static List<ItemStack> allWorn(LivingEntity entity) {
        return provider == null ? List.of() : new ArrayList<>(provider.getWorn(entity));
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

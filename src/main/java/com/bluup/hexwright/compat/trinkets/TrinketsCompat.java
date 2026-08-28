package com.bluup.hexwright.compat.trinkets;

import com.bluup.hexwright.server.accessory.WornAccessories;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TrinketsCompat {

    private static final Map<String, String> SLOT_GROUPS = Map.of(
        "seal", "chest",
        "talisman", "chest",
        "world_crystal", "chest",
        "back", "chest",
        "necklace", "chest",
        "ring", "hand",
        "face", "head"
    );

    private TrinketsCompat() {
    }

    public static void register() {
        WornAccessories.register(TrinketsCompat::wornOn);
        WornAccessories.register(new SlotHandler());
    }

    private static List<ItemStack> wornOn(LivingEntity entity) {
        Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(entity);
        if (component.isEmpty()) {
            return List.of();
        }
        List<ItemStack> worn = new ArrayList<>();
        for (var pair : component.get().getAllEquipped()) {
            worn.add(pair.getB());
        }
        return worn;
    }

    private static final class SlotHandler implements WornAccessories.SlotHandler {

        @Override
        public List<ItemStack> contents(LivingEntity entity, String slot) {
            TrinketInventory inventory = inventoryFor(entity, slot);
            if (inventory == null) {
                return List.of();
            }
            List<ItemStack> contents = new ArrayList<>(inventory.getContainerSize());
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                contents.add(inventory.getItem(i));
            }
            return contents;
        }

        @Override
        public void setBonusSlots(LivingEntity entity, String slot, UUID id, int bonus) {
            TrinketInventory inventory = inventoryFor(entity, slot);
            if (inventory == null) {
                return;
            }
            AttributeModifier existing = inventory.getModifiers().get(id);
            if (existing == null ? bonus == 0 : existing.getAmount() == bonus) {
                return;
            }
            if (existing != null) {
                inventory.removeModifier(id);
            }
            if (bonus > 0) {
                inventory.addPersistentModifier(new AttributeModifier(
                    id, "hexwright:bonus_" + slot, bonus, AttributeModifier.Operation.ADDITION));
            }
            inventory.update();
        }

        private static TrinketInventory inventoryFor(LivingEntity entity, String slot) {
            String group = SLOT_GROUPS.get(slot);
            if (group == null) {
                return null;
            }
            return TrinketsApi.getTrinketComponent(entity)
                .map(component -> component.getInventory().get(group))
                .map(inventories -> inventories.get(slot))
                .orElse(null);
        }
    }
}

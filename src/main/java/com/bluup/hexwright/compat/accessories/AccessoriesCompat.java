package com.bluup.hexwright.compat.accessories;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.accessory.WornAccessories;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AccessoriesCompat {

    private AccessoriesCompat() {
    }

    public static void register() {
        WornAccessories.register(AccessoriesCompat::wornOn);
        WornAccessories.register(new SlotHandler());

        AccessoriesAPI.registerAccessory(HexwrightItems.TALISMAN, new Accessory() {
            @Override
            public boolean canEquipFromUse(ItemStack stack) {
                return false;
            }
        });
    }

    private static List<ItemStack> wornOn(LivingEntity entity) {
        Optional<AccessoriesCapability> capability = AccessoriesCapability.getOptionally(entity);
        if (capability.isEmpty()) {
            return List.of();
        }
        List<ItemStack> worn = new ArrayList<>();
        for (SlotEntryReference entry : capability.get().getAllEquipped()) {
            worn.add(entry.stack());
        }
        return worn;
    }

    private static final class SlotHandler implements WornAccessories.SlotHandler {

        @Override
        public List<ItemStack> contents(LivingEntity entity, String slot) {
            AccessoriesContainer container = containerFor(entity, slot);
            if (container == null) {
                return List.of();
            }
            var accessories = container.getAccessories();
            List<ItemStack> contents = new ArrayList<>(accessories.getContainerSize());
            for (int i = 0; i < accessories.getContainerSize(); i++) {
                contents.add(accessories.getItem(i));
            }
            return contents;
        }

        @Override
        public void setBonusSlots(LivingEntity entity, String slot, UUID id, int bonus) {
            AccessoriesContainer container = containerFor(entity, slot);
            if (container == null) {
                return;
            }
            AttributeModifier existing = container.getModifiers().get(id);
            if (existing == null ? bonus == 0 : existing.getAmount() == bonus) {
                return;
            }
            if (existing != null) {
                container.removeModifier(id);
            }
            if (bonus > 0) {
                container.addPersistentModifier(new AttributeModifier(
                    id, "hexwright:bonus_" + slot, bonus, AttributeModifier.Operation.ADDITION));
            }
            AccessoriesCapability.getOptionally(entity)
                .ifPresent(AccessoriesCapability::updateContainers);
        }

        private static AccessoriesContainer containerFor(LivingEntity entity, String slot) {
            return AccessoriesCapability.getOptionally(entity)
                .map(capability -> capability.getContainers().get(slot))
                .orElse(null);
        }
    }
}

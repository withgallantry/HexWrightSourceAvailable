package com.bluup.hexwright.compat.accessories;

import at.petrak.hexcasting.common.items.ItemLens;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.accessory.WornAccessories;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import com.bluup.hexwright.server.wardingbox.WardersSpectaclesItem;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.events.CanEquipCallback;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.resources.ResourceLocation;
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

        CanEquipCallback.EVENT.register((stack, reference) ->
            PentaboxData.isLinkedStack(stack) ? TriState.FALSE : TriState.DEFAULT);

        AccessoriesAPI.registerAccessory(HexwrightItems.TALISMAN, new Accessory() {
            @Override
            public boolean canEquipFromUse(ItemStack stack) {
                return false;
            }
        });

        AccessoriesAPI.registerAccessory(HexwrightItems.WARDERS_SPECTACLES, new Accessory() {
            @Override
            public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
                if (!WardersSpectaclesItem.hasScryingLens(stack)) {
                    return;
                }
                builder.addExclusive(HexAttributes.SCRY_SIGHT, SCRYING_SIGHT_ID,
                    ItemLens.SCRY_SIGHT.getAmount(), ItemLens.SCRY_SIGHT.getOperation());
                builder.addExclusive(HexAttributes.GRID_ZOOM, SCRYING_ZOOM_ID,
                    ItemLens.GRID_ZOOM.getAmount(), ItemLens.GRID_ZOOM.getOperation());
            }
        });
    }

    private static final ResourceLocation SCRYING_SIGHT_ID = Hexwright.id("spectacles_scry_sight");
    private static final ResourceLocation SCRYING_ZOOM_ID = Hexwright.id("spectacles_grid_zoom");

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

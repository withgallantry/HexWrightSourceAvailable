package com.bluup.hexwright.compat.accessories;

import at.petrak.hexcasting.common.items.ItemLens;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.HexItems;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.EquipmentSlot;
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
                builder.addExclusive(HexAttributes.SCRY_SIGHT, ItemLens.SCRY_SIGHT);
                builder.addExclusive(HexAttributes.GRID_ZOOM, ItemLens.GRID_ZOOM);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % LENS_REASSERT_INTERVAL_TICKS != 0L) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (holdsOrWearsLens(player) || wearsFittedSpectacles(player)) {
                    reassertLensModifier(player, HexAttributes.SCRY_SIGHT, ItemLens.SCRY_SIGHT);
                    reassertLensModifier(player, HexAttributes.GRID_ZOOM, ItemLens.GRID_ZOOM);
                }
            }
        });
    }

    private static final int LENS_REASSERT_INTERVAL_TICKS = 10;

    private static boolean holdsOrWearsLens(LivingEntity entity) {
        return entity.getMainHandItem().is(HexItems.SCRYING_LENS)
            || entity.getOffhandItem().is(HexItems.SCRYING_LENS)
            || entity.getItemBySlot(EquipmentSlot.HEAD).is(HexItems.SCRYING_LENS);
    }

    private static boolean wearsFittedSpectacles(LivingEntity entity) {
        for (ItemStack stack : wornOn(entity)) {
            if (stack.is(HexwrightItems.WARDERS_SPECTACLES) && WardersSpectaclesItem.hasScryingLens(stack)) {
                return true;
            }
        }
        return false;
    }

    private static void reassertLensModifier(LivingEntity entity, Attribute attribute, AttributeModifier modifier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifier)) {
            instance.addTransientModifier(modifier);
        }
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

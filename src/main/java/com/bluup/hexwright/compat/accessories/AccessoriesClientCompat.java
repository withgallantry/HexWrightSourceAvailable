package com.bluup.hexwright.compat.accessories;

import com.bluup.hexwright.client.accessory.InertAccessorySlots;
import com.bluup.hexwright.client.armour.MageAttireRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AccessoriesClientCompat {

    private AccessoriesClientCompat() {
    }

    public static void register() {
        InertAccessorySlots.register(AccessoriesClientCompat::indexIn);

        AccessoryRenderer mageAttire = new AccessoryRenderer() {
            @Override
            public <M extends LivingEntity> void render(ItemStack stack, SlotReference reference, PoseStack pose,
                                                        EntityModel<M> model, MultiBufferSource buffers, int light,
                                                        float limbSwing, float limbSwingAmount, float partialTicks,
                                                        float ageInTicks, float netHeadYaw, float headPitch) {
                if (model instanceof HumanoidModel<?> humanoid) {
                    MageAttireRenderer.render(pose, buffers, stack, reference.entity(), light, humanoid);
                }
            }
        };
        for (Item piece : MageAttireRenderer.pieces()) {
            AccessoriesRendererRegistry.registerRenderer(piece, () -> mageAttire);
        }
    }

    private static int indexIn(Slot slot, String slotName) {
        if (!(slot instanceof AccessoriesBasedSlot accessorySlot)
            || !accessorySlot.accessoriesContainer.getSlotName().equals(slotName)) {
            return -1;
        }
        return accessorySlot.getContainerSlot();
    }
}

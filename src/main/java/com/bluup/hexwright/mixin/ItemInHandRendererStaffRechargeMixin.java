package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.item.ConfigurableStaffItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererStaffRechargeMixin {
    private static final String TAG_ASSEMBLY_ROOT = "hexwright_staff_assembly";
    private static final String TAG_MEDIA_REGEN_PROGRESS = "media_regen_progress";
    private static final String TAG_STORED_MEDIA = "hexcasting:media";

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private boolean hexwright$ignoreRechargeOnlyStaffNbtForSwapAnim(ItemStack previous, ItemStack current) {
        if (ItemStack.matches(previous, current)) {
            return true;
        }
        if (!(previous.getItem() instanceof ConfigurableStaffItem)
            || !previous.is(current.getItem())
            || previous.getCount() != current.getCount()) {
            return false;
        }
        return normalize(previous.getTag()).equals(normalize(current.getTag()));
    }

    private static CompoundTag normalize(CompoundTag tag) {
        CompoundTag out = tag == null ? new CompoundTag() : tag.copy();
        out.remove(TAG_STORED_MEDIA);
        if (out.contains(TAG_ASSEMBLY_ROOT)) {
            out.getCompound(TAG_ASSEMBLY_ROOT).remove(TAG_MEDIA_REGEN_PROGRESS);
        }
        return out;
    }
}

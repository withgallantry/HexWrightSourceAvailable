package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.item.VariantItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface Restylable extends VariantItem {

    @Nullable ArmourTier ownLook();

    Item lookItem(ArmourTier look);

    @Override
    default int numVariants() {
        ArmourTier own = ownLook();
        return own == null ? 1 : own.ordinal() + 1;
    }

    default @Nullable ArmourTier look(ItemStack stack) {
        ArmourTier own = ownLook();
        return own == null ? null : ArmourTier.values()[own.ordinal() - clampVariant(getVariant(stack))];
    }

    default boolean canWear(ArmourTier look) {
        ArmourTier own = ownLook();
        return own != null && look.ordinal() <= own.ordinal();
    }

    default void setLook(ItemStack stack, ArmourTier look) {
        ArmourTier own = ownLook();
        if (own == null || !canWear(look)) {
            return;
        }
        if (look == own) {
            stack.removeTagKey(TAG_VARIANT);
        } else {
            setVariant(stack, own.ordinal() - look.ordinal());
        }
    }

    default void appendLookTooltip(ItemStack stack, List<Component> tooltip) {
        ArmourTier look = look(stack);
        if (look == null || look == ownLook()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.hexwright.look",
                Component.translatable(look.displayQuality().translationKey()).withStyle(look.displayQuality().color()))
            .withStyle(ChatFormatting.GRAY));
    }

    static @Nullable ArmourTier gradedLook(Map<PocketCasterData.Quality, Item> ladder, Item item,
                                           @Nullable PocketCasterData.Quality quality) {
        return quality != null && ladder.get(quality) == item ? ArmourTier.forGrade(quality) : null;
    }
}

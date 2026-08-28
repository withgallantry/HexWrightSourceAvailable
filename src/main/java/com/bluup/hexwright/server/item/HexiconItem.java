package com.bluup.hexwright.server.item;

import at.petrak.hexcasting.api.item.VariantItem;
import com.bluup.hexwright.server.hexicon.HexiconData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class HexiconItem extends Item implements VariantItem {
    public static final String[] GLAMOURS = {
        "abyssal", "blight", "celestial", "deep", "infernal", "wild"
    };

    public HexiconItem(Properties properties) {
        super(properties);
    }

    @Override
    public int numVariants() {
        return GLAMOURS.length + 1;
    }

    public static int glamourOf(ItemStack stack) {
        return stack.getItem() instanceof HexiconItem hexicon ? hexicon.getVariant(stack) : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int chapter = HexiconData.getSelectedBar(stack) + 1;
        int page = HexiconData.getSelectedSlot(stack) + 1;

        boolean isBound = HexiconData.getCachedSelectedBound(stack);
        int writtenCount = HexiconData.getCachedWrittenCount(stack);
        if (level instanceof ServerLevel serverLevel) {
            UUID libraryId = HexiconData.getLibraryId(stack);
            if (libraryId != null) {
                isBound = !HexiconData.isSlotEmpty(serverLevel, libraryId, HexiconData.getSelectedAbsoluteSlot(stack));
                HexiconData.refreshCachedWrittenCount(serverLevel, stack);
                writtenCount = HexiconData.getCachedWrittenCount(stack);
            }
        }

        tooltip.add(Component.translatable("tooltip.hexwright.hexicon.capacity", writtenCount)
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.hexicon.chapter_page", chapter, page)
            .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                isBound ? "tooltip.hexwright.hexicon.selected_bound" : "tooltip.hexwright.hexicon.selected_empty")
            .withStyle(isBound ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("tooltip.hexwright.hexicon.double_tap_hint",
                Component.keybind("key.hexwright.hexicon_overlay"))
            .withStyle(ChatFormatting.DARK_PURPLE));
    }
}

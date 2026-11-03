package com.bluup.hexwright.server.item;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.wardingbox.WardingBoxData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class WardingBoxItem extends BlockItem {

    public WardingBoxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<PocketCasterData.Quality> quality = WardingBoxData.getQuality(stack);
        if (quality.isEmpty()) {
            return super.getName(stack);
        }
        return Component.translatable(
            "block.hexwright.warding_box.named",
            Component.translatable(quality.get().translationKey())
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        com.bluup.hexwright.server.progression.MakersMark.appendTooltip(stack, tooltip);
        PocketCasterData.Quality quality = WardingBoxData.getQuality(stack).orElse(PocketCasterData.Quality.CRUDE);
        long capacityDust = WardingBoxData.capacityFor(quality) / MediaConstants.DUST_UNIT;
        long bonusDust = capacityDust - WardingBoxData.capacityFor(PocketCasterData.Quality.CRUDE) / MediaConstants.DUST_UNIT;
        tooltip.add(Component.translatable("tooltip.hexwright.warding_box.capacity", PocketCasterData.formatWithBonus(capacityDust, bonusDust))
            .withStyle(ChatFormatting.AQUA));
        if (WardingBoxData.getSpellTag(stack) != null) {
            tooltip.add(Component.translatable("tooltip.hexwright.warding_box.spell", WardingBoxData.getSpellSize(stack))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        long media = WardingBoxData.getMedia(stack);
        if (media > 0) {
            tooltip.add(Component.translatable("tooltip.hexwright.warding_box.media", media / MediaConstants.DUST_UNIT)
                .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}

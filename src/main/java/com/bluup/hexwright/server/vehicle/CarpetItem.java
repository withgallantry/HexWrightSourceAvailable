package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.progression.MakersMark;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CarpetItem extends VehicleItem {

    public CarpetItem(Properties properties) {
        super(properties);
    }

    @Override
    protected VehicleEntity createEntity(ServerLevel level) {
        return new CarpetEntity(HexwrightEntities.CARPET, level);
    }

    @Override
    protected long getMediaCapacity(ItemStack stack) {
        return VehicleConfig.CARPET_MEDIA_CAPACITY;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(CarpetVariant.of(stack).nameKey());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        CarpetVariant variant = CarpetVariant.of(stack);
        if (variant == CarpetVariant.PURPLE) {
            return;
        }

        PocketCasterData.Quality quality = VehicleData.getQuality(stack.getOrCreateTagElement(VehicleData.ROOT_TAG));
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.grade",
            Component.translatable(quality.translationKey())
        ).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.speed",
            String.format("%.2f", variant.maxHorizontalSpeed(quality)),
            String.format("%.2f", variant.horizontalSpeedBonus(quality))
        ).withStyle(ChatFormatting.AQUA));
    }
}

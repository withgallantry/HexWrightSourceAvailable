package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.misc.MediaConstants;
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

public class BroomItem extends VehicleItem {

    public BroomItem(Properties properties) {
        super(properties);
    }

    @Override
    protected VehicleEntity createEntity(ServerLevel level) {
        return new BroomEntity(HexwrightEntities.BROOM, level);
    }

    @Override
    protected long getMediaCapacity(ItemStack stack) {
        return BroomVariant.mediaCapacity(gradeOf(stack));
    }

    @Override
    protected boolean mountsOnDeploy() {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(BroomVariant.of(stack).nameKey());
    }

    private static PocketCasterData.Quality gradeOf(ItemStack stack) {
        return VehicleData.getQuality(stack.getOrCreateTagElement(VehicleData.ROOT_TAG));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        PocketCasterData.Quality quality = gradeOf(stack);
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.grade",
            Component.translatable(quality.translationKey())
        ).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.top_speed",
            String.format("%.2f", BroomVariant.maxHorizontalSpeed(quality))
        ).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.reservoir",
            BroomVariant.mediaCapacity(quality) / MediaConstants.DUST_UNIT
        ).withStyle(ChatFormatting.AQUA));
    }
}

package com.bluup.hexwright.server.network;

import com.bluup.hexwright.common.network.ResonanceNameCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResonantKeyItem extends Item {

    static final String ROOT_TAG = "hexwright_resonant_key";

    public ResonantKeyItem(Properties properties) {
        super(properties);
    }

    public static void attune(ItemStack key, Level level, BlockPos towerPos) {
        ResonantAttunement.attune(key, ROOT_TAG, level, towerPos);
    }

    public static @Nullable BlockPos attunedPos(ItemStack key) {
        return ResonantAttunement.towerPos(key, ROOT_TAG);
    }

    public static boolean attunedDimensionMatches(ItemStack key, Level level) {
        return ResonantAttunement.dimensionMatches(key, ROOT_TAG, level);
    }

    public static @Nullable String networkKey(ItemStack key) {
        return ResonantAttunement.networkKey(key, ROOT_TAG);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        BlockPos pos = attunedPos(stack);
        if (pos == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.resonant_key.unattuned")
                .withStyle(ChatFormatting.GRAY));
        } else {
            String name = ResonanceNameCache.nameOf(networkKey(stack));
            if (name != null) {
                tooltip.add(Component.translatable("tooltip.hexwright.resonant_key.network", name)
                    .withStyle(ChatFormatting.AQUA));
            }
            String dimension = ResonantAttunement.dimension(stack, ROOT_TAG);
            String dimensionPath = dimension.isEmpty() ? "?" : new ResourceLocation(dimension).getPath();
            tooltip.add(Component.translatable("tooltip.hexwright.resonant_key.attuned",
                    pos.getX(), pos.getY(), pos.getZ(), dimensionPath)
                .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return attunedPos(stack) != null;
    }
}

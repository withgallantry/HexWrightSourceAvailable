package com.bluup.hexwright.server.reliquary;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ReliquarySealItem extends Item {

    private static final String ROOT_TAG = "hexwright_reliquary_seal";
    private static final String TAG_POS = "Reliquary";
    private static final String TAG_DIMENSION = "Dimension";

    public ReliquarySealItem(Properties properties) {
        super(properties);
    }

    public static void attune(ItemStack seal, Level level, BlockPos pos) {
        CompoundTag root = seal.getOrCreateTagElement(ROOT_TAG);
        root.put(TAG_POS, NbtUtils.writeBlockPos(pos));
        root.putString(TAG_DIMENSION, level.dimension().location().toString());
        seal.removeTagKey("data");
    }

    public static @Nullable BlockPos attunedPos(ItemStack seal) {
        CompoundTag root = seal.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_POS)) {
            return null;
        }
        return NbtUtils.readBlockPos(root.getCompound(TAG_POS));
    }

    public static @Nullable String attunedDimension(ItemStack seal) {
        CompoundTag root = seal.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_DIMENSION)) {
            return null;
        }
        return root.getString(TAG_DIMENSION);
    }

    public static @Nullable String storeKey(ItemStack seal) {
        BlockPos pos = attunedPos(seal);
        String dimension = attunedDimension(seal);
        if (pos == null || dimension == null) {
            return null;
        }
        return ReliquaryStore.key(
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(dimension)),
            pos
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        BlockPos pos = attunedPos(stack);
        if (pos == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.reliquary_seal.unattuned", Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        } else {
            String dimension = attunedDimension(stack);
            String dimensionPath = dimension == null ? "?" : new ResourceLocation(dimension).getPath();
            tooltip.add(Component.translatable("tooltip.hexwright.reliquary_seal.attuned",
                    pos.getX(), pos.getY(), pos.getZ(), dimensionPath)
                .withStyle(ChatFormatting.AQUA));
        }

    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return attunedPos(stack) != null;
    }
}

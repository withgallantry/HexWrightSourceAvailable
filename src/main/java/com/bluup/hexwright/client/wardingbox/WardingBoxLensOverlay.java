package com.bluup.hexwright.client.wardingbox;

import at.petrak.hexcasting.api.client.ScryingLensOverlayRegistry;
import at.petrak.hexcasting.common.lib.HexItems;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class WardingBoxLensOverlay {

    private WardingBoxLensOverlay() {
    }

    public static void register() {
        ScryingLensOverlayRegistry.addDisplayer(HexwrightBlocks.WARDING_BOX_BLOCK, WardingBoxLensOverlay::addLines);
    }

    public static void addLines(List<Pair<ItemStack, Component>> lines,
                                BlockState state, BlockPos pos, Player observer, Level world, Direction hitFace) {
        if (!(world.getBlockEntity(pos) instanceof WardingBoxBlockEntity box)) {
            return;
        }

        lines.add(new Pair<>(new ItemStack(HexItems.AMETHYST_DUST), box.mediaLine()));
        lines.add(new Pair<>(new ItemStack(HexwrightBlocks.WARDING_BOX_ITEM), box.statusLine()));

        Component displayMsg = box.getDisplayMsg();
        ItemStack displayItem = box.getDisplayItem();
        if (displayMsg != null && displayItem != null) {
            lines.add(new Pair<>(displayItem, displayMsg));
        }
    }
}

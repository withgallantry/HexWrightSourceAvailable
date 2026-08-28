package com.bluup.hexwright.client.debug;

import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.lib.HexItems;
import com.bluup.hexwright.client.wardingbox.WardingBoxLensOverlay;
import com.bluup.hexwright.common.network.ResonanceNameCache;
import com.bluup.hexwright.server.block.ExchangeBridgeBlockEntity;
import com.bluup.hexwright.server.block.HarmonicEmitterBlockEntity;
import com.bluup.hexwright.server.block.HarmonicExchangeBlockEntity;
import com.bluup.hexwright.server.block.HarmonicTransducerBlockEntity;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.ResonanceTowerBlockEntity;
import com.bluup.hexwright.server.network.EssenceNetwork;
import com.bluup.hexwright.server.network.ResonantAttunement;
import com.bluup.hexwright.server.network.ResonantKeyItem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class HexwrightDebugLines {

    private HexwrightDebugLines() {
    }

    public static List<Pair<ItemStack, Component>> linesFor(
        BlockState state, BlockPos pos, Player observer, Level level, Direction hitFace
    ) {
        List<Pair<ItemStack, Component>> lines = new ArrayList<>();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (state.is(HexwrightBlocks.WARDING_BOX_BLOCK)) {
            WardingBoxLensOverlay.addLines(lines, state, pos, observer, level, hitFace);
            return lines;
        }
        if (blockEntity instanceof HarmonicEmitterBlockEntity emitter) {
            emitterLines(lines, emitter);
        } else if (blockEntity instanceof HarmonicTransducerBlockEntity transducer) {
            transducerLines(lines, transducer);
        } else if (blockEntity instanceof ExchangeBridgeBlockEntity bridge) {
            bridgeLines(lines, bridge);
        } else if (blockEntity instanceof HarmonicExchangeBlockEntity exchange) {
            exchangeLines(lines, exchange);
        } else if (blockEntity instanceof ResonanceTowerBlockEntity tower) {
            towerLines(lines, tower, level, pos);
        }
        return lines;
    }


    private static void emitterLines(List<Pair<ItemStack, Component>> lines, HarmonicEmitterBlockEntity emitter) {
        add(lines, HexwrightBlocks.HARMONIC_EMITTER_ITEM, networkLine(emitter.networkKey()));
        add(lines, null, channelLine(
            emitter.harmonic(), HarmonicEmitterBlockEntity.NO_HARMONIC,
            emitter.dockedKey(), emitter.networkKey(), emitter.inTowerRange()));
        add(lines, HexItems.AMETHYST_DUST,
            mediaLine(emitter.media(), HarmonicEmitterBlockEntity.CAPACITY));

        int patterns = emitter.hexLength();
        add(lines, null, patterns > 0
            ? Component.translatable("gui.hexwright.spectacles.hex", patterns).withStyle(ChatFormatting.GRAY)
            : Component.translatable("gui.hexwright.spectacles.hex.none").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void transducerLines(
        List<Pair<ItemStack, Component>> lines, HarmonicTransducerBlockEntity transducer
    ) {
        add(lines, HexwrightBlocks.HARMONIC_TRANSDUCER_ITEM, networkLine(transducer.networkKey()));
        add(lines, null, channelLine(
            transducer.harmonic(), HarmonicTransducerBlockEntity.NO_HARMONIC,
            transducer.dockedKey(), transducer.networkKey(), transducer.inTowerRange()));
        add(lines, null, Component.translatable("gui.hexwright.spectacles.trigger",
            Component.translatable(transducer.triggerLabel())).withStyle(ChatFormatting.GRAY));
        add(lines, null, transducer.isActive()
            ? Component.translatable("gui.hexwright.spectacles.active").withStyle(ChatFormatting.GREEN)
            : Component.translatable("gui.hexwright.spectacles.inactive").withStyle(ChatFormatting.GRAY));
        add(lines, HexItems.AMETHYST_DUST,
            mediaLine(transducer.media(), HarmonicTransducerBlockEntity.CAPACITY));
    }

    private static void bridgeLines(List<Pair<ItemStack, Component>> lines, ExchangeBridgeBlockEntity bridge) {
        int harmonic = bridge.harmonic();
        add(lines, HexwrightBlocks.EXCHANGE_BRIDGE_ITEM, harmonic == ExchangeBridgeBlockEntity.NO_HARMONIC
            ? Component.translatable("gui.hexwright.spectacles.channel.none").withStyle(ChatFormatting.GRAY)
            : Component.translatable("gui.hexwright.spectacles.channel", harmonic).withStyle(ChatFormatting.AQUA));

        for (int slot : new int[]{ExchangeBridgeBlockEntity.KEY_SLOT_1, ExchangeBridgeBlockEntity.KEY_SLOT_2}) {
            add(lines, null, Component.translatable("gui.hexwright.spectacles.bridge_side",
                slot + 1, networkName(bridge.networkKey(slot))).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void exchangeLines(List<Pair<ItemStack, Component>> lines, HarmonicExchangeBlockEntity exchange) {
        add(lines, HexwrightBlocks.HARMONIC_EXCHANGE_ITEM, networkLine(exchange.networkKey()));
        add(lines, null, keyStatus(exchange.dockedKey(), exchange.networkKey(), exchange.inTowerRange()));
    }

    private static void towerLines(
        List<Pair<ItemStack, Component>> lines, ResonanceTowerBlockEntity tower, Level level, BlockPos pos
    ) {
        String name = ResonanceNameCache.nameOf(ResonantAttunement.networkKey(level, pos));
        add(lines, HexwrightBlocks.RESONANCE_TOWER_ITEM, name == null
            ? Component.translatable("gui.hexwright.spectacles.network.unnamed").withStyle(ChatFormatting.GRAY)
            : Component.translatable("gui.hexwright.spectacles.tower", name).withStyle(ChatFormatting.AQUA));
        add(lines, null, Component.translatable("gui.hexwright.spectacles.tower_radius",
            (int) tower.radius()).withStyle(ChatFormatting.GRAY));
    }


    private static Component networkLine(@Nullable String networkKey) {
        return Component.translatable("gui.hexwright.spectacles.network", networkName(networkKey))
            .withStyle(networkKey == null ? ChatFormatting.GRAY : ChatFormatting.AQUA);
    }

    private static Component networkName(@Nullable String networkKey) {
        if (networkKey == null) {
            return Component.translatable("gui.hexwright.spectacles.network.none");
        }
        String name = ResonanceNameCache.nameOf(networkKey);
        return name == null
            ? Component.translatable("gui.hexwright.spectacles.network.unnamed")
            : Component.literal(name);
    }

    private static Component channelLine(
        int harmonic, int noHarmonic, ItemStack dockedKey, @Nullable String networkKey, boolean inRange
    ) {
        if (networkKey == null || !inRange) {
            return keyStatus(dockedKey, networkKey, inRange);
        }
        return harmonic == noHarmonic
            ? Component.translatable("gui.hexwright.spectacles.channel.none").withStyle(ChatFormatting.GRAY)
            : Component.translatable("gui.hexwright.spectacles.channel", harmonic).withStyle(ChatFormatting.AQUA);
    }

    private static Component keyStatus(ItemStack dockedKey, @Nullable String networkKey, boolean inRange) {
        if (networkKey == null) {
            return dockedKey.getItem() instanceof ResonantKeyItem
                ? Component.translatable("gui.hexwright.spectacles.unattuned_key").withStyle(ChatFormatting.GOLD)
                : Component.translatable("gui.hexwright.spectacles.no_key").withStyle(ChatFormatting.GRAY);
        }
        if (!inRange) {
            return Component.translatable("gui.hexwright.spectacles.out_of_range", EssenceNetwork.keyRange())
                .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("gui.hexwright.spectacles.anchored").withStyle(ChatFormatting.GREEN);
    }

    private static Component mediaLine(long media, long capacity) {
        return Component.translatable("gui.hexwright.spectacles.media",
                formatDust(media), formatDust(capacity))
            .withStyle(media <= 0L ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE);
    }

    private static String formatDust(long amount) {
        long tenths = amount * 10 / MediaConstants.DUST_UNIT;
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
    }

    private static void add(List<Pair<ItemStack, Component>> lines, @Nullable Item icon, Component text) {
        lines.add(new Pair<>(icon == null ? ItemStack.EMPTY : new ItemStack(icon), text));
    }
}

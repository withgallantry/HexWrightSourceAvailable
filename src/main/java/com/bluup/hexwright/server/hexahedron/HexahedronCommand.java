package com.bluup.hexwright.server.hexahedron;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class HexahedronCommand {

    private HexahedronCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("hexahedron")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("begin")
                        .executes(ctx -> begin(ctx, Hexahedron.DEFAULT_RADIUS))
                        .then(Commands.argument("radius",
                                IntegerArgumentType.integer(Hexahedron.MIN_RADIUS, Hexahedron.MAX_RADIUS))
                            .executes(ctx -> begin(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("end").executes(HexahedronCommand::endNearest))
                    .then(Commands.literal("feed")
                        .then(Commands.argument("anchor", IntegerArgumentType.integer(1, RiftAnchor.COUNT))
                            .executes(ctx -> feed(ctx, -1))
                            .then(Commands.argument("dust", IntegerArgumentType.integer(1))
                                .executes(ctx -> feed(ctx, IntegerArgumentType.getInteger(ctx, "dust"))))))
                    .then(Commands.literal("clear").executes(HexahedronCommand::clear))
                    .then(Commands.literal("list").executes(HexahedronCommand::list)))));
    }

    private static int begin(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        Hexahedron hexahedron = HexahedronStore.breakThrough(level, Mth.floor(at.x) + 0.5, Mth.floor(at.z) + 0.5, radius, true);
        source.sendSuccess(() -> Component.literal(
            "The Hexahedron is breaking through over " + describe(hexahedron) + ", warding " + radius + " blocks.")
            .withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static int endNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Hexahedron nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("There is no Hexahedron in this dimension."));
            return 0;
        }
        HexahedronStore.remove(level, nearest.id());
        Hexahedron ended = nearest;
        source.sendSuccess(() -> Component.literal("The Hexahedron over " + describe(ended) + " has withdrawn.")
            .withStyle(ChatFormatting.GRAY), true);
        return SINGLE_SUCCESS;
    }

    private static int feed(CommandContext<CommandSourceStack> ctx, int dust) {
        CommandSourceStack source = ctx.getSource();
        Hexahedron nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("There is no Hexahedron in this dimension."));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(ctx, "anchor") - 1;
        RiftAnchor anchor = nearest.anchors().get(index);
        long media = dust < 0 ? Math.max(RiftAnchor.MEDIA_NEEDED - anchor.media(), 0L) : dust * MediaConstants.DUST_UNIT;
        if (!RiftAnchors.feed(source.getLevel(), nearest, index, media)) {
            source.sendFailure(Component.literal("Anchor " + (index + 1) + " has already fallen."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Fed anchor " + (index + 1) + " of the Hexahedron over " + describe(nearest)
            + " " + media / MediaConstants.DUST_UNIT + " dust.").withStyle(ChatFormatting.GRAY), true);
        return SINGLE_SUCCESS;
    }

    private static @Nullable Hexahedron nearest(CommandSourceStack source) {
        Vec3 at = source.getPosition();
        Hexahedron nearest = null;
        double best = Double.MAX_VALUE;
        for (Hexahedron hexahedron : HexahedronStore.in(source.getLevel())) {
            double distanceSq = Mth.square(hexahedron.x() - at.x) + Mth.square(hexahedron.z() - at.z);
            if (distanceSq < best) {
                best = distanceSq;
                nearest = hexahedron;
            }
        }
        return nearest;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int removed = HexahedronStore.clear(source.getLevel());
        source.sendSuccess(() -> Component.literal("Removed " + removed + " Hexahedron(s) from this dimension.")
            .withStyle(ChatFormatting.GRAY), true);
        return removed;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Collection<Hexahedron> hexahedra = HexahedronStore.in(source.getLevel());
        if (hexahedra.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Hexahedron in this dimension.")
                .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (Hexahedron hexahedron : hexahedra) {
            source.sendSuccess(() -> Component.literal(
                "over " + describe(hexahedron) + ", warding " + hexahedron.radius() + " blocks")
                .withStyle(ChatFormatting.GRAY), false);
            for (int i = 0; i < RiftAnchor.COUNT; i++) {
                RiftAnchor anchor = hexahedron.anchors().get(i);
                Vec3 hung = hexahedron.anchorAt(i);
                String state = anchor.fallen() ? (anchor.landed() ? "down" : "falling")
                    : Mth.floor(anchor.fill() * 100.0f) + "% fed";
                String line = "  anchor " + (i + 1) + " over " + Mth.floor(hung.x) + " " + Mth.floor(hung.z) + ": " + state;
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return hexahedra.size();
    }

    private static String describe(Hexahedron hexahedron) {
        return Mth.floor(hexahedron.x()) + " " + Mth.floor(hexahedron.z());
    }
}

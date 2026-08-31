package com.bluup.hexwright.client.staff_assembly;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class StaffTipCommands {
    private StaffTipCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrighttip")
                .then(ClientCommandManager.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("toggle")
                    .executes(ctx -> setEnabled(ctx.getSource(), !StaffTipFlash.isEnabled())))
                .then(ClientCommandManager.literal("marker")
                    .then(ClientCommandManager.literal("on").executes(ctx -> setMarker(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off").executes(ctx -> setMarker(ctx.getSource(), false)))
                    .executes(ctx -> setMarker(ctx.getSource(), !StaffTipFlash.isMarker())))
                .then(ClientCommandManager.literal("flash").executes(ctx -> flash(ctx.getSource())))
                .then(ClientCommandManager.literal("size")
                    .then(ClientCommandManager.argument("blocks", FloatArgumentType.floatArg(0.01f, 2.0f))
                        .executes(ctx -> {
                            StaffTipFlash.setSize(FloatArgumentType.getFloat(ctx, "blocks"));
                            return flash(ctx.getSource());
                        })))
                .then(ClientCommandManager.literal("decay")
                    .then(ClientCommandManager.argument("ticks", FloatArgumentType.floatArg(1.0f, 60.0f))
                        .executes(ctx -> {
                            StaffTipFlash.setDecay(FloatArgumentType.getFloat(ctx, "ticks"));
                            return flash(ctx.getSource());
                        })))
                .then(ClientCommandManager.literal("color")
                    .then(ClientCommandManager.argument("rrggbb", StringArgumentType.word())
                        .executes(ctx -> setColor(ctx.getSource(),
                            StringArgumentType.getString(ctx, "rrggbb")))))
                .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                .executes(ctx -> status(ctx.getSource()))));
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        StaffTipFlash.setEnabled(enabled);
        feedback(source, enabled
            ? "Tip flash on. Bright on a spell, dim on a hex that cast none; /hexwrighttip flash to fire one."
            : "Tip flash off for this session.");
        return SINGLE_SUCCESS;
    }

    private static int setMarker(FabricClientCommandSource source, boolean marker) {
        StaffTipFlash.setMarker(marker);
        feedback(source, marker
            ? "Anchor marker on - red/green/blue arms through the flash point, on the staff in hand."
            : "Anchor marker off.");
        if (marker && !StaffTipFlash.isEnabled()) {
            feedback(source, "The flash itself is off; /hexwrighttip on to see both.");
        }
        return SINGLE_SUCCESS;
    }

    private static int flash(FabricClientCommandSource source) {
        if (!StaffTipFlash.isEnabled()) {
            StaffTipFlash.setEnabled(true);
            feedback(source, "Tip flash switched on to fire this one.");
        }
        StaffTipFlash.flash();
        return SINGLE_SUCCESS;
    }

    private static int setColor(FabricClientCommandSource source, String hex) {
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb;
        try {
            rgb = Integer.parseInt(cleaned, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            source.sendError(Component.literal("Not a hex colour: " + hex));
            return 0;
        }
        StaffTipFlash.setColor(rgb);
        return flash(source);
    }

    private static int status(FabricClientCommandSource source) {
        feedback(source, String.format(
            "Tip flash %s, marker %s. size %.3f blocks, decay %.1f ticks, colour #%06X.",
            StaffTipFlash.isEnabled() ? "on" : "off",
            StaffTipFlash.isMarker() ? "on" : "off",
            StaffTipFlash.size(), StaffTipFlash.decay(), StaffTipFlash.color()));

        Vector3f anchor = StaffTipFlash.heldAnchor();
        feedback(source, anchor == null
            ? StaffTipAnchors.count() + " staff models have an anchor; you are not holding a staff."
            : String.format("%d staff models have an anchor; the one in hand sits at %.2f, %.2f, %.2f"
                + " blocks (%.1f, %.1f, %.1f in model units).",
                StaffTipAnchors.count(), anchor.x, anchor.y, anchor.z,
                anchor.x * 16.0f, anchor.y * 16.0f, anchor.z * 16.0f));
        return SINGLE_SUCCESS;
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }
}

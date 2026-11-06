package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.server.command.CommandGate;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.RiftAnchor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

final class MaintenanceCommand {

    private MaintenanceCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("maintenance")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("status").executes(MaintenanceCommand::status))
                    .then(Commands.literal("workshops")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                            .executes(MaintenanceCommand::workshops)))
                    .then(Commands.literal("study").executes(MaintenanceCommand::study))
                    .then(Commands.literal("engage").executes(MaintenanceCommand::engage))
                    .then(Commands.literal("keystone").executes(MaintenanceCommand::keystone))
                    .then(Commands.literal("release").executes(MaintenanceCommand::release)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        PlanarMaintenance state = PlanarMaintenance.get(server);
        tell(ctx, state.workshops() + " of " + PlanarMaintenance.WORKSHOPS_BEFORE_STUDY + " Workshops beaten"
            + (state.studyForced() ? "; the next Workshop holds the study" : ""));
        if (state.engagedAt() < 0L) {
            tell(ctx, "Maintenance is not engaged.");
            return SINGLE_SUCCESS;
        }
        long now = System.currentTimeMillis();
        String incursion;
        if (state.awaitedWorkshop() >= 0) {
            incursion = "waiting on Workshop #" + state.awaitedWorkshop() + " to close";
        } else if (state.keystone() != null) {
            ServerLevel overworld = server.overworld();
            Hexahedron keystone = PlanarIncursion.find(overworld, state.keystone());
            String which = "Keystone " + state.keystones() + " of " + PlanarIncursion.KEYSTONES;
            if (keystone == null) {
                incursion = "the Inversion is open; maintenance is released once it recedes";
            } else if (keystone.burstAt() != RiftAnchor.NEVER) {
                incursion = which + " is filled and burst" + (keystone.opensInversion()
                    ? "; the Inversion is breaking out" : "; the next comes a day after its sky clears");
            } else {
                incursion = which + " is in the sky, withdrawing in "
                    + minutes(state.keystoneOpenedAt() + PlanarIncursion.LIFETIME_MILLIS - now)
                    + " (ending the incursion) unless all four anchors are filled";
            }
        } else if (state.nextKeystoneAt() >= 0L) {
            incursion = "Keystone " + (state.keystones() + 1) + " of " + PlanarIncursion.KEYSTONES + " is due in "
                + minutes(state.nextKeystoneAt() - now) + " (once somebody is in the overworld)";
        } else {
            incursion = "no Keystone due";
        }
        tell(ctx, "Maintenance engaged " + (server.overworld().getGameTime() - state.engagedAt()) / 20L
            + "s of game time ago; " + incursion + ".");
        return SINGLE_SUCCESS;
    }

    private static String minutes(long millis) {
        long minutes = Math.max(0L, millis) / 60_000L;
        return minutes >= 60L ? minutes / 60L + "h " + minutes % 60L + "m" : minutes + "m";
    }

    private static int workshops(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        PlanarMaintenance.get(ctx.getSource().getServer()).setWorkshops(count);
        tell(ctx, count + " Workshops now count as beaten.");
        return SINGLE_SUCCESS;
    }

    private static int study(CommandContext<CommandSourceStack> ctx) {
        PlanarMaintenance.get(ctx.getSource().getServer()).forceStudy();
        tell(ctx, "The next Workshop opened will hold the Keystone Study.");
        return SINGLE_SUCCESS;
    }

    private static int engage(CommandContext<CommandSourceStack> ctx) {
        boolean engaged = PlanarMaintenance.engage(ctx.getSource().getServer(), -1);
        tell(ctx, engaged ? "Maintenance engaged; the first Keystone comes in a minute."
            : "Maintenance was already engaged.");
        return SINGLE_SUCCESS;
    }

    private static int keystone(CommandContext<CommandSourceStack> ctx) {
        PlanarMaintenance state = PlanarMaintenance.get(ctx.getSource().getServer());
        if (state.engagedAt() < 0L || state.keystone() != null) {
            ctx.getSource().sendFailure(Component.literal(state.engagedAt() < 0L ? "Maintenance is not engaged."
                : "A Keystone is already in the sky."));
            return 0;
        }
        state.scheduleKeystone(System.currentTimeMillis());
        tell(ctx, "The next Keystone is breaking through.");
        return SINGLE_SUCCESS;
    }

    private static int release(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        PlanarIncursion.withdrawKeystone(server);
        PlanarMaintenance.release(server);
        tell(ctx, "Maintenance released; any Keystone in the sky withdrawn, and Workshops may hold the Keystone Study again.");
        return SINGLE_SUCCESS;
    }

    private static void tell(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GRAY), true);
    }
}

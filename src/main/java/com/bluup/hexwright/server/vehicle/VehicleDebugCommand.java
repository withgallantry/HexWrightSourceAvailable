package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.casting.iota.PatternIota;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class VehicleDebugCommand {

    private VehicleDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .then(Commands.literal("testflighthex")
                    .requires(source -> source.hasPermission(2))
                    .executes(VehicleDebugCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        if (!player.isCreative()) {
            source.sendFailure(Component.literal("This debug command only works in Creative mode."));
            return 0;
        }

        if (player.getVehicle() instanceof VehicleEntity vehicleEntity) {
            vehicleEntity.setStoredHex(new PatternIota(FlightActions.DEBUG_FLIGHT_HEX_PATTERN));
            vehicleEntity.setInternalMedia(vehicleEntity.getMediaCapacity());
            source.sendSuccess(
                () -> Component.literal("Loaded the debug flight hex onto the "
                    + vehicleEntity.getType().getDescription().getString()
                    + " you're riding and topped up its media.").withStyle(ChatFormatting.AQUA),
                false
            );
            return SINGLE_SUCCESS;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof VehicleItem vehicleItem)) {
            source.sendFailure(Component.literal(
                "Hold a broom or carpet in your main hand, or ride a deployed one, first."));
            return 0;
        }

        vehicleItem.writeDatum(held, new PatternIota(FlightActions.DEBUG_FLIGHT_HEX_PATTERN));
        vehicleItem.setMedia(held, vehicleItem.getMaxMedia(held));

        source.sendSuccess(
            () -> Component.literal("Loaded the debug flight hex onto your " + held.getHoverName().getString()
                + " and topped up its media.").withStyle(ChatFormatting.AQUA),
            false
        );
        return SINGLE_SUCCESS;
    }
}

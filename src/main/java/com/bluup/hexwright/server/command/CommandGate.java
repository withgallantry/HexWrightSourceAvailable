package com.bluup.hexwright.server.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class CommandGate {

    private CommandGate() {
    }

    public static boolean creative(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return player.isCreative();
    }
}

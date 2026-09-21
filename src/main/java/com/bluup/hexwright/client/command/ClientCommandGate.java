package com.bluup.hexwright.client.command;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ClientCommandGate {

    private ClientCommandGate() {
    }

    public static boolean creative(FabricClientCommandSource source) {
        return source.getPlayer() != null && source.getPlayer().isCreative();
    }
}

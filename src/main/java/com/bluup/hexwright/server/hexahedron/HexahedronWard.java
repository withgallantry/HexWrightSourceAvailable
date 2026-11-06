package com.bluup.hexwright.server.hexahedron;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.worldgen.AreaWard;
import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HexahedronWard {

    public static final float DISRUPTION_CHANCE = 0.05f;

    private HexahedronWard() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HexahedronWard::tick);
        TeleportWards.register(new TeleportWards.Check() {
            @Override
            public boolean isEmpty(ServerLevel level) {
                return HexahedronStore.isEmpty(level);
            }

            @Override
            public boolean refuses(ServerLevel level, Vec3 from, Vec3 to) {
                return HexahedronStore.covering(level, to) != null;
            }

            @Override
            public void notifyRefused(ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hexwright.hexahedron.refused"), true);
            }
        });
    }

    public static boolean covers(Level level, Vec3 point) {
        return level instanceof ServerLevel server && HexahedronStore.covering(server, point) != null;
    }

    public static boolean disrupts(CastingEnvironment env) {
        ServerLevel level = env.getWorld();
        Vec3 at = env.mishapSprayPos();
        if (HexahedronStore.covering(level, at) == null || level.random.nextFloat() >= DISRUPTION_CHANCE) {
            return false;
        }
        HexwrightDebug.log(HexwrightDebug.CASTING, "[hexahedron] tore apart a pattern cast at {}", at);
        return true;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            if (HexahedronStore.covering(player.serverLevel(), player.position()) != null && AreaWard.ground(player)) {
                player.displayClientMessage(Component.translatable("message.hexwright.hexahedron.grounded"), true);
            }
        }
    }
}

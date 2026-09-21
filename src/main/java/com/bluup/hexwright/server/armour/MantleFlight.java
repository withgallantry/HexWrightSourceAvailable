package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.utils.MediaHelper;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.remnant.RemnantBuffs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MantleFlight {

    private static final double MEDIA_PER_TICK = MantleOfAscensionItem.MEDIA_PER_SECOND / 20.0;

    private static final Map<UUID, Double> OWED = new HashMap<>();

    private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();

    private MantleFlight() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MantleFlight::tick);
    }

    private static void tick(MinecraftServer server) {
        MantleFlightState state = MantleFlightState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, state);
        }
    }

    private static void tickPlayer(ServerPlayer player, MantleFlightState state) {
        UUID id = player.getUUID();

        if (player.isCreative() || player.isSpectator()) {
            OWED.remove(id);
            LAST_DIMENSION.remove(id);
            state.setGranted(id, false);
            return;
        }

        if (!MantleOfAscensionItem.wornBy(player)) {
            OWED.remove(id);
            LAST_DIMENSION.remove(id);
            if (state.granted(id)) {
                state.setGranted(id, false);
                revoke(player);
            }
            return;
        }

        ResourceKey<Level> dimension = player.level().dimension();
        boolean changedDimension = LAST_DIMENSION.put(id, dimension) != dimension;
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (changedDimension) {
            player.onUpdateAbilities();
        }
        state.setGranted(id, true);

        if (!player.getAbilities().flying) {
            OWED.remove(id);
            return;
        }

        double owed = OWED.getOrDefault(id, 0.0) + MEDIA_PER_TICK;
        long due = (long) owed;
        if (due <= 0) {
            OWED.put(id, owed);
            return;
        }
        if (!payMedia(player, due)) {
            OWED.remove(id);
            state.setGranted(id, false);
            revoke(player);
            runDry(player);
            return;
        }
        OWED.put(id, owed - due);
    }

    private static void revoke(ServerPlayer player) {
        if (RemnantBuffs.has(player, RemnantType.ASCENDANT, player.server.overworld().getGameTime())) {
            return;
        }
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    private static void runDry(ServerPlayer player) {
        var level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            HexEvalSounds.MISHAP.sound(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(),
            12, 0.25, 0.4, 0.25, 0.02);
        player.displayClientMessage(
            Component.translatable("message.hexwright.mantle_of_ascension.no_media")
                .withStyle(ChatFormatting.RED), true);
    }

    private static boolean payMedia(ServerPlayer player, long cost) {
        if (drawMedia(player, cost, true) > 0) {
            return false;
        }
        drawMedia(player, cost, false);
        return true;
    }

    private static long drawMedia(ServerPlayer player, long cost, boolean simulate) {
        long remaining = cost;
        for (ADMediaHolder source : MediaHelper.scanPlayerForMediaStuff(player)) {
            remaining -= MediaHelper.extractMedia(source, remaining, false, simulate);
            if (remaining <= 0) {
                break;
            }
        }
        return remaining;
    }
}

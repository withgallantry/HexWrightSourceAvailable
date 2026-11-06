package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.HexahedronStore;
import com.bluup.hexwright.server.hexahedron.RiftAnchor;
import com.bluup.hexwright.server.inversion.Inversion;
import com.bluup.hexwright.server.inversion.InversionStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class PlanarIncursion {

    static final long FIRST_KEYSTONE_MILLIS = 60_000L;
    public static final long DAY_MILLIS = Math.max(1L, Long.getLong("hexwright.keystone.dayMinutes", 24L * 60L)) * 60_000L;
    static final long LIFETIME_MILLIS = 2L * DAY_MILLIS;
    private static final long BETWEEN_MILLIS = DAY_MILLIS;
    static final int KEYSTONES = 4;
    private static final long SURVEY_GIVEN_UP_MILLIS = 30_000L;
    private static final int CHECK_TICKS = 20;

    private static final String BREAKING = "message.hexwright.keystone.breaking";
    private static final String WITHDRAWN = "message.hexwright.keystone.withdrawn";
    private static final String[] DIRECTIONS =
        {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};

    private static long surveyingSince = -1L;

    private PlanarIncursion() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS == 0) {
                tick(server);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> surveyingSince = -1L);
    }

    private static void tick(MinecraftServer server) {
        PlanarMaintenance state = PlanarMaintenance.get(server);
        if (state.engagedAt() < 0L) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long now = System.currentTimeMillis();
        UUID keystone = state.keystone();
        if (keystone != null) {
            watch(overworld, state, keystone, now);
            return;
        }
        boolean surveying = surveyingSince >= 0L && now - surveyingSince < SURVEY_GIVEN_UP_MILLIS;
        if (state.nextKeystoneAt() >= 0L && now >= state.nextKeystoneAt() && !surveying) {
            breakThrough(overworld);
        }
    }

    private static void watch(ServerLevel level, PlanarMaintenance state, UUID id, long now) {
        Hexahedron keystone = find(level, id);
        if (keystone != null) {
            long burstAt = keystone.burstAt();
            if (burstAt == RiftAnchor.NEVER && now - state.keystoneOpenedAt() >= LIFETIME_MILLIS) {
                HexahedronStore.remove(level, id);
                Component told = Component.translatable(WITHDRAWN).withStyle(ChatFormatting.DARK_AQUA);
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(told);
                }
                PlanarMaintenance.release(level.getServer());
                HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] keystone {} of {} withdrew unfilled; released",
                    state.keystones(), KEYSTONES);
            } else if (burstAt != RiftAnchor.NEVER && !keystone.opensInversion()
                && level.getGameTime() >= burstAt + Hexahedron.CLEAR_TICKS) {
                HexahedronStore.remove(level, id);
                state.keystoneFilled(now + BETWEEN_MILLIS);
                HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] keystone {} of {} filled; the next in a day",
                    state.keystones(), KEYSTONES);
            }
            return;
        }
        if (inversionOf(level, id) == null) {
            PlanarMaintenance.release(level.getServer());
            HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] keystone {} and its Inversion are gone; released", id);
        }
    }

    static @Nullable Inversion inversionOf(ServerLevel level, UUID keystone) {
        for (Inversion inversion : InversionStore.in(level)) {
            if (keystone.equals(inversion.source())) {
                return inversion;
            }
        }
        return null;
    }

    private static void breakThrough(ServerLevel level) {
        List<ServerPlayer> here = level.getPlayers(player -> player.isAlive() && !player.isSpectator());
        if (here.isEmpty()) {
            return;
        }
        ServerPlayer near = here.get(level.getRandom().nextInt(here.size()));
        surveyingSince = System.currentTimeMillis();
        KeystoneSites.find(level, near.blockPosition(), Hexahedron.DEFAULT_RADIUS, level.getRandom())
            .whenCompleteAsync((site, error) -> {
                surveyingSince = -1L;
                PlanarMaintenance state = PlanarMaintenance.get(level.getServer());
                if (site == null || state.engagedAt() < 0L || state.keystone() != null) {
                    return;
                }
                Hexahedron keystone = HexahedronStore.breakThrough(level, site.getX() + 0.5, site.getZ() + 0.5,
                    Hexahedron.DEFAULT_RADIUS, state.keystones() + 1 >= KEYSTONES);
                state.keystoneOpened(keystone.id(), System.currentTimeMillis());
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(Component.translatable(BREAKING,
                        Component.translatable("message.hexwright.keystone.direction." + direction(player, keystone)))
                        .withStyle(ChatFormatting.DARK_AQUA));
                }
                HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] keystone {} of {} breaking through over {} {}",
                    state.keystones(), KEYSTONES, site.getX(), site.getZ());
            }, level.getServer());
    }

    static void withdrawKeystone(MinecraftServer server) {
        UUID keystone = PlanarMaintenance.get(server).keystone();
        if (keystone != null) {
            HexahedronStore.remove(server.overworld(), keystone);
        }
    }

    private static String direction(ServerPlayer player, Hexahedron keystone) {
        double angle = Math.toDegrees(Math.atan2(keystone.z() - player.getZ(), keystone.x() - player.getX()));
        return DIRECTIONS[Math.floorMod(Math.round(angle / 45.0), DIRECTIONS.length)];
    }

    static @Nullable Hexahedron find(ServerLevel level, UUID id) {
        for (Hexahedron hexahedron : HexahedronStore.in(level)) {
            if (hexahedron.id().equals(id)) {
                return hexahedron;
            }
        }
        return null;
    }
}

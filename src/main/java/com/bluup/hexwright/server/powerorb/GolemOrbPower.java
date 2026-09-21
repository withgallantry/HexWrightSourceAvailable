package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GolemOrbPower {

    public static final long SUMMON_COST = 15 * MediaConstants.DUST_UNIT;

    private static final int PERIOD = 5;
    private static final int HEALTH_SYNC_TICKS = 20;
    private static final double RECALL_DISTANCE = 48.0D;

    private static final Map<UUID, SpiritGolemEntity> GOLEMS = new HashMap<>();

    private GolemOrbPower() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GolemOrbPower::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SpiritGolemEntity golem = GOLEMS.remove(handler.player.getUUID());
            if (golem != null) {
                putAway(golem, PowerOrbSlot.worn(handler.player, PowerOrbPower.GOLEM));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GOLEMS.clear());
    }

    public static boolean claims(SpiritGolemEntity golem) {
        UUID owner = golem.getOwnerUUID();
        return owner != null && GOLEMS.get(owner) == golem;
    }

    public static void summon(ServerPlayer player, ItemStack orb) {
        if (!PowerOrbData.holdsGolem(orb)) {
            PowerOrbData.setGolemHealth(orb, SpiritGolemEntity.MAX_HEALTH);
        }
        PowerOrbData.setActive(orb, true);
        if (!GOLEMS.containsKey(player.getUUID())) {
            callOut(player, orb);
        }
        syncStatus(player);
    }

    public static void dismiss(ServerPlayer player, ItemStack orb) {
        PowerOrbData.setActive(orb, false);
        SpiritGolemEntity golem = GOLEMS.remove(player.getUUID());
        if (golem != null && golem.isDeadOrDying()) {
            PowerOrbData.clearGolem(orb);
        } else if (golem != null) {
            putAway(golem, orb);
        }
        syncStatus(player);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % PERIOD != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, now);
            syncStatus(player);
        }
    }

    private static void syncStatus(ServerPlayer player) {
        HexwrightEffects.sync(player, HexwrightEffects.SPIRIT_GOLEM, GOLEMS.containsKey(player.getUUID()),
            MobEffectInstance.INFINITE_DURATION);
    }

    private static void tickPlayer(ServerPlayer player, int now) {
        UUID id = player.getUUID();
        ItemStack orb = PowerOrbSlot.worn(player, PowerOrbPower.GOLEM);
        SpiritGolemEntity golem = GOLEMS.get(id);

        if (golem != null && golem.isDeadOrDying()) {
            GOLEMS.remove(id);
            if (!orb.isEmpty()) {
                PowerOrbData.clearGolem(orb);
                PowerOrbData.setActive(orb, false);
            }
            player.displayClientMessage(Component.translatable("message.hexwright.power_orb.golem_slain")
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (golem != null && golem.isRemoved()) {
            GOLEMS.remove(id);
            golem = null;
        }

        boolean wanted = !orb.isEmpty() && PowerOrbData.isActive(orb) && PowerOrbData.holdsGolem(orb)
            && player.isAlive() && !player.isSpectator();
        if (golem != null) {
            boolean strayed = golem.level() != player.level()
                || golem.distanceToSqr(player) > RECALL_DISTANCE * RECALL_DISTANCE;
            if (!wanted || strayed) {
                GOLEMS.remove(id);
                putAway(golem, orb);
                return;
            }
            if (now % HEALTH_SYNC_TICKS == 0 && PowerOrbData.golemHealth(orb) != golem.getHealth()) {
                PowerOrbData.setGolemHealth(orb, golem.getHealth());
            }
            return;
        }
        if (wanted) {
            callOut(player, orb);
        }
    }

    private static void callOut(ServerPlayer player, ItemStack orb) {
        ServerLevel level = player.serverLevel();
        Vec3 at = SpiritGolemEntity.spotNear(level, player, PowerOrbEntities.SPIRIT_GOLEM);
        if (at == null) {
            at = player.position();
        }
        SpiritGolemEntity golem = SpiritGolemEntity.summon(level, player, at, PowerOrbData.golemHealth(orb));
        if (golem != null) {
            GOLEMS.put(player.getUUID(), golem);
        }
    }

    private static void putAway(SpiritGolemEntity golem, ItemStack orb) {
        if (golem.isRemoved() || golem.isDeadOrDying()) {
            return;
        }
        if (!orb.isEmpty()) {
            PowerOrbData.setGolemHealth(orb, golem.getHealth());
        }
        golem.dismiss();
    }
}

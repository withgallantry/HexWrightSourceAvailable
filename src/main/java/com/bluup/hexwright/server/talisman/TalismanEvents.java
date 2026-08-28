package com.bluup.hexwright.server.talisman;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class TalismanEvents {

    private static final long SLOT_SYNC_INTERVAL_TICKS = 10L;

    private TalismanEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % SLOT_SYNC_INTERVAL_TICKS != 0L) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TalismanSlots.sync(player);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (TalismanCasting.isCasting()) {
                return true;
            }
            if (entity instanceof ServerPlayer player) {
                TalismanCasting.onTrigger(player, TalismanData.Trigger.STRUCK,
                    source.getEntity(), (double) amount);
            }
            Entity attacker = source.getEntity();
            if (attacker instanceof ServerPlayer dealer && dealer != entity) {
                TalismanCasting.onTrigger(dealer, TalismanData.Trigger.HURT,
                    entity, (double) amount);
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && !TalismanCasting.isCasting()) {
                TalismanCasting.onTrigger(killer, TalismanData.Trigger.FELLED,
                    entity, (double) entity.getMaxHealth());
            }
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damage) -> {
            if (entity instanceof ServerPlayer player && !TalismanCasting.isCasting()) {
                TalismanCasting.onTrigger(player, TalismanData.Trigger.DEATH,
                    source.getEntity(), (double) damage);
            }
            return true;
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && !TalismanCasting.isCasting()) {
                TalismanCasting.onTrigger(serverPlayer, TalismanData.Trigger.MINE,
                    null, (double) state.getDestroySpeed(level, pos), Vec3.atCenterOf(pos));
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && !TalismanCasting.isCasting()) {
                TalismanCasting.onTrigger(serverPlayer, TalismanData.Trigger.INTERACT,
                    null, null, hitResult.getLocation());
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && !TalismanCasting.isCasting()) {
                TalismanCasting.onTrigger(serverPlayer, TalismanData.Trigger.INTERACT,
                    entity, null, entity.position());
            }
            return InteractionResult.PASS;
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (!TalismanCasting.isCasting()) {
                TalismanCasting.onTrigger(player, TalismanData.Trigger.TRAVERSE,
                    null, null, player.position());
            }
        });
    }
}

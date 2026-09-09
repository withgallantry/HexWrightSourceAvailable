package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.journal.ClientInvestigations;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class InvestigationProgress {

    private static final int SWEEP_INTERVAL_TICKS = 20;

    private static int tickCounter;

    private InvestigationProgress() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(InvestigationProgress::sweep);

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            ServerPlayer killer = creditedKiller(entity, source.getEntity());
            if (killer != null) {
                recordKill(killer, entity);
            }
        });
    }


    public static Set<String> completedFor(Player player) {
        return player instanceof ServerPlayer server
            ? InvestigationState.get(server.server).completed(server.getUUID())
            : ClientInvestigations.completed();
    }

    public static boolean isComplete(Player player, String investigationId) {
        return completedFor(player).contains(investigationId);
    }

    public static List<Investigation> visibleFor(Player player) {
        return Investigations.visible(completedFor(player));
    }


    public static boolean complete(ServerPlayer player, String investigationId) {
        if (!InvestigationState.get(player.server).complete(player.getUUID(), investigationId)) {
            return false;
        }
        HexwrightNetworking.sendInvestigationSync(player);
        announce(player, investigationId);
        return true;
    }

    public static int completeAll(ServerPlayer player) {
        InvestigationState state = InvestigationState.get(player.server);
        int closed = 0;
        for (Investigation investigation : Investigations.all()) {
            if (state.complete(player.getUUID(), investigation.id())) {
                closed++;
            }
        }
        if (closed > 0) {
            HexwrightNetworking.sendInvestigationSync(player);
        }
        return closed;
    }

    public static boolean resetAll(ServerPlayer player) {
        InvestigationState state = InvestigationState.get(player.server);
        boolean changed = state.uncompleteAll(player.getUUID());
        changed |= state.clearCounters(player.getUUID());
        if (changed) {
            HexwrightNetworking.sendInvestigationSync(player);
        }
        return changed;
    }

    public static boolean reset(ServerPlayer player, Collection<String> investigationIds) {
        InvestigationState state = InvestigationState.get(player.server);
        boolean changed = false;
        for (String id : investigationIds) {
            changed |= state.uncomplete(player.getUUID(), id);
        }
        if (changed) {
            HexwrightNetworking.sendInvestigationSync(player);
        }
        return changed;
    }


    private static void sweep(MinecraftServer server) {
        if (++tickCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        List<Investigation> content = Investigations.all();
        if (content.isEmpty()) {
            return;
        }

        InvestigationState state = InvestigationState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<String> done = state.completed(player.getUUID());

            List<Investigation> newlyDone = null;
            for (Investigation investigation : content) {
                if (done.contains(investigation.id()) || !done.containsAll(investigation.requires())) {
                    continue;
                }
                boolean met;
                try {
                    met = investigation.completion().isMet(player, state);
                } catch (Throwable t) {
                    Hexwright.LOGGER.error("Investigation '{}' threw while being checked", investigation.id(), t);
                    continue;
                }
                if (met) {
                    (newlyDone == null ? newlyDone = new ArrayList<>() : newlyDone).add(investigation);
                }
            }
            if (newlyDone != null) {
                for (Investigation investigation : newlyDone) {
                    complete(player, investigation.id());
                }
            }
        }
    }

    public static void recordCrucibleEssence(MinecraftServer server, UUID player,
                                             IngredientCategory aspect, double amount) {
        if (amount <= 0) {
            return;
        }
        var counted = Investigations.get().countedByKey();
        if (counted.isEmpty()) {
            return;
        }
        int units = (int) Math.min(Integer.MAX_VALUE,
            Math.round(amount * CompletionCondition.CrucibleBurn.SCALE));
        if (units <= 0) {
            return;
        }
        InvestigationState state = InvestigationState.get(server);
        for (CompletionCondition.Counted condition : counted.values()) {
            if (condition instanceof CompletionCondition.CrucibleBurn burn && burn.watches(aspect)) {
                state.addToCounter(player, burn.counterKey(), units);
            }
        }
    }

    private static void recordKill(ServerPlayer killer, LivingEntity dead) {
        var counted = Investigations.get().countedByKey();
        if (counted.isEmpty()) {
            return;
        }
        InvestigationState state = InvestigationState.get(killer.server);
        for (CompletionCondition.Counted condition : counted.values()) {
            if (condition instanceof CompletionCondition.Kill kill && kill.watches(dead)) {
                state.addToCounter(killer.getUUID(), kill.counterKey(), 1);
            }
        }
    }

    private static @Nullable ServerPlayer creditedKiller(LivingEntity dead, @Nullable Entity direct) {
        if (dead.getKillCredit() instanceof ServerPlayer credited) {
            return credited;
        }
        return direct instanceof ServerPlayer player ? player : null;
    }

    private static void announce(ServerPlayer player, String investigationId) {
        Investigation investigation = Investigations.get().byId().get(investigationId);
        Component name = investigation == null
            ? Component.literal(investigationId)
            : investigation.titleComponent();

        player.sendSystemMessage(Component.translatable("chat.hexwright.investigation.complete", name)
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.playNotifySound(SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 0.7f, 1.2f);
    }
}

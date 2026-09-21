package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.journal.ClientInvestigations;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.progression.RecipeUnlocks;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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

    public static Set<String> artifactsFor(Player player) {
        return player instanceof ServerPlayer server
            ? InvestigationState.get(server.server).artifacts(server.getUUID())
            : ClientInvestigations.artifacts();
    }

    public static boolean isComplete(Player player, String investigationId) {
        return completedFor(player).contains(investigationId);
    }

    public static List<Investigation> visibleFor(Player player) {
        return Investigations.visible(completedFor(player));
    }


    public static boolean complete(ServerPlayer player, String investigationId) {
        InvestigationState state = InvestigationState.get(player.server);
        if (state.isComplete(player.getUUID(), investigationId)) {
            return false;
        }

        Set<String> loreBefore = readableLore(player);

        state.complete(player.getUUID(), investigationId);
        HexwrightNetworking.sendInvestigationSync(player);
        announce(player, investigationId);
        announceNewLore(player, loreBefore);
        grantUnlocks(player, investigationId);
        return true;
    }

    private static void grantUnlocks(ServerPlayer player, String investigationId) {
        Investigation investigation = Investigations.get().byId().get(investigationId);
        if (investigation == null) {
            return;
        }
        for (String recipe : investigation.unlocks()) {
            if (RecipeUnlocks.unlock(player, recipe)) {
                player.displayClientMessage(
                    Component.translatable(
                        "message.hexwright.investigation.learned",
                        Component.translatable(recipe).withStyle(ChatFormatting.AQUA)
                    ).withStyle(ChatFormatting.GOLD),
                    false
                );
            }
        }
    }

    public static int completeAll(ServerPlayer player) {
        InvestigationState state = InvestigationState.get(player.server);
        int closed = 0;
        for (Investigation investigation : Investigations.all()) {
            if (state.complete(player.getUUID(), investigation.id())) {
                closed++;
            }
            for (String recipe : investigation.unlocks()) {
                RecipeUnlocks.unlock(player, recipe);
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

    public static boolean discoverArtifact(ServerPlayer player, String artifactId) {
        if (!InvestigationState.get(player.server).discoverArtifact(player.getUUID(), artifactId)) {
            return false;
        }
        HexwrightNetworking.sendArtifactSync(player);
        HexwrightNetworking.sendJournalToast(player, JournalToastKind.ARTIFACT, artifactId);
        return true;
    }

    public static int discoverAllArtifacts(ServerPlayer player) {
        InvestigationState state = InvestigationState.get(player.server);
        int found = 0;
        for (Artifact artifact : Artifacts.all()) {
            if (state.discoverArtifact(player.getUUID(), artifact.id())) {
                found++;
            }
        }
        if (found > 0) {
            HexwrightNetworking.sendArtifactSync(player);
        }
        return found;
    }

    public static boolean forgetArtifacts(ServerPlayer player) {
        boolean changed = InvestigationState.get(player.server).forgetArtifacts(player.getUUID());
        if (changed) {
            HexwrightNetworking.sendArtifactSync(player);
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
        List<Artifact> artifacts = Artifacts.all();
        if (content.isEmpty() && artifacts.isEmpty()) {
            return;
        }

        InvestigationState state = InvestigationState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!artifacts.isEmpty()) {
                recordCarriedArtifacts(player, state, artifacts);
            }
            if (content.isEmpty()) {
                continue;
            }
            Set<String> done = state.completed(player.getUUID());

            List<Investigation> newlyDone = null;
            for (Investigation investigation : content) {
                if (done.contains(investigation.id())) {
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

    private static void recordCarriedArtifacts(ServerPlayer player, InvestigationState state, List<Artifact> artifacts) {
        Set<String> known = state.artifacts(player.getUUID());
        List<Artifact> missing = null;
        for (Artifact artifact : artifacts) {
            if (!known.contains(artifact.id())) {
                (missing == null ? missing = new ArrayList<>() : missing).add(artifact);
            }
        }
        if (missing == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        for (int slot = 0; slot <= size && !missing.isEmpty(); slot++) {
            ItemStack stack = slot == size ? player.containerMenu.getCarried() : inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (Iterator<Artifact> it = missing.iterator(); it.hasNext(); ) {
                Artifact artifact = it.next();
                if (artifact.matches(stack)) {
                    it.remove();
                    discoverArtifact(player, artifact.id());
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

        HexwrightNetworking.sendJournalToast(player, kindOf(investigation), investigationId);
    }

    private static void announceNewLore(ServerPlayer player, Set<String> before) {
        for (String id : readableLore(player)) {
            if (!before.contains(id)) {
                HexwrightNetworking.sendJournalToast(player, JournalToastKind.LORE, id);
            }
        }
    }

    private static Set<String> readableLore(ServerPlayer player) {
        List<LoreEntry> readable = LoreEntries.visible(completedFor(player));
        if (readable.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>(readable.size());
        for (LoreEntry entry : readable) {
            ids.add(entry.id());
        }
        return ids;
    }

    private static JournalToastKind kindOf(@Nullable Investigation investigation) {
        return investigation != null && investigation.completion().discoversStructure()
            ? JournalToastKind.STRUCTURE
            : JournalToastKind.INVESTIGATION;
    }
}

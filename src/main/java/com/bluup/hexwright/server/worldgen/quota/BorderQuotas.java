package com.bluup.hexwright.server.worldgen.quota;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BorderQuotas {

    private static final int FIRST_COUNT_DELAY = 20 * 60;

    @Nullable
    private static BorderQuotaJob job;

    private static final Deque<ResourceKey<Level>> DUE = new ArrayDeque<>();

    private static final Map<ResourceKey<Level>, List<String>> REPORTS = new LinkedHashMap<>();

    private static int countAt = -1;

    private BorderQuotas() {
    }

    public static void register() {
        ServerWorldEvents.LOAD.register((server, level) -> PlantedSites.get(level).publish(level));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> countAt = server.getTickCount() + FIRST_COUNT_DELAY);
        ServerTickEvents.END_SERVER_TICK.register(BorderQuotas::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            job = null;
            DUE.clear();
            REPORTS.clear();
            countAt = -1;
        });
    }

    static boolean countNow(MinecraftServer server) {
        if (job != null || !DUE.isEmpty()) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            DUE.add(level.dimension());
        }
        return true;
    }

    static boolean counting() {
        return job != null || !DUE.isEmpty();
    }

    static Map<ResourceKey<Level>, List<String>> reports() {
        return REPORTS;
    }

    static void reported(ServerLevel level, PlayableArea area, List<String> lines) {
        List<String> report = new ArrayList<>();
        report.add("inside " + area.description() + ":");
        report.addAll(lines);
        REPORTS.put(level.dimension(), report);
    }

    private static void tick(MinecraftServer server) {
        if (countAt >= 0 && server.getTickCount() >= countAt) {
            countAt = -1;
            countNow(server);
        }
        if (job != null) {
            try {
                if (job.tick()) {
                    job = null;
                }
            } catch (RuntimeException failure) {
                Hexwright.LOGGER.error("The {} failed; it runs again at the next start", job, failure);
                job.abandon("it threw " + failure);
                job = null;
            }
            return;
        }
        ResourceKey<Level> next = DUE.poll();
        ServerLevel level = next == null ? null : server.getLevel(next);
        if (level == null) {
            return;
        }
        List<BorderQuotaJob.Quota> quotas = quotasIn(level);
        if (quotas.isEmpty()) {
            REPORTS.remove(next);
            return;
        }
        PlayableArea area = PlayableArea.of(level);
        if (area == null) {
            REPORTS.put(next, List.of("no border fences this level in, so there is nothing to keep a count within"));
            return;
        }
        job = new BorderQuotaJob(level, area, quotas);
    }

    private static List<BorderQuotaJob.Quota> quotasIn(ServerLevel level) {
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        List<BorderQuotaJob.Quota> quotas = new ArrayList<>();
        for (Map.Entry<ResourceKey<Structure>, Structure> entry : structures.entrySet()) {
            if (!(entry.getValue() instanceof BorderQuota quota) || quota.minimumWithinBorder() <= 0) {
                continue;
            }
            List<StructurePlacement> placements = state.getPlacementsForStructure(structures.wrapAsHolder(entry.getValue()));
            if (!placements.isEmpty()) {
                quotas.add(new BorderQuotaJob.Quota(entry.getKey(), entry.getValue(), placements.get(0),
                    quota.minimumWithinBorder()));
            }
        }
        return quotas;
    }
}

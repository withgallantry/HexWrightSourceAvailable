package com.bluup.hexwright.server.worldgen.quota;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlantedSites extends SavedData {

    private static final String STORAGE_ID = "hexwright_planted_sites";
    private static final String TAG_SITES = "Sites";
    private static final String TAG_STRUCTURE = "Structure";
    private static final String TAG_START = "Start";
    private static final String TAG_BOX = "Box";
    private static final String TAG_STANDING = "Standing";

    record Site(ResourceKey<Structure> structure, ChunkPos start, BoundingBox box, boolean standing) {
    }

    private final List<Site> sites = new ArrayList<>();

    public static PlantedSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlantedSites::load, PlantedSites::new, STORAGE_ID);
    }

    List<Site> sites() {
        return Collections.unmodifiableList(this.sites);
    }

    Site plan(ServerLevel level, ResourceKey<Structure> structure, ChunkPos start, BoundingBox box) {
        Site site = new Site(structure, start, box, false);
        this.sites.add(site);
        setDirty();
        publish(level);
        return site;
    }

    void stand(ServerLevel level, Site site, BoundingBox box) {
        this.sites.set(this.sites.indexOf(site), new Site(site.structure(), site.start(), box, true));
        setDirty();
        publish(level);
    }

    void forget(ServerLevel level, Site site) {
        if (this.sites.remove(site)) {
            setDirty();
            publish(level);
        }
    }

    public void publish(ServerLevel level) {
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Long2ObjectMap<Structure> planned = new Long2ObjectOpenHashMap<>();
        for (Site site : this.sites) {
            Structure structure = structures.get(site.structure());
            if (!site.standing() && structure != null) {
                planned.put(site.start().toLong(), structure);
            }
        }
        ((PlannedStarts) level.getChunkSource().getGeneratorState())
            .hexwright$setPlannedStarts(Long2ObjectMaps.unmodifiable(planned));
    }

    public static void createPlannedStart(ChunkGenerator generator, RegistryAccess registryAccess,
                                          ChunkGeneratorStructureState state, StructureManager structures,
                                          ChunkAccess chunk, StructureTemplateManager templates) {
        Structure structure = ((PlannedStarts) state).hexwright$plannedStarts().get(chunk.getPos().toLong());
        if (structure == null) {
            return;
        }
        SectionPos section = SectionPos.bottomOf(chunk);
        if (structures.getStartForStructure(section, structure, chunk) != null) {
            return;
        }
        StructureStart start = structure.generate(registryAccess, generator, generator.getBiomeSource(),
            state.randomState(), templates, state.getLevelSeed(), chunk.getPos(), 0, chunk, structure.biomes()::contains);
        if (start.isValid()) {
            structures.setStartForStructure(section, structure, start, chunk);
        }
    }

    private static PlantedSites load(CompoundTag tag) {
        PlantedSites state = new PlantedSites();
        ListTag sites = tag.getList(TAG_SITES, Tag.TAG_COMPOUND);
        for (int index = 0; index < sites.size(); index++) {
            CompoundTag row = sites.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(row.getString(TAG_STRUCTURE));
            int[] box = row.getIntArray(TAG_BOX);
            if (id == null || box.length != 6) {
                continue;
            }
            state.sites.add(new Site(ResourceKey.create(Registries.STRUCTURE, id), new ChunkPos(row.getLong(TAG_START)),
                new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]), row.getBoolean(TAG_STANDING)));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag sites = new ListTag();
        for (Site site : this.sites) {
            CompoundTag row = new CompoundTag();
            row.putString(TAG_STRUCTURE, site.structure().location().toString());
            row.putLong(TAG_START, site.start().toLong());
            BoundingBox box = site.box();
            row.putIntArray(TAG_BOX, new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            row.putBoolean(TAG_STANDING, site.standing());
            sites.add(row);
        }
        tag.put(TAG_SITES, sites);
        return tag;
    }
}

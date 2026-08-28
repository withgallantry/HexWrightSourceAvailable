package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vault.VaultDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DecadentVaultRegistry extends SavedData {

    private static final String STORAGE_ID = "hexwright_decadent_vaults";

    private static final int SEPARATION = 24;

    private static final int DEPTH_ABOVE_FLOOR = 2;

    private final Map<Integer, Site> sites = new LinkedHashMap<>();
    private int nextId;

    private record Site(ResourceKey<Level> dimension, BlockPos origin) {
        AABB bounds() {
            return new AABB(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + DecadentVaultGenerator.SIZE,
                origin.getY() + DecadentVaultGenerator.HEIGHT,
                origin.getZ() + DecadentVaultGenerator.SIZE);
        }
    }

    public static DecadentVaultRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(DecadentVaultRegistry::load, DecadentVaultRegistry::new, STORAGE_ID);
    }

    public BlockPos createFor(ServerLevel level, BlockPos portalPos) {
        BlockPos origin = siteUnder(level, portalPos);
        int id = nextId++;
        sites.put(id, new Site(level.dimension(), origin));
        setDirty();
        BlockPos entrance = DecadentVaultGenerator.generate(
            level, origin, level.dimension(), portalPos, level.random);
        Hexwright.LOGGER.info("Rolled Decadent Vault {} at {} in {} for a portal at {}",
            id, origin, level.dimension().location(), portalPos);
        return entrance;
    }

    private BlockPos siteUnder(ServerLevel level, BlockPos portalPos) {
        int y = level.getMinBuildHeight() + DEPTH_ABOVE_FLOOR;
        int x = portalPos.getX() - DecadentVaultGenerator.SIZE / 2;
        int z = portalPos.getZ() - DecadentVaultGenerator.SIZE / 2;
        int step = DecadentVaultGenerator.SIZE + SEPARATION;
        for (int attempt = 0; attempt < 64; attempt++) {
            BlockPos candidate = new BlockPos(x + attempt * step, y, z);
            if (isClear(level.dimension(), new Site(level.dimension(), candidate).bounds())) {
                return candidate;
            }
        }
        return new BlockPos(x, y, z);
    }

    private boolean isClear(ResourceKey<Level> dimension, AABB bounds) {
        for (Site site : sites.values()) {
            if (site.dimension().equals(dimension) && site.bounds().intersects(bounds)) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmptyIn(ResourceKey<Level> dimension) {
        for (Site site : sites.values()) {
            if (site.dimension().equals(dimension)) {
                return false;
            }
        }
        return true;
    }

    public boolean isInside(ResourceKey<Level> dimension, Vec3 position) {
        for (Site site : sites.values()) {
            if (site.dimension().equals(dimension) && site.bounds().contains(position)) {
                return true;
            }
        }
        return false;
    }

    private DecadentVaultRegistry() {
    }

    private static DecadentVaultRegistry load(CompoundTag tag) {
        DecadentVaultRegistry registry = new DecadentVaultRegistry();
        registry.nextId = tag.getInt("NextId");
        ListTag entries = tag.getList("Vaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag row = entries.getCompound(i);
            int id = row.getInt("Id");
            BlockPos origin = new BlockPos(row.getInt("X"), row.getInt("Y"), row.getInt("Z"));
            ResourceLocation dimensionId = ResourceLocation.tryParse(row.getString("Dimension"));
            ResourceKey<Level> dimension = dimensionId == null
                ? VaultDimension.KEY
                : ResourceKey.create(Registries.DIMENSION, dimensionId);
            registry.sites.put(id, new Site(dimension, origin));
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", nextId);
        ListTag entries = new ListTag();
        sites.forEach((id, site) -> {
            CompoundTag row = new CompoundTag();
            row.putInt("Id", id);
            row.putString("Dimension", site.dimension().location().toString());
            row.putInt("X", site.origin().getX());
            row.putInt("Y", site.origin().getY());
            row.putInt("Z", site.origin().getZ());
            entries.add(row);
        });
        tag.put("Vaults", entries);
        return tag;
    }
}

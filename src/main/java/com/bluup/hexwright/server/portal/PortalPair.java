package com.bluup.hexwright.server.portal;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PortalPair {

    public static final int OPEN_TICKS = 30;

    public static final double EXIT_CLEARANCE = 0.2;

    public static final long NO_EXPIRY = Long.MAX_VALUE;

    private final UUID id;
    private final UUID caster;
    private final PortalWindow[] windows;
    private final long createdGameTime;
    private final long expiryGameTime;
    private final PortalTransform[] transforms;
    private final @Nullable ResourceKey<Level>[] dimensions;
    private final boolean sessionBound;

    public PortalPair(UUID id, UUID caster, PortalWindow a, PortalWindow b, long createdGameTime) {
        this(id, caster, a, b, createdGameTime, NO_EXPIRY, null, null, false);
    }

    public PortalPair(UUID id, UUID caster, PortalWindow a, PortalWindow b, long createdGameTime,
                      long expiryGameTime) {
        this(id, caster, a, b, createdGameTime, expiryGameTime, null, null, false);
    }

    public PortalPair(UUID id, UUID caster, PortalWindow a, PortalWindow b, long createdGameTime,
                      @Nullable ResourceKey<Level> dimA, @Nullable ResourceKey<Level> dimB,
                      boolean sessionBound) {
        this(id, caster, a, b, createdGameTime, NO_EXPIRY, dimA, dimB, sessionBound);
    }

    @SuppressWarnings("unchecked")
    public PortalPair(UUID id, UUID caster, PortalWindow a, PortalWindow b, long createdGameTime,
                      long expiryGameTime,
                      @Nullable ResourceKey<Level> dimA, @Nullable ResourceKey<Level> dimB,
                      boolean sessionBound) {
        this.id = id;
        this.caster = caster;
        this.windows = new PortalWindow[]{a, b};
        this.createdGameTime = createdGameTime;
        this.expiryGameTime = expiryGameTime;
        this.transforms = new PortalTransform[]{new PortalTransform(a, b), new PortalTransform(b, a)};
        this.dimensions = (ResourceKey<Level>[]) new ResourceKey[]{dimA, dimB};
        this.sessionBound = sessionBound;
    }

    public UUID id() {
        return id;
    }

    public UUID caster() {
        return caster;
    }

    public PortalWindow window(int side) {
        return windows[side];
    }

    public PortalTransform transformFrom(int side) {
        return transforms[side];
    }

    public long createdGameTime() {
        return createdGameTime;
    }

    public long expiryGameTime() {
        return expiryGameTime;
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= expiryGameTime;
    }

    public @Nullable ResourceKey<Level> dimension(int side) {
        return dimensions[side];
    }

    public ResourceKey<Level> dimensionOr(int side, ResourceKey<Level> fallback) {
        ResourceKey<Level> dim = dimensions[side];
        return dim == null ? fallback : dim;
    }

    public boolean sideIn(int side, ResourceKey<Level> dim) {
        ResourceKey<Level> own = dimensions[side];
        return own == null || own.equals(dim);
    }

    public boolean isCrossDimensional() {
        return dimensions[0] != null && dimensions[1] != null && !dimensions[0].equals(dimensions[1]);
    }

    public boolean isSessionBound() {
        return sessionBound;
    }

    public boolean isValid() {
        return windows[0].isValid() && windows[1].isValid();
    }

    public boolean isOpen(long gameTime) {
        return gameTime - createdGameTime >= OPEN_TICKS;
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Caster", caster);
        tag.put("A", windows[0].save());
        tag.put("B", windows[1].save());
        tag.putLong("Created", createdGameTime);
        if (expiryGameTime != NO_EXPIRY) {
            tag.putLong("Expiry", expiryGameTime);
        }
        if (dimensions[0] != null) {
            tag.putString("DimA", dimensions[0].location().toString());
        }
        if (dimensions[1] != null) {
            tag.putString("DimB", dimensions[1].location().toString());
        }
        return tag;
    }

    public static PortalPair load(CompoundTag tag) {
        return new PortalPair(
            tag.getUUID("Id"),
            tag.getUUID("Caster"),
            PortalWindow.load(tag.getCompound("A")),
            PortalWindow.load(tag.getCompound("B")),
            tag.getLong("Created"),
            tag.contains("Expiry") ? tag.getLong("Expiry") : NO_EXPIRY,
            readDim(tag, "DimA"),
            readDim(tag, "DimB"),
            false);
    }

    private static @Nullable ResourceKey<Level> readDim(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(tag.getString(key));
        return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        windows[0].write(buf);
        windows[1].write(buf);
        writeDim(buf, dimensions[0]);
        writeDim(buf, dimensions[1]);
    }

    public static PortalPair read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        PortalWindow a = PortalWindow.read(buf);
        PortalWindow b = PortalWindow.read(buf);
        ResourceKey<Level> dimA = readDim(buf);
        ResourceKey<Level> dimB = readDim(buf);
        return new PortalPair(id, id, a, b, 0L, NO_EXPIRY, dimA, dimB, false);
    }

    private static void writeDim(FriendlyByteBuf buf, @Nullable ResourceKey<Level> dim) {
        buf.writeBoolean(dim != null);
        if (dim != null) {
            buf.writeResourceLocation(dim.location());
        }
    }

    private static @Nullable ResourceKey<Level> readDim(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
    }
}

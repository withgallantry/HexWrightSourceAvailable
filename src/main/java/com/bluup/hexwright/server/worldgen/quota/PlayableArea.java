package com.bluup.hexwright.server.worldgen.quota;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;
import org.popcraft.chunky.shape.Shape;
import org.popcraft.chunkyborder.BorderData;
import org.popcraft.chunkyborder.ChunkyBorder;
import org.popcraft.chunkyborder.ChunkyBorderProvider;

import java.util.Optional;

public record PlayableArea(int minX, int minZ, int maxX, int maxZ, String description, Edge edge) {

    private static final boolean CHUNKY_BORDER = FabricLoader.getInstance().isModLoaded("chunkyborder");

    private static final int MAX_SPAN = 100_000;

    @FunctionalInterface
    public interface Edge {
        boolean inside(double x, double z);
    }

    public static @Nullable PlayableArea of(ServerLevel level) {
        PlayableArea area = vanilla(level.getWorldBorder());
        if (CHUNKY_BORDER) {
            PlayableArea chunky = ChunkyBorderBridge.of(level);
            if (chunky != null) {
                area = area == null ? chunky : area.intersect(chunky);
            }
        }
        if (area == null || area.maxX - area.minX > MAX_SPAN || area.maxZ - area.minZ > MAX_SPAN) {
            return null;
        }
        return area;
    }

    private static @Nullable PlayableArea vanilla(WorldBorder border) {
        if (border.getSize() > MAX_SPAN) {
            return null;
        }
        return new PlayableArea(Mth.floor(border.getMinX()), Mth.floor(border.getMinZ()),
            Mth.ceil(border.getMaxX()) - 1, Mth.ceil(border.getMaxZ()) - 1,
            "the world border, " + Mth.floor(border.getSize()) + " across around " + centre(border.getCenterX(), border.getCenterZ()),
            border::isWithinBounds);
    }

    private PlayableArea intersect(PlayableArea other) {
        return new PlayableArea(Math.max(this.minX, other.minX), Math.max(this.minZ, other.minZ),
            Math.min(this.maxX, other.maxX), Math.min(this.maxZ, other.maxZ),
            this.description + " and " + other.description,
            (x, z) -> this.edge.inside(x, z) && other.edge.inside(x, z));
    }

    public boolean contains(double x, double z) {
        return x >= this.minX && x <= this.maxX + 1 && z >= this.minZ && z <= this.maxZ + 1 && this.edge.inside(x, z);
    }

    public boolean contains(BoundingBox box) {
        return contains(box.minX() + 0.5, box.minZ() + 0.5) && contains(box.maxX() + 0.5, box.minZ() + 0.5)
            && contains(box.minX() + 0.5, box.maxZ() + 0.5) && contains(box.maxX() + 0.5, box.maxZ() + 0.5);
    }

    public int centreX() {
        return (this.minX + this.maxX) / 2;
    }

    public int centreZ() {
        return (this.minZ + this.maxZ) / 2;
    }

    private static String centre(double x, double z) {
        return "(" + Mth.floor(x) + ", " + Mth.floor(z) + ")";
    }

    private static final class ChunkyBorderBridge {

        private static @Nullable PlayableArea of(ServerLevel level) {
            ChunkyBorder chunkyBorder;
            try {
                chunkyBorder = ChunkyBorderProvider.get();
            } catch (IllegalStateException notStarted) {
                return null;
            }
            Optional<BorderData> border = chunkyBorder.getBorder(level.dimension().location().toString());
            if (border.isEmpty()) {
                return null;
            }
            BorderData data = border.get();
            Shape shape = data.getBorder();
            return new PlayableArea(
                Mth.floor(data.getCenterX() - data.getRadiusX()), Mth.floor(data.getCenterZ() - data.getRadiusZ()),
                Mth.ceil(data.getCenterX() + data.getRadiusX()), Mth.ceil(data.getCenterZ() + data.getRadiusZ()),
                "ChunkyBorder's " + data.getShape() + ", radius " + Mth.floor(data.getRadiusX()) + " around "
                    + centre(data.getCenterX(), data.getCenterZ()),
                shape::isBounding);
        }
    }
}

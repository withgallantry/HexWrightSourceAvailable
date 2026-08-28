package com.bluup.hexwright.server.worldgen;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.mixin.EntitySharedFlagAccessor;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonBlast;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonClears;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonPiece;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonPlanner;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonStructure;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaWard {

    private static final ResourceKey<Structure> DEEP_DUNGEON =
        ResourceKey.create(Registries.STRUCTURE, Hexwright.id("deep_dungeon"));

    private static final double WARDED_FALL_SPEED = 0.35;

    private static final int FLAG_FALL_FLYING = 7;

    private static final int CELL_CACHE_LIMIT = 8192;

    private static final BoundingBox[] NONE = new BoundingBox[0];

    private record Seal(@Nullable Structure structure, int minY, int maxY) {

        boolean possible(int y) {
            return this.structure != null && y >= this.minY && y <= this.maxY;
        }
    }

    private record Cell(ResourceKey<Level> dimension, long chunk) {
    }

    private record Area(ResourceKey<Level> dimension, BoundingBox box) {
    }

    @Nullable
    private static volatile Seal seal;

    private static final Map<Cell, BoundingBox[]> CELLS = new ConcurrentHashMap<>();

    private static final Map<UUID, Area> AREAS = new ConcurrentHashMap<>();

    private static final Set<UUID> LOWERING = ConcurrentHashMap.newKeySet();

    public interface Unsealed {

        boolean ignoresAreaWard();
    }

    private AreaWard() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AreaWard::tick);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!refusesEdit(player, level, pos)) {
                return InteractionResult.PASS;
            }
            notifyImmovable((ServerPlayer) player);
            return InteractionResult.FAIL;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (!refusesEdit(player, level, pos)) {
                return true;
            }
            notifyImmovable((ServerPlayer) player);
            return false;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            LOWERING.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            seal = null;
            CELLS.clear();
            LOWERING.clear();
            AREAS.clear();
        });
    }

    public static void seal(ResourceKey<Level> dimension, UUID owner, BoundingBox box) {
        AREAS.put(owner, new Area(dimension, box));
    }

    public static void unseal(UUID owner) {
        AREAS.remove(owner);
    }

    public static boolean clearDungeon(ServerLevel level, BlockPos pos) {
        Structure structure = seal(level).structure();
        if (structure == null) {
            return false;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        if (!start.isValid()) {
            return false;
        }
        if (!DungeonClears.get(level.getServer()).clear(level.dimension(), start.getChunkPos().toLong())) {
            return false;
        }
        CELLS.clear();
        return true;
    }

    public static boolean sealed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        return inArea(server, pos) || inModule(server, pos);
    }

    public static boolean sealed(Level level, Vec3 point) {
        return sealed(level, BlockPos.containing(point));
    }

    private static boolean inArea(ServerLevel level, BlockPos pos) {
        if (AREAS.isEmpty()) {
            return false;
        }
        for (Area area : AREAS.values()) {
            if (area.dimension().equals(level.dimension()) && area.box().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inModule(ServerLevel level, BlockPos pos) {
        if (!seal(level).possible(pos.getY())) {
            return false;
        }
        for (BoundingBox box : boxes(level, pos)) {
            if (box.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    public static void prune(Level level, List<BlockPos> positions, @Nullable Entity source) {
        if (positions.isEmpty() || !(level instanceof ServerLevel server)) {
            return;
        }
        boolean breaks = source instanceof DungeonBlast blast && blast.hexwright$breaksDungeon();
        if (AREAS.isEmpty() && (breaks || seal(server).structure() == null)) {
            return;
        }
        positions.removeIf(pos -> inArea(server, pos)
            || (!breaks && inModule(server, pos) && !charged(server, pos)));
    }

    private static boolean charged(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.TNT) && dungeonCharge(level, pos);
    }

    public static boolean dungeonCharge(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        Seal current = seal(server);
        if (!current.possible(pos.getY())) {
            return false;
        }
        StructureStart start = server.structureManager().getStructureWithPieceAt(pos, current.structure());
        if (!start.isValid()) {
            return false;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof DungeonPiece module && module.getBoundingBox().isInside(pos)
                && module.holdsCharge(pos)) {
                return true;
            }
        }
        return false;
    }

    private static Seal seal(ServerLevel level) {
        Seal current = seal;
        if (current != null) {
            return current;
        }
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(DEEP_DUNGEON);
        Seal resolved = structure instanceof DungeonStructure dungeon
            ? new Seal(structure, dungeon.lowestFloorY(), dungeon.highestCeilingY())
            : new Seal(null, 0, 0);
        seal = resolved;
        return resolved;
    }

    private static BoundingBox[] boxes(ServerLevel level, BlockPos pos) {
        Cell cell = new Cell(level.dimension(), ChunkPos.asLong(pos));
        BoundingBox[] cached = CELLS.get(cell);
        if (cached != null) {
            return cached;
        }
        ChunkPos chunk = new ChunkPos(pos);
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
            return NONE;
        }
        Structure structure = seal(level).structure();
        DungeonClears cleared = DungeonClears.get(level.getServer());
        BoundingBox span = new BoundingBox(
            chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
            chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ());
        List<BoundingBox> found = new ArrayList<>();
        for (StructureStart start : level.structureManager().startsForStructure(chunk, candidate -> candidate == structure)) {
            if (cleared.isCleared(level.dimension(), start.getChunkPos().toLong())) {
                continue;
            }
            for (StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().intersects(span)) {
                    found.add(piece.getBoundingBox());
                }
            }
        }
        BoundingBox[] boxes = found.isEmpty() ? NONE : found.toArray(BoundingBox[]::new);
        if (CELLS.size() >= CELL_CACHE_LIMIT) {
            CELLS.clear();
        }
        CELLS.put(cell, boxes);
        return boxes;
    }

    private static boolean refusesEdit(Player player, Level level, BlockPos pos) {
        if (level.isClientSide || !(player instanceof ServerPlayer server)
            || server.isSpectator() || server.isCreative()) {
            return false;
        }
        return sealed(level, pos);
    }

    private static void notifyImmovable(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.hexwright.dungeon.immovable"), true);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && !player.isCreative()
                && sealed(player.level(), player.blockPosition())) {
                ground(player);
            }
            lower(player);
        }
    }

    private static void ground(ServerPlayer player) {
        boolean taken = false;
        if (player.isFallFlying()) {
            ((EntitySharedFlagAccessor) player).hexwright$setSharedFlag(FLAG_FALL_FLYING, false);
            taken = true;
        }
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            taken = true;
        }
        if (taken) {
            LOWERING.add(player.getUUID());
            player.displayClientMessage(Component.translatable("message.hexwright.dungeon.grounded"), true);
        }
    }

    private static void lower(ServerPlayer player) {
        if (LOWERING.isEmpty() || !LOWERING.contains(player.getUUID())) {
            return;
        }
        if (player.onGround()) {
            LOWERING.remove(player.getUUID());
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < -WARDED_FALL_SPEED) {
            player.setDeltaMovement(motion.x, -WARDED_FALL_SPEED, motion.z);
            player.hurtMarked = true;
        }
        player.resetFallDistance();
    }
}

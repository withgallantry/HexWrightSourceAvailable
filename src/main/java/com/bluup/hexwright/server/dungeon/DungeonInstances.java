package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.combat.SpellCaster;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import com.bluup.hexwright.server.vault.VaultDimension;
import com.bluup.hexwright.server.vault.VaultManager;
import com.bluup.hexwright.server.vault.VaultPortalSession;
import com.bluup.hexwright.server.vault.VaultRecord;
import com.bluup.hexwright.server.vault.VaultRegistry;
import com.bluup.hexwright.server.worldgen.AreaWard;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonInstanceLayout;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class DungeonInstances {

    public static final int RIFT_OPEN_TICKS =
        Mth.clamp(Integer.getInteger("hexwright.dungeon.riftSeconds", 180), 10, 3600) * 20;

    static final int CELL_SPACING = 1024;

    private static final int GRID_WIDTH = 8;

    public static final int MAX_INSTANCES = 32;

    private static final int MARGIN = 16;

    private static final int VOID_DROP = 8;

    private static final int RIFT_WARNING_TICKS = 30 * 20;

    private static final int EMPTY_GRACE_TICKS = 100;

    private static final int OFFLINE_GRACE_TICKS = 5 * 60 * 20;

    private static final int CROSSING_COOLDOWN_TICKS = 40;

    private static final int RIFT_DISTANCE = 2;

    private static final int BODY_ASIDE = 3;

    private static final double ANNOUNCE_RANGE = 24.0;

    private static final int WARD_INTERVAL = 10;

    private static final int CLEAR_RETRY_TICKS = 5 * 60 * 20;

    private static final long WARD_ID = 0x4878447567656F6EL;

    @Nullable
    private static DungeonInstanceJob job;

    private static final Map<Integer, Integer> EMPTY = new HashMap<>();
    private static final Set<Integer> HOLDING = new HashSet<>();
    private static final Map<UUID, Long> CROSSED = new HashMap<>();
    private static final Map<Integer, Integer> NOT_BEFORE = new HashMap<>();

    private static final ResourceLocation NO_PVP_PHASE = Hexwright.id("dungeon_no_pvp");

    private DungeonInstances() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DungeonInstances::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(NO_PVP_PHASE, Event.DEFAULT_PHASE);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(NO_PVP_PHASE, DungeonInstances::allowDamage);
        ServerLifecycleEvents.SERVER_STARTED.register(DungeonInstances::onStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            job = null;
            EMPTY.clear();
            HOLDING.clear();
            CROSSED.clear();
            NOT_BEFORE.clear();
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!DungeonDimension.isDungeonLevel(level) || !(state.getBlock() instanceof BedBlock)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hexwright.dungeon.no_sleeping"), true);
            }
            return InteractionResult.SUCCESS;
        });
        DungeonRifts.register();
        DungeonSkies.register();
    }


    public static boolean open(ServerPlayer player, DungeonInstance.Kind kind) {
        return open(player.serverLevel(), feetOf(player), player.getDirection(), player.getUUID(), kind,
            message -> player.displayClientMessage(message, true));
    }

    public static boolean open(ServerLevel level, BlockPos feet, Direction facing, UUID opener,
                               DungeonInstance.Kind kind, Consumer<Component> tell) {
        if (!mayOpenFrom(level, tell)) {
            return false;
        }
        BlockPos spot = riftSpot(level, DungeonInstanceRegistry.get(level.getServer()), feet, facing);
        if (spot == null) {
            tell.accept(Component.translatable("message.hexwright.dungeon.no_room"));
            return false;
        }
        DungeonInstance instance = create(level, new DungeonInstance.Entrance(level.dimension(),
            Vec3.atBottomCenterOf(spot).add(0.0, DungeonRift.HALF_LENGTH, 0.0), facing), opener, kind, tell);
        if (instance == null) {
            return false;
        }
        DungeonRifts.broadcast(level.getServer(), level.dimension());
        level.playSound(null, spot.above(), HexwrightSoundEvents.voidTearOpen(), SoundSource.PLAYERS, 1.0F, 0.6F);
        tell.accept(Component.translatable("message.hexwright.dungeon.forming"));
        return true;
    }

    public static @Nullable DungeonInstance openAtStone(ServerLevel level, TravelStoneBlockEntity stone, UUID opener,
                                                        DungeonInstance.Kind kind, Consumer<Component> tell) {
        if (!mayOpenFrom(level, tell)) {
            return null;
        }
        BlockPos at = stone.getBlockPos();
        DungeonInstance instance = create(level, new DungeonInstance.Entrance(level.dimension(),
            stone.windowCenter(), stone.front().getOpposite(), at), opener, kind, tell);
        if (instance != null) {
            instance.holdWindowUntil(level.getServer().overworld().getGameTime() + stone.openTicks());
            DungeonInstanceRegistry.get(level.getServer()).setDirty();
        }
        return instance;
    }

    private static boolean mayOpenFrom(ServerLevel level, Consumer<Component> tell) {
        if (DungeonDimension.level(level.getServer()) == null) {
            tell.accept(Component.translatable("message.hexwright.dungeon.no_dimension"));
            return false;
        }
        if (DungeonDimension.isDungeonLevel(level) || VaultDimension.isVaultLevel(level)) {
            tell.accept(Component.translatable("message.hexwright.dungeon.wrong_place"));
            return false;
        }
        return true;
    }

    private static @Nullable DungeonInstance create(ServerLevel level, DungeonInstance.Entrance entrance,
                                                    UUID opener, DungeonInstance.Kind kind,
                                                    Consumer<Component> tell) {
        MinecraftServer server = level.getServer();
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        int cell = registry.size() >= MAX_INSTANCES ? -1 : registry.freeCell(GRID_WIDTH * GRID_WIDTH);
        if (cell < 0) {
            tell.accept(Component.translatable("message.hexwright.dungeon.busy"));
            return null;
        }
        long seed = level.getRandom().nextLong();
        Footprint footprint = footprint(kind, DungeonDimension.level(server), cellOrigin(cell), seed);
        if (footprint == null) {
            tell.accept(Component.translatable("message.hexwright.dungeon.failed"));
            return null;
        }
        DungeonInstance instance = registry.create(kind, cell, seed, opener, entrance, footprint.extent(),
            footprint.startChunk(), footprint.shaftFoot(), footprint.room().shaft());
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {} opened in cell {} from {} {}",
            instance.id(), cell, level.dimension().location(), BlockPos.containing(entrance.center()).toShortString());
        return instance;
    }

    private record Footprint(BoundingBox extent, long startChunk, BlockPos shaftFoot, DungeonSafeRoom room) {
    }

    private static @Nullable Footprint footprint(DungeonInstance.Kind kind, ServerLevel dungeons, BlockPos origin,
                                                 long seed) {
        return switch (kind) {
            case DUNGEON -> {
                DungeonInstanceLayout.Plan plan = DungeonInstanceLayout.plan(dungeons, origin.getX(), origin.getZ(), seed);
                if (plan == null) {
                    yield null;
                }
                DungeonSafeRoom room = new DungeonSafeRoom(
                    new BlockPos(plan.shaftFoot().getX(), plan.lid(), plan.shaftFoot().getZ()));
                BoundingBox extent = BoundingBox.encapsulatingBoxes(List.of(plan.start().getBoundingBox(), room.box()))
                    .orElseThrow();
                yield new Footprint(extent, plan.start().getChunkPos().toLong(), plan.shaftFoot(), room);
            }
            case TALUS_ARENA, TOWER_SKELETON_ARENA, MAGNUS_ARENA, COG_SENTINEL_ARENA -> {
                ArenaInstanceLayout.Plan plan = ArenaInstanceLayout.plan(dungeons, origin, kind);
                yield plan == null ? null
                    : new Footprint(plan.extent(), new ChunkPos(origin).toLong(), plan.bridgeFoot(), plan.room());
            }
        };
    }

    private static BlockPos feetOf(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        BlockState state = player.level().getBlockState(feet);
        return state.isAir() || state.canBeReplaced() ? feet : feet.above();
    }

    private static @Nullable BlockPos riftSpot(ServerLevel level, DungeonInstanceRegistry registry, BlockPos feet,
                                               Direction facing) {
        for (int distance = RIFT_DISTANCE; distance <= RIFT_DISTANCE + 1; distance++) {
            BlockPos base = feet.relative(facing, distance);
            if (clear(level, base) && clear(level, base.above()) && clear(level, base.above(2))
                && !nearOtherRift(registry, level, base)) {
                return base;
            }
        }
        return null;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean nearOtherRift(DungeonInstanceRegistry registry, ServerLevel level, BlockPos base) {
        Vec3 center = Vec3.atBottomCenterOf(base).add(0.0, DungeonRift.HALF_LENGTH, 0.0);
        for (DungeonInstance other : registry.all()) {
            if (other.entrance().dimension().equals(level.dimension())
                && other.entrance().center().distanceToSqr(center) < 9.0) {
                return true;
            }
        }
        return false;
    }


    static BlockPos cellOrigin(int cell) {
        int column = cell % GRID_WIDTH - GRID_WIDTH / 2;
        int row = cell / GRID_WIDTH - GRID_WIDTH / 2;
        return new BlockPos(column * CELL_SPACING, 0, row * CELL_SPACING);
    }

    private static int cellAt(double x, double z) {
        int column = Mth.floor((x + CELL_SPACING / 2.0) / CELL_SPACING) + GRID_WIDTH / 2;
        int row = Mth.floor((z + CELL_SPACING / 2.0) / CELL_SPACING) + GRID_WIDTH / 2;
        if (column < 0 || column >= GRID_WIDTH || row < 0 || row >= GRID_WIDTH) {
            return -1;
        }
        return row * GRID_WIDTH + column;
    }

    public static @Nullable DungeonInstance at(MinecraftServer server, Vec3 pos) {
        int cell = cellAt(pos.x, pos.z);
        return cell < 0 ? null : DungeonInstanceRegistry.get(server).byCell(cell);
    }

    static BoundingBox bounds(ServerLevel dungeons, DungeonInstance instance) {
        BoundingBox around = instance.extent().inflatedBy(MARGIN);
        return new BoundingBox(around.minX(), Math.max(around.minY(), dungeons.getMinBuildHeight()), around.minZ(),
            around.maxX(), Math.min(around.maxY(), dungeons.getMaxBuildHeight() - 1), around.maxZ());
    }

    public static boolean allowsVaultDoor(ServerLevel level, Vec3 point) {
        DungeonInstance instance = at(level.getServer(), point);
        return instance != null && instance.state() == DungeonInstance.State.OPEN
            && instance.safeRoom().interior().contains(point);
    }


    private static void tick(MinecraftServer server) {
        ServerLevel dungeons = DungeonDimension.level(server);
        if (dungeons == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        tickJob(server, dungeons, registry);
        if (registry.size() == 0 && dungeons.players().isEmpty()) {
            return;
        }
        contain(server, dungeons, registry);
        Map<Integer, Set<UUID>> inVaults = vaultOccupants(server);
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != DungeonInstance.State.OPEN) {
                continue;
            }
            tickRift(server, instance, now);
            tickSeal(dungeons, instance, now);
            crossInto(server, dungeons, registry, instance, now);
            crossOut(server, dungeons, instance, now);
            tickOccupancy(server, dungeons, registry, instance, now, inVaults.getOrDefault(instance.id(), Set.of()));
        }
        if (server.getTickCount() % WARD_INTERVAL == 0) {
            wardSafeRooms(dungeons, registry);
        }
        if (server.getTickCount() % 200 == 0) {
            CROSSED.values().removeIf(at -> now - at > CROSSING_COOLDOWN_TICKS);
        }
    }

    private static void tickJob(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry) {
        if (job != null) {
            try {
                if (job.tick()) {
                    job = null;
                }
            } catch (RuntimeException failure) {
                Hexwright.LOGGER.error("{} failed", job, failure);
                job.release();
                failed(server, dungeons, registry, job.instance());
                job = null;
            }
        }
        if (job == null) {
            job = nextJob(server, dungeons, registry);
        }
    }

    private static @Nullable DungeonInstanceJob nextJob(MinecraftServer server, ServerLevel dungeons,
                                                       DungeonInstanceRegistry registry) {
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != DungeonInstance.State.BUILDING) {
                continue;
            }
            DungeonInstanceJob build = buildJob(server, dungeons, registry, instance);
            if (build == null) {
                failed(server, dungeons, registry, instance);
                continue;
            }
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] building instance {} ({})", instance.id(),
                instance.kind());
            return build;
        }
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != DungeonInstance.State.CLEARING
                || server.getTickCount() < NOT_BEFORE.getOrDefault(instance.id(), 0)) {
                continue;
            }
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] clearing instance {}", instance.id());
            evacuate(server, dungeons, instance);
            return DungeonInstanceJob.clear(dungeons, instance, bounds(dungeons, instance),
                () -> cleared(registry, instance));
        }
        return null;
    }

    private static @Nullable DungeonInstanceJob buildJob(MinecraftServer server, ServerLevel dungeons,
                                                        DungeonInstanceRegistry registry, DungeonInstance instance) {
        BlockPos origin = cellOrigin(instance.cell());
        Runnable built = () -> built(server, dungeons, registry, instance);
        return switch (instance.kind()) {
            case DUNGEON -> {
                DungeonInstanceLayout.Plan plan =
                    DungeonInstanceLayout.plan(dungeons, origin.getX(), origin.getZ(), instance.seed());
                yield plan == null ? null
                    : DungeonInstanceJob.build(dungeons, instance, bounds(dungeons, instance), plan, built);
            }
            case TALUS_ARENA, TOWER_SKELETON_ARENA, MAGNUS_ARENA, COG_SENTINEL_ARENA -> {
                ArenaInstanceLayout.Plan plan = ArenaInstanceLayout.plan(dungeons, origin, instance.kind());
                yield plan == null ? null
                    : DungeonInstanceJob.buildArena(dungeons, instance, bounds(dungeons, instance), plan, built);
            }
        };
    }

    private static void built(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry,
                              DungeonInstance instance) {
        long now = server.overworld().getGameTime();
        long opensAt = Math.max(now, instance.riftOpensAt());
        instance.setState(DungeonInstance.State.OPEN);
        instance.openRift(opensAt, opensAt + RIFT_OPEN_TICKS);
        registry.setDirty();
        AreaWard.seal(DungeonDimension.KEY, wardId(instance), wardBox(instance));
        HOLDING.add(instance.id());
        DungeonRifts.broadcast(server, instance.entrance().dimension());
        DungeonRifts.broadcast(server, DungeonDimension.KEY);
        DungeonSkies.broadcast(server);
        ServerLevel level = server.getLevel(instance.entrance().dimension());
        if (level != null && instance.entrance().stone() == null) {
            Vec3 center = instance.entrance().center();
            level.playSound(null, center.x, center.y, center.z, HexwrightSoundEvents.ruinedPortalFlare(),
                SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {} built; its rift holds until {}",
            instance.id(), instance.riftClosesAt());
    }

    private static void cleared(DungeonInstanceRegistry registry, DungeonInstance instance) {
        registry.remove(instance);
        NOT_BEFORE.remove(instance.id());
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {} cleared; cell {} is free",
            instance.id(), instance.cell());
    }

    private static void failed(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry,
                               DungeonInstance instance) {
        if (instance.state() == DungeonInstance.State.CLEARING) {
            NOT_BEFORE.put(instance.id(), server.getTickCount() + CLEAR_RETRY_TICKS);
            return;
        }
        ServerPlayer opener = server.getPlayerList().getPlayer(instance.opener());
        if (opener != null) {
            opener.displayClientMessage(Component.translatable("message.hexwright.dungeon.failed")
                .withStyle(ChatFormatting.RED), false);
        }
        tearDown(server, dungeons, registry, instance, "its build failed");
    }

    static void tearDown(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry,
                         DungeonInstance instance, String reason) {
        boolean riftHeld = instance.entranceRift(server.overworld().getGameTime()) != null;
        if (job != null && job.instance() == instance) {
            job.release();
            job = null;
        }
        instance.setState(DungeonInstance.State.CLEARING);
        for (UUID player : instance.offlineSince().keySet()) {
            registry.strand(player, instance.entrance());
        }
        instance.offlineSince().clear();
        registry.setDirty();
        AreaWard.unseal(wardId(instance));
        VaultManager.closeDoorsWithin(server, DungeonDimension.KEY, AABB.of(bounds(dungeons, instance)),
            "dungeon instance " + instance.id() + " torn down");
        evacuate(server, dungeons, instance);
        EMPTY.remove(instance.id());
        HOLDING.remove(instance.id());
        DungeonRifts.broadcast(server, DungeonDimension.KEY);
        DungeonSkies.broadcast(server);
        if (riftHeld) {
            DungeonRifts.broadcast(server, instance.entrance().dimension());
        }
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {} torn down: {}", instance.id(), reason);
    }

    private static void evacuate(MinecraftServer server, ServerLevel dungeons, DungeonInstance instance) {
        BoundingBox bounds = bounds(dungeons, instance);
        for (ServerPlayer player : List.copyOf(dungeons.players())) {
            if (bounds.isInside(player.blockPosition())) {
                putDown(player, landing(server, instance.entrance(), player));
                player.displayClientMessage(Component.translatable("message.hexwright.dungeon.collapsed"), false);
            }
        }
    }

    private static void onStarted(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (DungeonInstance instance : DungeonInstanceRegistry.get(server).all()) {
            if (instance.state() != DungeonInstance.State.OPEN) {
                continue;
            }
            AreaWard.seal(DungeonDimension.KEY, wardId(instance), wardBox(instance));
            if (instance.riftHolds(now)) {
                HOLDING.add(instance.id());
            }
        }
    }

    private static void tickRift(MinecraftServer server, DungeonInstance instance, long now) {
        if (instance.riftHolds(now)) {
            if (now == instance.riftOpensAt()) {
                ServerLevel level = server.getLevel(instance.entrance().dimension());
                if (level != null) {
                    announce(level, instance.entrance().center(), Component.translatable(
                        entranceMessage(instance, "open"), duration(RIFT_OPEN_TICKS)));
                }
            }
            if (instance.riftClosesAt() - now == RIFT_WARNING_TICKS) {
                ServerLevel level = server.getLevel(instance.entrance().dimension());
                if (level != null) {
                    announce(level, instance.entrance().center(), Component.translatable(
                        entranceMessage(instance, "closing"), duration(RIFT_WARNING_TICKS)));
                }
            }
            return;
        }
        if (HOLDING.remove(instance.id())) {
            DungeonRifts.broadcast(server, instance.entrance().dimension());
            if (instance.entrance().stone() != null) {
                DungeonRifts.broadcast(server, DungeonDimension.KEY);
            }
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {}'s rift has closed", instance.id());
        }
    }

    private static void tickSeal(ServerLevel dungeons, DungeonInstance instance, long now) {
        DungeonSafeRoom room = instance.safeRoom();
        if (!dungeons.hasChunkAt(room.sealAnchor())) {
            return;
        }
        boolean shut = instance.riftHolds(now);
        if (room.seal(dungeons, shut) && !shut) {
            AABB interior = room.interior();
            for (ServerPlayer player : dungeons.getEntitiesOfClass(ServerPlayer.class, interior)) {
                player.displayClientMessage(Component.translatable("message.hexwright.dungeon.way_on_open")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {}'s safe room unsealed", instance.id());
        }
    }

    private static String entranceMessage(DungeonInstance instance, String event) {
        return "message.hexwright.dungeon." + (instance.entrance().stone() != null ? "stone_" : "rift_") + event;
    }


    private static void crossInto(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry,
                                  DungeonInstance instance, long now) {
        DungeonRift rift = instance.entranceRift(now);
        ServerLevel level = server.getLevel(instance.entrance().dimension());
        if (rift == null || level == null) {
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, rift.triggerBox())) {
            if (player.isSpectator() || !player.isAlive() || crossedRecently(player, now)) {
                continue;
            }
            CROSSED.put(player.getUUID(), now);
            if (instance.departed().contains(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.hexwright.dungeon.left_behind"), true);
                continue;
            }
            admit(dungeons, registry, instance, player);
        }
    }

    private static void crossOut(MinecraftServer server, ServerLevel dungeons, DungeonInstance instance, long now) {
        if (instance.windowHolds(now) || instance.windowForming(now)) {
            return;
        }
        DungeonRift exit = instance.safeRoom().exit(instance.id(), 0L);
        for (ServerPlayer player : dungeons.getEntitiesOfClass(ServerPlayer.class, exit.triggerBox())) {
            if (player.isSpectator() || !player.isAlive() || crossedRecently(player, now)) {
                continue;
            }
            CROSSED.put(player.getUUID(), now);
            putDown(player, landing(server, instance.entrance(), player));
            player.level().playSound(null, player.blockPosition(), HexwrightSoundEvents.ruinedPortalTeleport(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.hexwright.dungeon.left"), false);
        }
    }

    private static boolean crossedRecently(ServerPlayer player, long now) {
        Long last = CROSSED.get(player.getUUID());
        return last != null && now - last < CROSSING_COOLDOWN_TICKS;
    }

    static void admit(ServerLevel dungeons, DungeonInstanceRegistry registry, DungeonInstance instance,
                      ServerPlayer player) {
        toStart(dungeons, instance, player);
        dungeons.playSound(null, player.blockPosition(), HexwrightSoundEvents.ruinedPortalTeleport(),
            SoundSource.PLAYERS, 1.0F, 1.0F);
        enlist(registry, instance, player);
    }

    private static void enlist(DungeonInstanceRegistry registry, DungeonInstance instance, ServerPlayer player) {
        if (!instance.members().add(player.getUUID())) {
            return;
        }
        registry.setDirty();
        String welcome = instance.entrance().stone() != null ? "entered_window" : "entered";
        player.displayClientMessage(Component.translatable("message.hexwright.dungeon." + welcome)
            .withStyle(ChatFormatting.DARK_PURPLE), false);
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} joined instance {}",
            player.getGameProfile().getName(), instance.id());
    }


    private static void tickOccupancy(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry,
                                      DungeonInstance instance, long now, Set<UUID> inVaults) {
        BoundingBox bounds = bounds(dungeons, instance);
        Set<UUID> inside = new HashSet<>();
        for (UUID member : instance.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player == null || instance.departed().contains(member)) {
                continue;
            }
            boolean here = player.level() == dungeons && bounds.isInside(player.blockPosition());
            if (here || inVaults.contains(member)) {
                inside.add(member);
                if (instance.offlineSince().remove(member) != null) {
                    registry.setDirty();
                }
            }
        }
        boolean riftHolds = instance.riftHolds(now);
        if (!riftHolds) {
            for (UUID member : instance.members()) {
                if (!inside.contains(member) && server.getPlayerList().getPlayer(member) != null
                    && instance.departed().add(member)) {
                    registry.setDirty();
                    HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} left instance {}", member, instance.id());
                }
            }
        }

        boolean waitedFor = false;
        for (long since : instance.offlineSince().values()) {
            waitedFor |= now - since < OFFLINE_GRACE_TICKS;
        }
        if (!inside.isEmpty() || waitedFor || riftHolds) {
            EMPTY.remove(instance.id());
            return;
        }
        if (EMPTY.merge(instance.id(), 1, Integer::sum) > EMPTY_GRACE_TICKS) {
            tearDown(server, dungeons, registry, instance, "everyone has left");
        }
    }

    private static Map<Integer, Set<UUID>> vaultOccupants(MinecraftServer server) {
        Map<Integer, Set<UUID>> occupants = new HashMap<>();
        ServerLevel vaults = VaultDimension.level(server);
        if (vaults == null) {
            return occupants;
        }
        for (ServerPlayer player : vaults.players()) {
            VaultRecord vault = VaultRegistry.get(server).byPosition(player.position());
            if (vault == null) {
                continue;
            }
            for (VaultPortalSession door : VaultManager.sessionsOf(vault.id())) {
                DungeonInstance instance = door.outsideDimension().equals(DungeonDimension.KEY)
                    ? at(server, door.outsideWindow().center())
                    : null;
                if (instance != null) {
                    occupants.computeIfAbsent(instance.id(), id -> new HashSet<>()).add(player.getUUID());
                }
            }
        }
        return occupants;
    }

    private static void contain(MinecraftServer server, ServerLevel dungeons, DungeonInstanceRegistry registry) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : List.copyOf(dungeons.players())) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            DungeonInstance instance = at(server, player.position());
            if (instance != null && cameThroughWindow(instance, player, now)) {
                enlist(registry, instance, player);
            }
            if (instance == null || !belongs(instance, player.getUUID())) {
                if (!player.isCreative()) {
                    putDown(player, home(server, registry, player));
                    player.displayClientMessage(Component.translatable("message.hexwright.dungeon.not_yours"), false);
                }
                continue;
            }
            boolean fallen = player.getY() < instance.extent().minY() - VOID_DROP;
            if (player.isCreative()) {
                if (fallen) {
                    toStart(dungeons, instance, player);
                    player.displayClientMessage(Component.translatable("message.hexwright.dungeon.returned"), true);
                }
                continue;
            }
            if (instance.riftHolds(now) && !instance.safeRoom().interior().contains(player.position())) {
                eject(server, registry, instance, player);
                continue;
            }
            if (fallen) {
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                continue;
            }
            BoundingBox bounds = bounds(dungeons, instance);
            boolean strayed = player.getX() < bounds.minX() || player.getX() > bounds.maxX() + 1
                || player.getZ() < bounds.minZ() || player.getZ() > bounds.maxZ() + 1;
            if (strayed) {
                toStart(dungeons, instance, player);
                player.displayClientMessage(Component.translatable("message.hexwright.dungeon.returned"), true);
            }
        }
    }

    private static void eject(MinecraftServer server, DungeonInstanceRegistry registry, DungeonInstance instance,
                              ServerPlayer player) {
        if (instance.departed().add(player.getUUID())) {
            registry.setDirty();
        }
        putDown(player, landing(server, instance.entrance(), player));
        player.level().playSound(null, player.blockPosition(), HexwrightSoundEvents.ruinedPortalTeleport(),
            SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.hexwright.dungeon.escaped")
            .withStyle(ChatFormatting.DARK_PURPLE), false);
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} got out of instance {}'s safe room early",
            player.getGameProfile().getName(), instance.id());
    }

    private static boolean cameThroughWindow(DungeonInstance instance, ServerPlayer player, long now) {
        return instance.windowHolds(now) && !instance.members().contains(player.getUUID())
            && instance.safeRoom().interior().contains(player.position());
    }

    private static boolean belongs(DungeonInstance instance, UUID player) {
        return instance.state() == DungeonInstance.State.OPEN && instance.members().contains(player)
            && !instance.departed().contains(player);
    }

    private static void wardSafeRooms(ServerLevel dungeons, DungeonInstanceRegistry registry) {
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != DungeonInstance.State.OPEN) {
                continue;
            }
            AABB interior = instance.safeRoom().interior();
            if (!dungeons.hasChunkAt(BlockPos.containing(interior.getCenter()))) {
                continue;
            }
            BlockPos foot = instance.shaftFoot();
            for (Mob mob : dungeons.getEntitiesOfClass(Mob.class, interior,
                mob -> mob.getType().getCategory() == MobCategory.MONSTER)) {
                mob.teleportTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5);
            }
        }
    }


    private static void onJoin(ServerPlayer player) {
        if (!DungeonDimension.isDungeonLevel(player.level())) {
            return;
        }
        MinecraftServer server = player.server;
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        DungeonInstance instance = at(server, player.position());
        if (instance != null && belongs(instance, player.getUUID())) {
            if (instance.offlineSince().remove(player.getUUID()) != null) {
                registry.setDirty();
            }
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        putDown(player, home(server, registry, player));
        player.displayClientMessage(Component.translatable("message.hexwright.dungeon.collapsed"), false);
    }

    public static void onLeaving(ServerPlayer player) {
        CROSSED.remove(player.getUUID());
        if (!DungeonDimension.isDungeonLevel(player.level())) {
            return;
        }
        DungeonInstance instance = at(player.server, player.position());
        if (instance != null && belongs(instance, player.getUUID())) {
            instance.offlineSince().put(player.getUUID(), player.server.overworld().getGameTime());
            DungeonInstanceRegistry.get(player.server).setDirty();
        }
    }

    public static void onDying(ServerPlayer player) {
        if (!DungeonDimension.isDungeonLevel(player.level())) {
            return;
        }
        DungeonInstance instance = at(player.server, player.position());
        if (instance != null) {
            layOut(player, instance, instance.entrance());
        }
    }

    private static void layOut(ServerPlayer player, @Nullable DungeonInstance instance,
                               DungeonInstance.Entrance entrance) {
        if (instance != null && instance.departed().add(player.getUUID())) {
            DungeonInstanceRegistry.get(player.server).setDirty();
        }
        putDown(player, landing(player.server, entrance, player, BODY_ASIDE));
        player.displayClientMessage(Component.translatable("message.hexwright.dungeon.died")
            .withStyle(ChatFormatting.DARK_PURPLE), false);
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} died in instance {}",
            player.getGameProfile().getName(), instance == null ? "(gone)" : instance.id());
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Player)) {
            return true;
        }
        if (!(entity.level() instanceof ServerLevel level) || !DungeonDimension.isDungeonLevel(level)) {
            return true;
        }
        if (!(SpellCaster.attackerOf(source, level) instanceof Player attacker) || attacker == entity) {
            return true;
        }
        attacker.displayClientMessage(Component.translatable("message.hexwright.dungeon.no_pvp"), true);
        return false;
    }


    private record Landing(ServerLevel level, Vec3 pos, float yaw) {
    }

    private static void toStart(ServerLevel dungeons, DungeonInstance instance, ServerPlayer player) {
        Vec3 arrival = instance.safeRoom().arrival();
        putDown(player, new Landing(dungeons, arrival, DungeonSafeRoom.ARRIVAL_YAW));
    }

    private static void putDown(ServerPlayer player, Landing landing) {
        landing.level().getChunk(BlockPos.containing(landing.pos()));
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(landing.level(), landing.pos().x, landing.pos().y, landing.pos().z, landing.yaw(), 0.0F);
        player.resetFallDistance();
    }

    private static Landing landing(MinecraftServer server, DungeonInstance.Entrance entrance, ServerPlayer player) {
        return landing(server, entrance, player, 0);
    }

    private static Landing landing(MinecraftServer server, DungeonInstance.Entrance entrance, ServerPlayer player,
                                   int aside) {
        ServerLevel level = server.getLevel(entrance.dimension());
        if (level == null) {
            return spawn(server, player);
        }
        BlockPos stand = (entrance.stone() != null
            ? entrance.stone().relative(entrance.facing().getOpposite(), TravelStones.LANDING_DISTANCE)
            : BlockPos.containing(entrance.center().x, entrance.center().y - DungeonRift.HALF_LENGTH,
                entrance.center().z).relative(entrance.facing().getOpposite(), RIFT_DISTANCE))
            .relative(entrance.facing().getClockWise(), aside);
        level.getChunk(stand);
        return new Landing(level, Vec3.atBottomCenterOf(standingSpot(level, stand)), entrance.facing().toYRot());
    }

    private static BlockPos standingSpot(ServerLevel level, BlockPos column) {
        for (int dy = 2; dy >= -6; dy--) {
            BlockPos feet = column.above(dy);
            if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && clear(level, feet) && clear(level, feet.above())) {
                return feet;
            }
        }
        return column;
    }

    private static Landing home(MinecraftServer server, DungeonInstanceRegistry registry, ServerPlayer player) {
        DungeonInstance.Entrance stranded = registry.takeStranded(player.getUUID());
        return stranded != null ? landing(server, stranded, player) : spawn(server, player);
    }

    private static Landing spawn(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = server.getLevel(player.getRespawnDimension());
        BlockPos respawn = player.getRespawnPosition();
        if (level != null && respawn != null && !DungeonDimension.isDungeonLevel(level)) {
            Optional<Vec3> spot = Player.findRespawnPositionAndUseSpawnBlock(level, respawn,
                player.getRespawnAngle(), player.isRespawnForced(), true);
            if (spot.isPresent()) {
                return new Landing(level, spot.get(), player.getRespawnAngle());
            }
        }
        ServerLevel overworld = server.overworld();
        return new Landing(overworld, Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), player.getYRot());
    }


    private static void announce(ServerLevel level, Vec3 center, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) < ANNOUNCE_RANGE * ANNOUNCE_RANGE) {
                player.displayClientMessage(message, false);
            }
        }
    }

    public static String duration(long ticks) {
        long seconds = ticks / 20L;
        return seconds / 60L + ":" + String.format("%02d", seconds % 60L);
    }

    private static UUID wardId(DungeonInstance instance) {
        return new UUID(WARD_ID, instance.id());
    }

    private static BoundingBox wardBox(DungeonInstance instance) {
        return instance.kind() == DungeonInstance.Kind.DUNGEON ? instance.safeRoom().box() : instance.extent();
    }
}

package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VaultFrames {

    public static final int MAX_FRAMES = 2;

    private static final List<int[]> RING = new ArrayList<>();

    static {
        for (int column = -1; column <= 2; column++) {
            for (int row = -1; row <= 3; row++) {
                if (column == -1 || column == 2 || row == -1 || row == 3) {
                    RING.add(new int[]{column, row});
                }
            }
        }
    }

    private static final Set<BlockPos> REMOVED = new HashSet<>();

    private VaultFrames() {
    }


    static List<BlockPos> ring(BlockPos anchor, Direction facing) {
        Vec3i d = facing.getNormal();
        int ux = -d.getZ();
        int uz = d.getX();
        List<BlockPos> blocks = new ArrayList<>(RING.size());
        for (int[] cell : RING) {
            blocks.add(anchor.offset(ux * cell[0], cell[1], uz * cell[0]));
        }
        return blocks;
    }

    private static boolean ringIntact(Level level, BlockPos anchor, Direction facing) {
        for (BlockPos pos : ring(anchor, facing)) {
            if (!level.getBlockState(pos).is(HexwrightBlocks.BINDSTONE_BLOCK)) {
                return false;
            }
        }
        return true;
    }

    static boolean ringIntact(Level level, VaultRecord.Frame frame) {
        return ringIntact(level, frame.anchor(), frame.facing());
    }

    static boolean openingClear(ServerLevel level, VaultRecord.Frame frame) {
        return VaultPortalPlacement.paneClear(level, frame.anchor(), frame.facing());
    }

    private static @Nullable Candidate find(ServerLevel level, BlockPos clicked, Vec3 viewer) {
        Candidate best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3i d = facing.getNormal();
            int ux = -d.getZ();
            int uz = d.getX();
            for (int[] cell : RING) {
                BlockPos anchor = clicked.offset(-ux * cell[0], -cell[1], -uz * cell[0]);
                PortalWindow window = VaultPortalPlacement.paneAt(anchor, facing);
                if (window.signedDistance(viewer) <= 0.0
                    || !ringIntact(level, anchor, facing)
                    || !VaultPortalPlacement.paneClear(level, anchor, facing)) {
                    continue;
                }
                double distSq = window.center().distanceToSqr(viewer);
                if (distSq < bestDistSq) {
                    best = new Candidate(anchor, facing, window);
                    bestDistSq = distSq;
                }
            }
        }
        return best;
    }

    private record Candidate(BlockPos anchor, Direction facing, PortalWindow window) {
    }


    static boolean bind(ServerPlayer player, ItemStack blank, BlockPos clicked) {
        MinecraftServer server = player.server;
        VaultRegistry registry = VaultRegistry.get(server);
        VaultRecord record = registry.byRoom(player.position());
        if (record == null) {
            return false;
        }
        if (!record.owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("hexwright.vault.frame_owner_only"), true);
            return false;
        }
        if (VaultKeyItem.grade(blank) != record.grade()) {
            player.displayClientMessage(Component.translatable("hexwright.vault.frame_wrong_grade",
                Component.translatable(record.grade().translationKey())), true);
            return false;
        }
        Candidate candidate = find(player.serverLevel(), clicked, player.getEyePosition());
        if (candidate == null || !insideRoom(record, candidate)) {
            player.displayClientMessage(Component.translatable("hexwright.vault.frame_invalid"), true);
            return false;
        }

        VaultRecord.Frame frame = registeredAt(record, candidate.window());
        if (frame == null) {
            if (overlapsAnotherOpening(record, candidate)) {
                player.displayClientMessage(Component.translatable("hexwright.vault.frame_overlaps"), true);
                return false;
            }
            if (record.frames().size() >= MAX_FRAMES) {
                player.displayClientMessage(
                    Component.translatable("hexwright.vault.frame_limit", MAX_FRAMES), true);
                return false;
            }
            frame = record.addFrame(candidate.anchor(), candidate.facing());
            registry.setDirty();
        }
        VaultKeyItem.cutToFrame(blank, record, frame.id());
        player.sendSystemMessage(Component.translatable("hexwright.vault.frame_bound", record.id()));
        return true;
    }

    static boolean hangMainDoor(ServerPlayer player, int vaultId, BlockPos clicked) {
        MinecraftServer server = player.server;
        VaultRecord record = VaultRegistry.get(server).byRoom(player.position());
        if (record == null || record.id() != vaultId) {
            player.displayClientMessage(Component.translatable("hexwright.vault.main_door_wrong_vault"), true);
            return false;
        }
        if (!record.owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("hexwright.vault.main_door_owner_only"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        VaultRecord.Frame current = mainDoorFrame(record);

        if (level.getBlockState(clicked).is(HexwrightBlocks.VAULT_FRAME_BLOCK)) {
            if (ring(current.anchor(), current.facing()).contains(clicked)) {
                player.displayClientMessage(Component.translatable("hexwright.vault.main_door_already"), true);
            }
            return false;
        }

        Candidate candidate = find(level, clicked, player.getEyePosition());
        if (candidate == null || !insideRoom(record, candidate)) {
            player.displayClientMessage(Component.translatable("hexwright.vault.frame_invalid"), true);
            return false;
        }
        if (registeredAt(record, candidate.window()) != null) {
            player.displayClientMessage(Component.translatable("hexwright.vault.main_door_frame_taken"), true);
            return false;
        }
        if (sharesStoneWithFrame(record, candidate)) {
            player.displayClientMessage(Component.translatable("hexwright.vault.main_door_frame_shared"), true);
            return false;
        }
        if (overlapsAnotherOpening(record, candidate)) {
            player.displayClientMessage(Component.translatable("hexwright.vault.frame_overlaps"), true);
            return false;
        }

        boolean wasArch = record.mainDoorFrame() == null;
        VaultRecord.Frame moved = new VaultRecord.Frame(VaultRecord.MAIN_DOOR, candidate.anchor(), candidate.facing());
        VaultManager.hangMainDoor(server, record, moved);
        List<BlockPos> movedRing = ring(moved.anchor(), moved.facing());
        for (BlockPos pos : movedRing) {
            level.setBlock(pos, HexwrightBlocks.VAULT_FRAME_BLOCK.defaultBlockState(), 3);
        }
        if (wasArch) {
            VaultRooms.removeArch(level, record);
        } else {
            for (BlockPos pos : ring(current.anchor(), current.facing())) {
                if (!movedRing.contains(pos)) {
                    level.destroyBlock(pos, false);
                }
            }
        }
        player.displayClientMessage(Component.translatable("hexwright.vault.main_door_moved", record.id()), true);
        return true;
    }

    private static VaultRecord.Frame mainDoorFrame(VaultRecord record) {
        VaultRecord.Frame moved = record.mainDoorFrame();
        if (moved != null) {
            return moved;
        }
        PortalWindow arch = VaultRooms.vaultWindow(record);
        return new VaultRecord.Frame(VaultRecord.MAIN_DOOR,
            VaultPortalPlacement.anchorOf(arch), VaultPortalPlacement.facingOf(arch));
    }

    private static boolean sharesStoneWithFrame(VaultRecord record, Candidate candidate) {
        List<BlockPos> stone = ring(candidate.anchor(), candidate.facing());
        for (VaultRecord.Frame frame : record.frames()) {
            for (BlockPos pos : ring(frame.anchor(), frame.facing())) {
                if (stone.contains(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean insideRoom(VaultRecord record, Candidate candidate) {
        AABB habitable = VaultRooms.habitableBounds(record);
        for (BlockPos pos : ring(candidate.anchor(), candidate.facing())) {
            if (!habitable.contains(Vec3.atCenterOf(pos))) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable VaultRecord.Frame registeredAt(VaultRecord record, PortalWindow window) {
        for (VaultRecord.Frame frame : record.frames()) {
            if (frame.window().center().distanceToSqr(window.center()) < 0.01) {
                return frame;
            }
        }
        return null;
    }

    private static boolean overlapsAnotherOpening(VaultRecord record, Candidate candidate) {
        Set<BlockPos> taken = new HashSet<>();
        List<VaultRecord.Frame> rings = new ArrayList<>(record.frames());
        rings.add(mainDoorFrame(record));
        for (VaultRecord.Frame frame : rings) {
            taken.addAll(VaultPortalPlacement.paneBlocks(frame.anchor(), frame.facing()));
        }
        for (BlockPos pos : VaultPortalPlacement.paneBlocks(candidate.anchor(), candidate.facing())) {
            if (taken.contains(pos)) {
                return true;
            }
        }
        return false;
    }


    public static void noteRemoved(Level level, BlockPos pos) {
        if (!level.isClientSide && VaultDimension.isVaultLevel(level)) {
            REMOVED.add(pos.immutable());
        }
    }

    static void tick(MinecraftServer server) {
        if (REMOVED.isEmpty()) {
            return;
        }
        List<BlockPos> removed = List.copyOf(REMOVED);
        REMOVED.clear();
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null) {
            return;
        }
        VaultRegistry registry = VaultRegistry.get(server);
        for (BlockPos pos : removed) {
            VaultRecord record = registry.byRoom(Vec3.atCenterOf(pos));
            if (record == null) {
                continue;
            }
            for (VaultRecord.Frame frame : List.copyOf(record.frames())) {
                if (ring(frame.anchor(), frame.facing()).contains(pos) && !ringIntact(vaultLevel, frame)) {
                    VaultManager.breakFrame(server, record, frame.id());
                }
            }
        }
    }
}

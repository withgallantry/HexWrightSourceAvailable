package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VaultRooms {

    public static final int TEMPLATE_VERSION = 5;

    public static final int GRID_WIDTH = 128;

    public static final int CELL_SPACING = 1024;

    public static final int PORTAL_INSET = 1;

    private static final double HABITABLE_SLACK = 1.0;

    private static final int ARCH_WIDTH = 4;

    public enum Layout {
        CHAMBER(8, 6, 5, 24),
        HALL(12, 12, 5, 24),
        GALLERY(18, 18, 7, 24),
        PLANE(0, 0, 0, VaultGrounds.GROUND_Y),
        ESTATE(0, 0, 0, VaultGrounds.GROUND_Y);

        private final int width;
        private final int depth;
        private final int interiorHeight;
        private final int floorY;

        Layout(int width, int depth, int interiorHeight, int floorY) {
            this.width = width;
            this.depth = depth;
            this.interiorHeight = interiorHeight;
            this.floorY = floorY;
        }

        public boolean isEstate() {
            return this == ESTATE;
        }

        public boolean isOpenAir() {
            return this == ESTATE || this == PLANE;
        }

        public int interiorHeight() {
            return interiorHeight;
        }

        public int interiorWidth() {
            return width - 2;
        }

        public int interiorDepth() {
            return depth - 2;
        }
    }

    public record Plan(int width, int depth, int interiorHeight, int floorY, boolean openAir) {

        public int chunkSpanX() {
            return (width + 15) / 16;
        }

        public int chunkSpanZ() {
            return (depth + 15) / 16;
        }

        public int offsetX() {
            return (chunkSpanX() * 16 - width) / 2;
        }

        public int offsetZ() {
            return (chunkSpanZ() * 16 - depth) / 2;
        }
    }

    public static Layout layoutFor(PocketCasterData.Quality grade, boolean artifact) {
        if (artifact) {
            return Layout.ESTATE;
        }
        return switch (grade) {
            case CRUDE, SOUND -> Layout.CHAMBER;
            case FINE -> Layout.HALL;
            case EXQUISITE -> Layout.GALLERY;
            case MASTERWORK -> Layout.PLANE;
        };
    }

    public static Layout layoutOf(VaultRecord record) {
        return layoutFor(record.grade(), record.artifact());
    }

    public static Plan planOf(VaultRecord record) {
        Layout layout = layoutOf(record);
        if (layout == Layout.ESTATE) {
            VaultBuild build = VaultBuilds.byId(record.build());
            int size = build == null ? VaultBuilds.fallbackGroundsSize() : build.groundsSize();
            return new Plan(size, size, 0, VaultGrounds.GROUND_Y, true);
        }
        if (layout == Layout.PLANE) {
            return new Plan(VaultGrounds.PLANE_SIZE, VaultGrounds.PLANE_SIZE, 0,
                VaultGrounds.GROUND_Y, true);
        }
        return new Plan(layout.width, layout.depth, layout.interiorHeight, layout.floorY, false);
    }

    private VaultRooms() {
    }


    public static BlockPos cellOrigin(int vaultId) {
        int cellX = vaultId % GRID_WIDTH;
        int cellZ = vaultId / GRID_WIDTH;
        return new BlockPos(cellX * CELL_SPACING, 0, cellZ * CELL_SPACING);
    }

    public static AABB cellBounds(VaultRecord record) {
        BlockPos origin = record.origin();
        return new AABB(origin.getX(), 0, origin.getZ(),
            origin.getX() + CELL_SPACING, VaultDimension.HEIGHT, origin.getZ() + CELL_SPACING);
    }

    public static BlockPos roomOrigin(VaultRecord record) {
        Plan plan = planOf(record);
        return record.origin().offset(plan.offsetX(), 0, plan.offsetZ());
    }

    public static ChunkPos roomChunk(VaultRecord record) {
        Plan plan = planOf(record);
        int firstX = record.origin().getX() >> 4;
        int firstZ = record.origin().getZ() >> 4;
        return new ChunkPos(firstX + (plan.chunkSpanX() - 1) / 2, firstZ + (plan.chunkSpanZ() - 1) / 2);
    }

    public static List<ChunkPos> roomChunks(VaultRecord record) {
        Plan plan = planOf(record);
        int firstX = record.origin().getX() >> 4;
        int firstZ = record.origin().getZ() >> 4;
        List<ChunkPos> chunks = new ArrayList<>(plan.chunkSpanX() * plan.chunkSpanZ());
        for (int dx = 0; dx < plan.chunkSpanX(); dx++) {
            for (int dz = 0; dz < plan.chunkSpanZ(); dz++) {
                chunks.add(new ChunkPos(firstX + dx, firstZ + dz));
            }
        }
        return chunks;
    }

    public static List<ChunkPos> viewChunks(VaultRecord record) {
        Plan plan = planOf(record);
        int firstX = (record.origin().getX() >> 4) - 1;
        int firstZ = (record.origin().getZ() >> 4) - 1;
        int spanX = plan.chunkSpanX() + 2;
        int spanZ = plan.chunkSpanZ() + 2;
        List<ChunkPos> chunks = new ArrayList<>(spanX * spanZ);
        for (int dx = 0; dx < spanX; dx++) {
            for (int dz = 0; dz < spanZ; dz++) {
                chunks.add(new ChunkPos(firstX + dx, firstZ + dz));
            }
        }
        ChunkPos centre = roomChunk(record);
        chunks.sort(Comparator.comparingInt(chunk ->
            (chunk.x - centre.x) * (chunk.x - centre.x) + (chunk.z - centre.z) * (chunk.z - centre.z)));
        return chunks;
    }

    public static int roomTicketRadius(VaultRecord record) {
        Plan plan = planOf(record);
        int halfSpanX = (plan.chunkSpanX() - 1) - (plan.chunkSpanX() - 1) / 2;
        int halfSpanZ = (plan.chunkSpanZ() - 1) - (plan.chunkSpanZ() - 1) / 2;
        return Math.max(halfSpanX, halfSpanZ) + 2;
    }

    public static AABB roomBounds(VaultRecord record) {
        Plan plan = planOf(record);
        BlockPos min = roomOrigin(record);
        return new AABB(min.getX(), 0, min.getZ(),
            min.getX() + plan.width(), VaultDimension.HEIGHT, min.getZ() + plan.depth());
    }

    public static AABB habitableBounds(VaultRecord record) {
        Plan plan = planOf(record);
        BlockPos min = roomOrigin(record);
        double lowY = (plan.openAir() ? VaultGrounds.BASE_Y : plan.floorY()) - 1;
        double highY = plan.openAir()
            ? VaultDimension.HEIGHT
            : plan.floorY() + plan.interiorHeight() + 2;
        return new AABB(
            min.getX() - HABITABLE_SLACK, lowY, min.getZ() - HABITABLE_SLACK,
            min.getX() + plan.width() + HABITABLE_SLACK, highY,
            min.getZ() + plan.depth() + HABITABLE_SLACK);
    }


    public static PortalWindow vaultWindow(VaultRecord record) {
        Plan plan = planOf(record);
        BlockPos min = roomOrigin(record);
        int baseX = min.getX() + archOffsetX(plan) + 1;
        int baseY = plan.floorY() + 1;
        int z = min.getZ() + PORTAL_INSET;
        PortalWindow window = PortalWindow.fromCorners(
            Vec3.atCenterOf(new BlockPos(baseX, baseY, z)),
            Vec3.atCenterOf(new BlockPos(baseX + 1, baseY + 2, z)));
        if (window == null) {
            throw new IllegalStateException("Vault window corners degenerate for vault " + record.id());
        }
        return window;
    }

    static int archOffsetX(Plan plan) {
        return (plan.width() - ARCH_WIDTH) / 2;
    }

    public static Vec3 interiorArrival(VaultRecord record) {
        Plan plan = planOf(record);
        BlockPos min = roomOrigin(record);
        if (plan.openAir()) {
            return new Vec3(min.getX() + plan.width() / 2.0,
                plan.floorY() + 1,
                min.getZ() + PORTAL_INSET + 3.5);
        }
        return new Vec3(min.getX() + plan.width() / 2.0,
            plan.floorY() + 1,
            min.getZ() + plan.depth() / 2.0);
    }

    public static BlockPos intactProbe(VaultRecord record) {
        Plan plan = planOf(record);
        BlockPos min = roomOrigin(record);
        int y = plan.openAir() ? VaultGrounds.BASE_Y : plan.floorY();
        return new BlockPos(min.getX() + plan.width() / 2, y, min.getZ() + plan.depth() / 2);
    }


    public static void generate(ServerLevel vaultLevel, VaultRecord record) {
        Layout layout = layoutOf(record);
        if (layout == Layout.ESTATE) {
            VaultGrounds.generate(vaultLevel, record);
            return;
        }
        if (layout == Layout.PLANE) {
            VaultGrounds.generatePlane(vaultLevel, record);
            return;
        }
        generateRoom(vaultLevel, record, planOf(record));
    }

    static void generateArch(ServerLevel vaultLevel, BlockPos min, Plan plan) {
        BlockState frame = HexwrightBlocks.VAULT_FRAME_BLOCK.defaultBlockState();
        int frameZ = min.getZ() + PORTAL_INSET;
        int frameX0 = min.getX() + archOffsetX(plan);
        int floorY = plan.floorY();
        for (int x = frameX0; x <= frameX0 + ARCH_WIDTH - 1; x++) {
            for (int y = floorY; y <= floorY + 4; y++) {
                boolean ring = x == frameX0 || x == frameX0 + ARCH_WIDTH - 1 || y == floorY || y == floorY + 4;
                vaultLevel.setBlock(new BlockPos(x, y, frameZ),
                    ring ? frame : Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void generateRoom(ServerLevel vaultLevel, VaultRecord record, Plan plan) {
        BlockPos min = roomOrigin(record);
        int x0 = min.getX();
        int z0 = min.getZ();
        int x1 = x0 + plan.width() - 1;
        int z1 = z0 + plan.depth() - 1;
        int floorY = plan.floorY();
        int ceilingY = floorY + plan.interiorHeight() + 1;

        BlockState wall = HexwrightBlocks.VAULT_SHELL_BLOCK.defaultBlockState();
        BlockState floor = HexwrightBlocks.VAULT_FLOOR_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                vaultLevel.setBlock(new BlockPos(x, floorY, z), floor, 3);
                vaultLevel.setBlock(new BlockPos(x, ceilingY, z), wall, 3);
                for (int y = floorY + 1; y < ceilingY; y++) {
                    vaultLevel.setBlock(new BlockPos(x, y, z), edge ? wall : air, 3);
                }
            }
        }

        BlockState lantern = HexwrightBlocks.VAULT_LAMP_BLOCK.defaultBlockState();
        for (int dx : new int[]{2, plan.width() - 3}) {
            for (int dz : new int[]{2, plan.depth() - 3}) {
                vaultLevel.setBlock(new BlockPos(x0 + dx, ceilingY, z0 + dz), lantern, 3);
            }
        }

        generateArch(vaultLevel, min, plan);

        vaultLevel.setBlock(new BlockPos(x0 + plan.width() / 2 - 1, floorY + 1, z1 - 1),
            Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        vaultLevel.setBlock(new BlockPos(x1 - 1, floorY + 2, z0 + plan.depth() / 2 - 1),
            Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), 3);
    }
}

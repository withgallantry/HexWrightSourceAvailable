package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.boss.HexwrightBossEntities;
import com.bluup.hexwright.server.boss.cog.CogSentinelEntity;
import com.bluup.hexwright.server.boss.magnus.MagnusBonfireEntity;
import com.bluup.hexwright.server.boss.talus.TalusKnightEntity;
import com.bluup.hexwright.server.boss.tower.TowerSkeletonEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ArenaInstanceLayout {

    private static final int FLOOR_Y = 64;

    private static final int BRIDGE_LENGTH = 20;
    private static final int BRIDGE_HALF_WIDTH = 1;
    private static final int LANTERN_SPACING = 5;
    private static final int LANTERN_POST_HEIGHT = 2;

    private static final float BOSS_YAW = 90.0F;

    private enum Arena {
        FOREST_GLADE("arena/forest_arena", Rotation.NONE, 2,
            new BoundingBox(22, 2, 34, 70, 101, 113),
            new BlockPos(-1, 1, 85),
            new BoundingBox(21, 1, 84, 21, 5, 86),
            new BlockPos(23, 2, 85),
            new BlockPos(46, 2, 74)),
        WARDEN_SANCTUM("arena/warden_arena", Rotation.COUNTERCLOCKWISE_90, 2,
            new BoundingBox(5, 2, 5, 71, 62, 71),
            new BlockPos(38, 1, -1),
            new BoundingBox(36, 1, 0, 40, 6, 0),
            new BlockPos(38, 2, 5),
            new BlockPos(38, 2, 54)),
        COG_SANCTUM("arena/cog_sanctum", Rotation.COUNTERCLOCKWISE_90, 2,
            new BoundingBox(5, 2, 5, 71, 62, 71),
            new BlockPos(38, 1, -1),
            new BoundingBox(36, 1, 0, 40, 6, 0),
            new BlockPos(38, 2, 5),
            new BlockPos(38, 2, 38)),
        NETHER_HALL("arena/nether_hall", Rotation.NONE, 30,
            new BoundingBox(5, 30, 7, 61, 58, 40),
            new BlockPos(0, 29, 24),
            new BoundingBox(1, 29, 23, 4, 33, 25),
            new BlockPos(6, 30, 24),
            new BlockPos(34, 30, 24));

        final ResourceLocation template;
        final Rotation rotation;
        final int feet;
        final BoundingBox hall;
        final BlockPos landing;
        final BoundingBox doorway;
        final BlockPos inside;
        final BlockPos post;

        Arena(String template, Rotation rotation, int feet, BoundingBox hall, BlockPos landing, BoundingBox doorway,
              BlockPos inside, BlockPos post) {
            this.template = Hexwright.id(template);
            this.rotation = rotation;
            this.feet = feet;
            this.hall = hall;
            this.landing = landing;
            this.doorway = doorway;
            this.inside = inside;
            this.post = post;
        }

        static Arena of(DungeonInstance.Kind kind) {
            return switch (kind) {
                case TOWER_SKELETON_ARENA -> WARDEN_SANCTUM;
                case MAGNUS_ARENA -> NETHER_HALL;
                case COG_SENTINEL_ARENA -> COG_SANCTUM;
                default -> FOREST_GLADE;
            };
        }
    }

    public record Plan(ResourceLocation template, Rotation rotation, BlockPos origin, BoundingBox box,
                       BlockPos threshold, BlockPos landing, BoundingBox doorway, DungeonSafeRoom room, AABB hall,
                       BlockPos inside, BlockPos post, BoundingBox extent) {

        StructurePlaceSettings settings() {
            return new StructurePlaceSettings().setRotation(this.rotation);
        }

        public BlockPos bridgeFoot() {
            return this.inside;
        }
    }

    private ArenaInstanceLayout() {
    }

    public static @Nullable Plan plan(ServerLevel level, BlockPos origin, DungeonInstance.Kind kind) {
        Arena arena = Arena.of(kind);
        StructureTemplate template = level.getStructureManager().get(arena.template).orElse(null);
        if (template == null || template.getSize().getX() == 0) {
            Hexwright.LOGGER.error("The arena template {} is missing", arena.template);
            return null;
        }
        BlockPos middle = turned(arena, arena.post.atY(arena.feet));
        BlockPos corner = new BlockPos(origin.getX() - middle.getX(), FLOOR_Y - arena.feet,
            origin.getZ() - middle.getZ());
        BlockPos landing = corner.offset(turned(arena, arena.landing));
        BlockPos threshold = landing.offset(-BRIDGE_LENGTH, 0, 0);
        DungeonSafeRoom room = DungeonSafeRoom.openingEastOnto(threshold);
        BoundingBox doorway = placed(arena, corner, arena.doorway);
        AABB hall = AABB.of(placed(arena, corner, arena.hall));
        BoundingBox box = template.getBoundingBox(new StructurePlaceSettings().setRotation(arena.rotation), corner);
        BoundingBox extent = BoundingBox.encapsulatingBoxes(
            List.of(box, room.box(), bridgeBox(threshold, landing), doorway)).orElseThrow();
        return new Plan(arena.template, arena.rotation, corner, box, threshold, landing, doorway, room, hall,
            corner.offset(turned(arena, arena.inside)), corner.offset(turned(arena, arena.post)), extent);
    }

    private static BlockPos turned(Arena arena, BlockPos pos) {
        return StructureTemplate.transform(pos, Mirror.NONE, arena.rotation, BlockPos.ZERO);
    }

    private static BoundingBox placed(Arena arena, BlockPos corner, BoundingBox box) {
        return BoundingBox.fromCorners(corner.offset(turned(arena, new BlockPos(box.minX(), box.minY(), box.minZ()))),
            corner.offset(turned(arena, new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
    }

    private static BoundingBox bridgeBox(BlockPos threshold, BlockPos landing) {
        return new BoundingBox(threshold.getX(), threshold.getY() - 1, threshold.getZ() - BRIDGE_HALF_WIDTH - 1,
            landing.getX(), threshold.getY() + LANTERN_POST_HEIGHT + 1, threshold.getZ() + BRIDGE_HALF_WIDTH + 1);
    }

    public static LongSet chunks(Plan plan) {
        BoundingBox box = plan.box();
        LongSet chunks = new LongOpenHashSet();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }

    public static void place(ServerLevel level, Plan plan, ChunkPos chunk) {
        StructureTemplate template = level.getStructureManager().get(plan.template()).orElseThrow();
        BoundingBox column = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
            chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
        template.placeInWorld(level, plan.origin(), plan.origin(), plan.settings().setBoundingBox(column),
            RandomSource.create(chunk.toLong()), Block.UPDATE_CLIENTS);
    }

    public static void bridge(ServerLevel level, Plan plan, long seed) {
        RandomSource random = RandomSource.create(seed);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> rails = new ArrayList<>();
        int deck = plan.threshold().getY();
        int middle = plan.threshold().getZ();
        for (int x = plan.threshold().getX(); x <= plan.landing().getX(); x++) {
            int along = x - plan.threshold().getX();
            for (int z = middle - BRIDGE_HALF_WIDTH; z <= middle + BRIDGE_HALF_WIDTH; z++) {
                level.setBlock(pos.set(x, deck, z), deckBlock(random), Block.UPDATE_CLIENTS);
                level.setBlock(pos.set(x, deck - 1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            }
            for (int side : new int[] {-1, 1}) {
                int z = middle + side * (BRIDGE_HALF_WIDTH + 1);
                level.setBlock(pos.set(x, deck, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
                level.setBlock(pos.set(x, deck - 1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
                boolean post = along % LANTERN_SPACING == 2;
                int height = post ? LANTERN_POST_HEIGHT : 1;
                for (int rise = 1; rise <= height; rise++) {
                    level.setBlock(pos.set(x, deck + rise, z), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
                    rails.add(pos.immutable());
                }
                if (post) {
                    level.setBlock(pos.set(x, deck + height + 1, z),
                        Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false),
                        Block.UPDATE_CLIENTS);
                }
            }
        }
        BoundingBox doorway = plan.doorway();
        for (int x = doorway.minX(); x <= doorway.maxX(); x++) {
            for (int z = doorway.minZ(); z <= doorway.maxZ(); z++) {
                BlockState floor = level.getBlockState(pos.set(x, doorway.minY(), z));
                if (floor.isAir() || floor.is(Blocks.BARRIER)) {
                    level.setBlock(pos, deckBlock(random), Block.UPDATE_CLIENTS);
                }
                for (int y = doorway.minY() + 1; y <= doorway.maxY(); y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        for (BlockPos rail : rails) {
            level.setBlock(rail, Block.updateFromNeighbourShapes(level.getBlockState(rail), level, rail),
                Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState deckBlock(RandomSource random) {
        return (random.nextInt(4) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS)
            .defaultBlockState();
    }

    public static void garrison(ServerLevel level, Plan plan, DungeonInstance.Kind kind) {
        BlockPos post = plan.post();
        if (kind == DungeonInstance.Kind.MAGNUS_ARENA) {
            MagnusBonfireEntity.kindling(level, Vec3.atBottomCenterOf(post), BOSS_YAW, plan.hall());
            return;
        }
        if (kind == DungeonInstance.Kind.TOWER_SKELETON_ARENA) {
            TowerSkeletonEntity.enthrone(level, plan.hall(), post, BOSS_YAW);
            return;
        }
        if (kind == DungeonInstance.Kind.COG_SENTINEL_ARENA) {
            CogSentinelEntity.station(level, plan.hall(), post, BOSS_YAW);
            return;
        }
        TalusKnightEntity knight = HexwrightBossEntities.TALUS_KNIGHT.create(level);
        if (knight == null) {
            return;
        }
        knight.moveTo(post.getX(), post.getY(), post.getZ(), BOSS_YAW, 0.0F);
        knight.setYHeadRot(BOSS_YAW);
        knight.setYBodyRot(BOSS_YAW);
        knight.guard(plan.hall(), post, BOSS_YAW);
        knight.finalizeSpawn(level, level.getCurrentDifficultyAt(post), MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(knight);
    }
}

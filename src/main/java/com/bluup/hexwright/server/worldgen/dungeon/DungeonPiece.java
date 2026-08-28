package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlockStates;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.boss.HexwrightBossEntities;
import com.bluup.hexwright.server.boss.QuartzGolemEntity;
import com.bluup.hexwright.server.boss.WardedChests;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DungeonPiece extends TemplateStructurePiece {

    private static final String SPAWN_MARKER = "spawn/";

    private static final ResourceLocation CRYSTALITE_TABLE = Hexwright.id("chests/deep_dungeon_crystalite");

    private final float mobChance;
    private final DungeonPlanner.Fittings fittings;

    private final String rune;

    private Set<BlockPos> air;

    private Set<BlockPos> charges;

    public DungeonPiece(StructureTemplateManager templates, ResourceLocation template,
                        BlockPos cellMin, Rotation rotation, float mobChance,
                        DungeonPlanner.Fittings fittings, String rune) {
        super(HexwrightWorldgen.DUNGEON_PIECE, 0, templates, template, template.toString(),
            settings(rotation), align(templates, template, rotation, cellMin));
        this.mobChance = mobChance;
        this.fittings = fittings;
        this.rune = rune;
    }

    public DungeonPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(HexwrightWorldgen.DUNGEON_PIECE, tag, templates,
            template -> settings(savedRotation(tag)));
        this.mobChance = tag.contains("MobChance") ? tag.getFloat("MobChance") : 1.0F;
        String fixture = tag.getString("Fixture");
        this.fittings = new DungeonPlanner.Fittings(
            tag.getBoolean("CrystaliteChest"),
            tag.getBoolean("CaveTap"),
            fixture.isEmpty() ? null : parseFixture(fixture),
            tag.getBoolean("Miniboss"),
            DungeonTraps.Spec.load(tag),
            tag.getBoolean("Anchor"));
        this.rune = tag.getString("Group");
    }

    private static DungeonModules.Fixture parseFixture(String name) {
        for (DungeonModules.Fixture fixture : DungeonModules.Fixture.values()) {
            if (fixture.name().equals(name)) {
                return fixture;
            }
        }
        Hexwright.LOGGER.warn("Dungeon piece names unknown fixture '{}'; leaving the room bare", name);
        return null;
    }

    private static Rotation savedRotation(CompoundTag tag) {
        String saved = tag.getString("Rot");
        for (Rotation rotation : Rotation.values()) {
            if (rotation.name().equals(saved)) {
                return rotation;
            }
        }
        return recoveredRotation(tag);
    }

    private static Rotation recoveredRotation(CompoundTag tag) {
        int[] box = tag.getIntArray("BB");
        if (box.length < 6) {
            Hexwright.LOGGER.warn("Dungeon piece has neither a Rot tag nor a bounding box; placing it unrotated");
            return Rotation.NONE;
        }
        boolean turnedX = tag.getInt("TPX") != box[0];
        boolean turnedZ = tag.getInt("TPZ") != box[2];
        if (turnedX && turnedZ) {
            return Rotation.CLOCKWISE_180;
        }
        if (turnedX) {
            return Rotation.CLOCKWISE_90;
        }
        if (turnedZ) {
            return Rotation.COUNTERCLOCKWISE_90;
        }
        return Rotation.NONE;
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(true);
    }

    private static BlockPos align(StructureTemplateManager templates, ResourceLocation template,
                                  Rotation rotation, BlockPos cellMin) {
        BoundingBox turned = templates.getOrCreate(template)
            .getBoundingBox(settings(rotation), BlockPos.ZERO);
        return new BlockPos(
            cellMin.getX() - turned.minX(),
            cellMin.getY() - turned.minY(),
            cellMin.getZ() - turned.minZ());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        tag.putFloat("MobChance", this.mobChance);
        tag.putBoolean("CrystaliteChest", this.fittings.crystaliteChest());
        tag.putBoolean("CaveTap", this.fittings.caveTap());
        tag.putString("Fixture", this.fittings.fixture() == null ? "" : this.fittings.fixture().name());
        tag.putBoolean("Miniboss", this.fittings.miniboss());
        tag.putBoolean("Anchor", this.fittings.anchor());
        tag.putString("Group", this.rune);
        if (this.fittings.trap() != null) {
            this.fittings.trap().save(tag);
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(level, manager, generator, random, box, chunkPos, pos);
        if (this.fittings.crystaliteChest()) {
            stockCrystaliteChest(level, box, random);
        }
        if (this.fittings.fixture() != null) {
            standFixture(level, box);
        }
        if (this.fittings.caveTap()) {
            openCaveTap(level, box, random);
        }
        if (this.fittings.trap() != null) {
            layTrap(level, box);
        }
        if (this.fittings.anchor()) {
            standAnchor(level, box);
        }
        if (this.fittings.miniboss()) {
            wardBossChests(level, box);
            spawnMiniboss(level, box, random);
        }
        armArrowTraps(level, box);
    }

    private void armArrowTraps(WorldGenLevel level, BoundingBox box) {
        List<BlockPos> dispensers = allOf(Blocks.DISPENSER);
        if (dispensers.isEmpty()) {
            return;
        }
        DungeonArrowTraps.arm(level, box, air(), dispensers, allOf(Blocks.REDSTONE_WIRE));
    }

    public boolean holdsCharge(BlockPos pos) {
        if (this.charges == null) {
            this.charges = new HashSet<>(allOf(Blocks.TNT));
        }
        return this.charges.contains(pos);
    }

    private List<BlockPos> allOf(net.minecraft.world.level.block.Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : this.template.filterBlocks(
            this.templatePosition, settings(this.placeSettings.getRotation()), block)) {
            found.add(info.pos());
        }
        return found;
    }

    private Set<BlockPos> air() {
        if (this.air == null) {
            this.air = new HashSet<>(allOf(Blocks.AIR));
        }
        return this.air;
    }

    private void stockCrystaliteChest(WorldGenLevel level, BoundingBox box, RandomSource random) {
        List<BlockPos> chests = allOf(Blocks.CHEST);
        if (chests.isEmpty()) {
            return;
        }
        chests.sort(DungeonFittings::compare);
        BlockPos chosen = chests.get(0);
        if (!box.isInside(chosen)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(chosen);
        if (entity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(CRYSTALITE_TABLE, random.nextLong());
        }
    }

    private void standFixture(WorldGenLevel level, BoundingBox box) {
        List<BlockPos> spots = DungeonFittings.fixtureSpots(air(), this.boundingBox);
        if (spots.isEmpty()) {
            return;
        }
        int index = Math.floorMod(this.boundingBox.minX() * 31 + this.boundingBox.minZ(), spots.size());
        BlockPos chosen = spots.get(index);
        if (box.isInside(chosen)) {
            DungeonFittings.placeFixture(level, this.fittings.fixture(), chosen, air());
        }
    }

    private void layTrap(WorldGenLevel level, BoundingBox box) {
        BlockPos chosen = trapSpot();
        if (chosen != null && box.isInside(chosen)) {
            DungeonTraps.lay(level, chosen, this.fittings.trap());
        }
    }

    private BlockPos trapSpot() {
        if (this.fittings.trap() == null) {
            return null;
        }
        List<BlockPos> spots = DungeonFittings.trapSpots(air(), this.boundingBox);
        if (spots.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(this.boundingBox.minX() * 17 + this.boundingBox.minZ(), spots.size());
        return spots.get(index);
    }

    public String anchorRune() {
        return this.fittings.anchor() && !this.rune.isEmpty() ? this.rune : null;
    }

    public BlockPos anchorSpot() {
        if (!this.fittings.anchor()) {
            return null;
        }
        BlockPos column = DungeonFittings.tapColumn(air(), this.boundingBox);
        if (column == null) {
            return null;
        }
        return DungeonFittings.anchorSpot(air(), this.boundingBox, column, trapSpot());
    }

    private void standAnchor(WorldGenLevel level, BoundingBox box) {
        String rune = anchorRune();
        BlockPos spot = rune == null ? null : anchorSpot();
        if (spot == null || !box.isInside(spot)) {
            return;
        }
        level.setBlock(spot, HexwrightBlocks.RESONANT_ANCHOR_BLOCK.defaultBlockState()
            .setValue(HexwrightBlockStates.ACTIVE, true), Block.UPDATE_CLIENTS);
        BlockEntity entity = level.getBlockEntity(spot);
        if (entity instanceof ResonantAnchorBlockEntity anchor) {
            anchor.installDungeonAnchor(
                DungeonGroups.registryKey(DungeonGroups.wordFor(level.getSeed(), rune)));
        } else {
            Hexwright.LOGGER.warn("Dungeon anchor at {} has no block entity behind it; leaving it untuned", spot);
        }
    }

    private void openCaveTap(WorldGenLevel level, BoundingBox box, RandomSource random) {
        BlockPos column = DungeonFittings.tapColumn(air(), this.boundingBox);
        if (column == null || !box.isInside(column)) {
            return;
        }
        DungeonFittings.carveCaveTap(level, box, random, column, column.getY() + 1, this.boundingBox.maxY());
    }

    private List<BlockPos> bossChests() {
        List<BlockPos> chests = allOf(Blocks.CHEST);
        chests.sort(DungeonFittings::compare);
        return chests;
    }

    private void wardBossChests(WorldGenLevel level, BoundingBox box) {
        for (BlockPos chest : bossChests()) {
            if (box.isInside(chest)) {
                WardedChests.seal(level, chest);
            }
        }
    }

    private void spawnMiniboss(WorldGenLevel level, BoundingBox box, RandomSource random) {
        BlockPos spot = DungeonFittings.minibossSpot(air(), this.boundingBox);
        if (spot == null || !box.isInside(spot)) {
            return;
        }
        QuartzGolemEntity golem = HexwrightBossEntities.QUARTZ_GOLEM.create(level.getLevel());
        if (golem == null) {
            return;
        }
        golem.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        golem.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null, null);
        golem.setPersistenceRequired();
        golem.setRoom(this.boundingBox, spot);
        golem.setHoardChests(bossChests());
        level.addFreshEntity(golem);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (!marker.startsWith(SPAWN_MARKER) || random.nextFloat() >= this.mobChance) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(marker.substring(SPAWN_MARKER.length()));
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            Hexwright.LOGGER.warn("Dungeon module {} asks for unknown mob '{}'", this.templateName, marker);
            return;
        }
        Entity entity = type.create(level.getLevel());
        if (!(entity instanceof Mob mob)) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }
}

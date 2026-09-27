package com.bluup.hexwright.server.worldgen.hexwrightbase;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.VaultPlinthBlockEntity;
import com.bluup.hexwright.server.block.WeaponRackBlock;
import com.bluup.hexwright.server.block.WeaponRackBlockEntity;
import com.bluup.hexwright.server.cloak.CloakField;
import com.bluup.hexwright.server.cloak.ShieldGeneratorBlockEntity;
import com.bluup.hexwright.server.item.HexwrightItemTags;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.bluup.hexwright.server.worldgen.decadentvault.MasterworkRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HexwrightBasePiece extends StructurePiece {

    public static final ResourceLocation TEMPLATE = Hexwright.id("hexwright_base");

    public static final float SHIELD_RADIUS = 32.0f;
    public static final float SHIELD_HEIGHT = 80.0f;

    public static final float SHIELD_TOUGHNESS = 1500.0f;

    private final StructureTemplate template;
    private final BlockPos templatePosition;
    private final Rotation rotation;

    public HexwrightBasePiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation) {
        super(HexwrightWorldgen.HEXWRIGHT_BASE_PIECE, 0, site(templates.getOrCreate(TEMPLATE), origin));
        this.template = templates.getOrCreate(TEMPLATE);
        this.rotation = rotation;
        BoundingBox turned = this.template.getBoundingBox(settings(rotation), BlockPos.ZERO);
        this.templatePosition = origin.offset(-turned.minX(), -turned.minY(), -turned.minZ());
    }

    public HexwrightBasePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(HexwrightWorldgen.HEXWRIGHT_BASE_PIECE, tag);
        this.template = templates.getOrCreate(TEMPLATE);
        this.templatePosition = new BlockPos(tag.getInt("TPX"), tag.getInt("TPY"), tag.getInt("TPZ"));
        this.rotation = Rotation.valueOf(tag.getString("Rot"));
    }

    public static Vec3i footprint(StructureTemplateManager templates) {
        return templates.getOrCreate(TEMPLATE).getSize();
    }

    private static BoundingBox site(StructureTemplate template, BlockPos origin) {
        Vec3i size = template.getSize();
        int reach = Mth.ceil(SHIELD_RADIUS);
        int centreX = origin.getX() + size.getX() / 2;
        int centreZ = origin.getZ() + size.getZ() / 2;
        return new BoundingBox(centreX - reach, origin.getY() + 1, centreZ - reach,
            centreX + reach, origin.getY() + size.getY() - 1, centreZ + reach);
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(true);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("TPX", this.templatePosition.getX());
        tag.putInt("TPY", this.templatePosition.getY());
        tag.putInt("TPZ", this.templatePosition.getZ());
        tag.putString("Rot", this.rotation.name());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        this.template.placeInWorld(level, this.templatePosition, this.templatePosition,
            settings(this.rotation).setBoundingBox(chunkBox), random, Block.UPDATE_CLIENTS);

        PositionalRandomFactory draws = RandomSource.create(this.templatePosition.asLong()).forkPositional();
        raiseShield(level, chunkBox, draws.fromHashOf("shield"));
        stockPlinth(level, chunkBox, draws.fromHashOf("orb"));
        stockRack(level, chunkBox, draws.fromHashOf("staff"));
        HexwrightBaseHoard.stock(level, chunkBox, positions(Blocks.CHEST), positions(Blocks.BARREL),
            draws.fromHashOf("hoard"));
    }

    private void raiseShield(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        BlockPos pos = firstInChunk(HexwrightBlocks.SHIELD_GENERATOR_BLOCK, chunkBox);
        if (pos == null || !(level.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator)) {
            return;
        }
        Vec3i size = this.template.getSize();
        BoundingBox build = this.template.getBoundingBox(settings(this.rotation), this.templatePosition);
        Vec3 centre = new Vec3(build.minX() + size.getX() / 2.0, build.minY() + 1.0, build.minZ() + size.getZ() / 2.0);
        generator.guard(new CloakField(Mth.createInsecureUUID(random), centre, SHIELD_RADIUS, SHIELD_HEIGHT,
            null, SHIELD_TOUGHNESS));
    }

    private void stockPlinth(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        BlockPos pos = firstInChunk(HexwrightBlocks.PLINTH_BLOCK, chunkBox);
        if (pos == null || !(level.getBlockEntity(pos) instanceof VaultPlinthBlockEntity plinth)) {
            return;
        }
        List<Item> orbs = new ArrayList<>();
        for (Holder<Item> orb : BuiltInRegistries.ITEM.getTagOrEmpty(HexwrightItemTags.POWER_ORBS)) {
            orbs.add(orb.value());
        }
        if (!orbs.isEmpty()) {
            plinth.stock(new ItemStack(orbs.get(random.nextInt(orbs.size()))));
        }
    }

    private void stockRack(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        BlockPos pos = firstInChunk(HexwrightBlocks.WEAPON_RACK_BLOCK, chunkBox);
        if (pos == null || !(level.getBlockEntity(pos) instanceof WeaponRackBlockEntity rack)) {
            return;
        }
        int slot = random.nextInt(WeaponRackBlock.SLOTS);
        ItemStack staff = MasterworkRewards.staff(random);
        if (!staff.isEmpty()) {
            rack.set(slot, staff);
        }
    }

    @Nullable
    private BlockPos firstInChunk(Block block, BoundingBox chunkBox) {
        for (StructureTemplate.StructureBlockInfo info : this.template.filterBlocks(
            this.templatePosition, settings(this.rotation), block)) {
            if (info.nbt() != null && chunkBox.isInside(info.pos())) {
                return info.pos();
            }
        }
        return null;
    }

    private List<BlockPos> positions(Block block) {
        return this.template.filterBlocks(this.templatePosition, settings(this.rotation), block).stream()
            .map(StructureTemplate.StructureBlockInfo::pos)
            .toList();
    }
}

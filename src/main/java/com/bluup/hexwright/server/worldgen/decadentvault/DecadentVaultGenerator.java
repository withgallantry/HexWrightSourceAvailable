package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.block.DecadentVaultExitBlockEntity;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.PlacedBottleBlockEntity;
import com.bluup.hexwright.server.block.VaultPlinthBlockEntity;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.remnant.BottleData;
import com.bluup.hexwright.server.vault.VaultKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DecadentVaultGenerator {

    static final ResourceLocation STRUCTURE = Hexwright.id("decadent_vault/ancient_stash");

    private static final int STRUCT_W = 22;
    private static final int STRUCT_H = 15;
    private static final int STRUCT_L = 41;

    private static final int MARGIN = 3;

    static final int SIZE_X = STRUCT_W + 2 * MARGIN;
    static final int SIZE_Z = STRUCT_L + 2 * MARGIN;
    static final int HEIGHT = STRUCT_H + 2 * MARGIN;

    private static final float UNIQUE_DROP_CHANCE = 0.3f;

    private static final float MAGE_ATTIRE_CHANCE = 0.3f;

    private DecadentVaultGenerator() {
    }

    static BlockPos generate(ServerLevel level, BlockPos origin, ResourceKey<Level> portalDimension,
                             BlockPos portalPos, RandomSource random) {
        int minChunk = origin.getX() >> 4;
        int maxChunk = (origin.getX() + SIZE_X) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + SIZE_Z) >> 4;
        for (int cx = minChunk; cx <= maxChunk; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }

        fillSolid(level, origin);
        BlockPos innerOrigin = origin.offset(MARGIN, MARGIN, MARGIN);
        placeStructure(level, innerOrigin, random);
        closeDoors(level, innerOrigin);

        Markers markers = scan(level, innerOrigin);
        BlockPos exit = resolveExit(level, markers.exits, portalDimension, portalPos);
        resolveBottles(level, markers.bottles, random);
        resolveDisplays(level, innerOrigin, random);
        DecadentVaultHoard.stock(level, markers.containers, markers.barrels, random);

        if (exit == null) {
            Hexwright.LOGGER.error("Decadent Vault template {} has no exit marker", STRUCTURE);
            return innerOrigin.offset(STRUCT_W / 2, 1, 2);
        }
        return exit.above();
    }


    private static void placeStructure(ServerLevel level, BlockPos innerOrigin, RandomSource random) {
        var loaded = level.getStructureManager().get(STRUCTURE);
        if (loaded.isEmpty()) {
            Hexwright.LOGGER.error("Decadent Vault is missing its structure {}", STRUCTURE);
            return;
        }
        StructureTemplate structure = loaded.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(Rotation.NONE)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(false);
        structure.placeInWorld(level, innerOrigin, innerOrigin, settings, random, Block.UPDATE_CLIENTS);
    }

    private static void closeDoors(ServerLevel level, BlockPos innerOrigin) {
        for (int x = 0; x < STRUCT_W; x++) {
            for (int y = 0; y < STRUCT_H; y++) {
                for (int z = 0; z < STRUCT_L; z++) {
                    BlockPos pos = innerOrigin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
                        set(level, pos, state.setValue(DoorBlock.OPEN, false));
                    }
                }
            }
        }
    }

    private static void fillSolid(ServerLevel level, BlockPos origin) {
        BlockState shell = HexwrightBlocks.REFINED_BINDSTONE_BLOCK.defaultBlockState();
        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    set(level, origin.offset(x, y, z), shell);
                }
            }
        }
    }


    private record Markers(List<BlockPos> containers, List<BlockPos> barrels,
                           List<BlockPos> exits, List<BlockPos> bottles) {
    }

    private static Markers scan(ServerLevel level, BlockPos innerOrigin) {
        Markers markers = new Markers(new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>());
        for (int x = 0; x < STRUCT_W; x++) {
            for (int y = 0; y < STRUCT_H; y++) {
                for (int z = 0; z < STRUCT_L; z++) {
                    BlockPos pos = innerOrigin.offset(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block instanceof EnderChestBlock) {
                        continue;
                    }
                    if (block instanceof AbstractChestBlock || block instanceof ShulkerBoxBlock) {
                        markers.containers().add(pos.immutable());
                    } else if (block instanceof BarrelBlock) {
                        markers.barrels().add(pos.immutable());
                    } else if (block == Blocks.YELLOW_WOOL || block == Blocks.YELLOW_CARPET) {
                        markers.exits().add(pos.immutable());
                    } else if (block == HexwrightBlocks.PLACED_BOTTLE_BLOCK) {
                        markers.bottles().add(pos.immutable());
                    }
                }
            }
        }
        return markers;
    }


    private static BlockPos resolveExit(ServerLevel level, List<BlockPos> marked,
                                        ResourceKey<Level> portalDimension, BlockPos portalPos) {
        if (marked.isEmpty()) {
            return null;
        }
        BlockPos exit = marked.get(0);
        set(level, exit, HexwrightBlocks.DECADENT_VAULT_EXIT_BLOCK.defaultBlockState());
        if (level.getBlockEntity(exit) instanceof DecadentVaultExitBlockEntity bound) {
            bound.bindPortal(portalDimension, portalPos);
        }
        for (int i = 1; i < marked.size(); i++) {
            set(level, marked.get(i), Blocks.AIR.defaultBlockState());
        }
        return exit;
    }

    private static void resolveBottles(ServerLevel level, List<BlockPos> marked,
                                       RandomSource random) {
        for (BlockPos pos : marked) {
            if (!(level.getBlockEntity(pos) instanceof PlacedBottleBlockEntity placed)) {
                continue;
            }
            PocketCasterData.Quality[] grades = PocketCasterData.Quality.values();
            ItemStack bottle = BottleData.create(grades[random.nextInt(grades.length)]);
            RemnantType[] types = RemnantType.values();
            double fill = 0.34 + random.nextDouble() * 0.66;
            BottleData.pour(bottle, new Remnant(types[random.nextInt(types.length)],
                BottleData.capacity(bottle) * fill));
            placed.setBottle(bottle);
        }
    }

    private static void resolveDisplays(ServerLevel level, BlockPos innerOrigin,
                                        RandomSource random) {
        AABB box = new AABB(innerOrigin, innerOrigin.offset(STRUCT_W, STRUCT_H, STRUCT_L));
        List<ArmorStand> spots = new ArrayList<>(level.getEntitiesOfClass(ArmorStand.class, box));
        Collections.shuffle(spots, new java.util.Random(random.nextLong()));

        List<ItemStack> plinthed = new ArrayList<>(List.of(
            VaultKeyItem.artifact(),
            MasterworkRewards.staffCore(random),
            MasterworkRewards.broom(random)));
        if (random.nextFloat() < UNIQUE_DROP_CHANCE) {
            plinthed.add(UniqueGolemDrops.roll(random));
        }

        List<List<ItemStack>> geared = new ArrayList<>();
        geared.add(MasterworkRewards.armourSet(random));
        if (random.nextFloat() < MAGE_ATTIRE_CHANCE) {
            geared.add(MageAttire.roll(random));
        }
        int haulStands = 2 + random.nextInt(2);
        for (int i = 0; i < haulStands; i++) {
            geared.add(random.nextBoolean()
                ? VanillaGearSets.netherite()
                : VanillaGearSets.enchantedDiamond(random));
        }

        for (ArmorStand stand : spots) {
            if (!plinthed.isEmpty()) {
                BlockPos pos = stand.blockPosition();
                stand.discard();
                stockPlinth(level, pos, plinthed.remove(plinthed.size() - 1));
            } else if (!geared.isEmpty()) {
                dress(stand, geared.remove(geared.size() - 1));
            } else {
                stand.discard();
            }
        }

        if (!plinthed.isEmpty() || !geared.isEmpty()) {
            Hexwright.LOGGER.warn("Decadent Vault has {} display spots, {} rewards went unplaced",
                spots.size(), plinthed.size() + geared.size());
        }
    }

    private static void dress(ArmorStand stand, List<ItemStack> gear) {
        if (gear.size() != 4) {
            return;
        }
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setItemSlot(EquipmentSlot.HEAD, gear.get(0));
        stand.setItemSlot(EquipmentSlot.CHEST, gear.get(1));
        stand.setItemSlot(EquipmentSlot.LEGS, gear.get(2));
        stand.setItemSlot(EquipmentSlot.FEET, gear.get(3));
    }

    private static void stockPlinth(ServerLevel level, BlockPos pos, ItemStack item) {
        set(level, pos, HexwrightBlocks.VAULT_PLINTH_BLOCK.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof VaultPlinthBlockEntity plinth) {
            plinth.stock(item);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}

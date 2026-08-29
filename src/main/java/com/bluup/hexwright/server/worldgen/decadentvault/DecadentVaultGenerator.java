package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.DecadentVaultExitBlockEntity;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.VaultPlinthBlockEntity;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        hollow(level, innerOrigin);
        placeStructure(level, innerOrigin, random);

        List<BlockPos> containers = scanContainers(level, innerOrigin);
        Set<BlockPos> containerSet = new HashSet<>(containers);
        ceilingLights(level, innerOrigin, containerSet);

        List<BlockPos> spots = scanFloorSpots(level, innerOrigin, containerSet);
        Collections.shuffle(spots, new java.util.Random(random.nextLong()));

        BlockPos arrival = pickNearFront(spots);
        BlockPos exitSpot = pickNearFront(spots);
        if (arrival == null) {
            arrival = innerOrigin.offset(STRUCT_W / 2, 1, 2);
        }
        if (exitSpot == null) {
            exitSpot = arrival;
        }

        stockPlinth(level, pickSpot(spots, innerOrigin),
            VaultKeyItem.blank(PocketCasterData.Quality.MASTERWORK));
        stockPlinth(level, pickSpot(spots, innerOrigin), MasterworkRewards.staffCore(random));
        stockPlinth(level, pickSpot(spots, innerOrigin), MasterworkRewards.broom(random));
        spawnArmourStand(level, pickSpot(spots, innerOrigin), MasterworkRewards.armourSet(random), random);

        if (random.nextFloat() < UNIQUE_DROP_CHANCE) {
            stockPlinth(level, pickSpot(spots, innerOrigin), UniqueGolemDrops.roll(random));
        }

        int haulStands = 2 + random.nextInt(2);
        for (int i = 0; i < haulStands; i++) {
            List<ItemStack> gear = random.nextBoolean()
                ? VanillaGearSets.netherite()
                : VanillaGearSets.enchantedDiamond(random);
            spawnArmourStand(level, pickSpot(spots, innerOrigin), gear, random);
        }

        DecadentVaultHoard.stock(level, containers, random);

        entrance(level, exitSpot, portalDimension, portalPos);

        return arrival;
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
            .setIgnoreEntities(true);
        structure.placeInWorld(level, innerOrigin, innerOrigin, settings, random, Block.UPDATE_CLIENTS);
    }

    private static void entrance(ServerLevel level, BlockPos exitSpot,
                                 ResourceKey<Level> portalDimension, BlockPos portalPos) {
        set(level, exitSpot, HexwrightBlocks.DECADENT_VAULT_EXIT_BLOCK.defaultBlockState());
        if (level.getBlockEntity(exitSpot) instanceof DecadentVaultExitBlockEntity exit) {
            exit.bindPortal(portalDimension, portalPos);
        }
    }

    private static void spawnArmourStand(ServerLevel level, BlockPos pos, List<ItemStack> gear,
                                         RandomSource random) {
        if (gear.size() != 4) {
            return;
        }
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setYRot(random.nextFloat() * 360.0f);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setItemSlot(EquipmentSlot.HEAD, gear.get(0));
        stand.setItemSlot(EquipmentSlot.CHEST, gear.get(1));
        stand.setItemSlot(EquipmentSlot.LEGS, gear.get(2));
        stand.setItemSlot(EquipmentSlot.FEET, gear.get(3));
        level.addFreshEntity(stand);
    }

    private static void stockPlinth(ServerLevel level, BlockPos pos, ItemStack item) {
        set(level, pos, HexwrightBlocks.VAULT_PLINTH_BLOCK.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof VaultPlinthBlockEntity plinth) {
            plinth.stock(item);
        }
    }


    private static void fillSolid(ServerLevel level, BlockPos origin) {
        var shell = HexwrightBlocks.REFINED_BINDSTONE_BLOCK.defaultBlockState();
        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    set(level, origin.offset(x, y, z), shell);
                }
            }
        }
    }

    private static void hollow(ServerLevel level, BlockPos innerOrigin) {
        var air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < STRUCT_W; x++) {
            for (int y = 0; y < STRUCT_H; y++) {
                for (int z = 0; z < STRUCT_L; z++) {
                    set(level, innerOrigin.offset(x, y, z), air);
                }
            }
        }
    }


    private static void ceilingLights(ServerLevel level, BlockPos innerOrigin, Set<BlockPos> reserved) {
        for (int x = 1; x < STRUCT_W - 1; x += 4) {
            for (int z = 1; z < STRUCT_L - 1; z += 4) {
                for (int y = STRUCT_H - 1; y >= 1; y--) {
                    BlockPos pos = innerOrigin.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    if (!level.getBlockState(pos.above()).isAir() && !reserved.contains(pos)) {
                        set(level, pos, Blocks.SEA_LANTERN.defaultBlockState());
                    }
                    break;
                }
            }
        }
    }


    private static List<BlockPos> scanContainers(ServerLevel level, BlockPos innerOrigin) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = 0; x < STRUCT_W; x++) {
            for (int y = 0; y < STRUCT_H; y++) {
                for (int z = 0; z < STRUCT_L; z++) {
                    BlockPos pos = innerOrigin.offset(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block instanceof AbstractChestBlock || block instanceof ShulkerBoxBlock) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
        return found;
    }

    private static List<BlockPos> scanFloorSpots(ServerLevel level, BlockPos innerOrigin,
                                                 Set<BlockPos> containers) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = 0; x < STRUCT_W; x++) {
            for (int y = 0; y < STRUCT_H - 1; y++) {
                for (int z = 0; z < STRUCT_L; z++) {
                    BlockPos pos = innerOrigin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() || !level.getFluidState(pos).isEmpty()) {
                        continue;
                    }
                    BlockPos below = pos.below();
                    BlockState belowState = level.getBlockState(below);
                    if (belowState.isAir() || !level.getFluidState(below).isEmpty()) {
                        continue;
                    }
                    if (!level.getBlockState(pos.above()).isAir()) {
                        continue;
                    }
                    if (nearContainer(pos, containers)) {
                        continue;
                    }
                    spots.add(pos.immutable());
                }
            }
        }
        return spots;
    }

    private static boolean nearContainer(BlockPos pos, Set<BlockPos> containers) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (containers.contains(pos.offset(dx, 0, dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockPos pickNearFront(List<BlockPos> spots) {
        if (spots.isEmpty()) {
            return null;
        }
        BlockPos best = spots.get(0);
        for (BlockPos candidate : spots) {
            if (candidate.getZ() < best.getZ()) {
                best = candidate;
            }
        }
        spots.remove(best);
        return best;
    }

    private static BlockPos pickSpot(List<BlockPos> spots, BlockPos innerOrigin) {
        if (spots.isEmpty()) {
            return innerOrigin.offset(STRUCT_W / 2, 1, STRUCT_L / 2);
        }
        return spots.remove(spots.size() - 1);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}

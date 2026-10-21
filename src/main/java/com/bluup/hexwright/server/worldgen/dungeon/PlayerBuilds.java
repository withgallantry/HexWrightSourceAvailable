package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class PlayerBuilds {

    public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK, Hexwright.id("dungeon_site_natural"));

    public static final TagKey<Block> DEBRIS = TagKey.create(Registries.BLOCK, Hexwright.id("dungeon_debris"));

    static final TagKey<Block> LOOT = TagKey.create(Registries.BLOCK, Hexwright.id("loot_containers"));

    private static final Set<BlockEntityType<?>> WORLDGEN_BLOCK_ENTITIES = Set.of(
        BlockEntityType.MOB_SPAWNER,
        BlockEntityType.SCULK_SENSOR,
        BlockEntityType.CALIBRATED_SCULK_SENSOR,
        BlockEntityType.SCULK_SHRIEKER,
        BlockEntityType.SCULK_CATALYST,
        BlockEntityType.BRUSHABLE_BLOCK);

    private PlayerBuilds() {
    }

    public static boolean isWorldgen(BlockEntity entity) {
        if (WORLDGEN_BLOCK_ENTITIES.contains(entity.getType()) || entity.getBlockState().is(LOOT)) {
            return true;
        }
        return entity instanceof RandomizableContainerBlockEntity
            && entity.saveWithoutMetadata().contains(RandomizableContainerBlockEntity.LOOT_TABLE_TAG);
    }

    public static @Nullable String unnaturalIn(LevelChunk chunk, BoundingBox region) {
        ChunkPos pos = chunk.getPos();
        int minX = Math.max(region.minX(), pos.getMinBlockX());
        int maxX = Math.min(region.maxX(), pos.getMaxBlockX());
        int minZ = Math.max(region.minZ(), pos.getMinBlockZ());
        int maxZ = Math.min(region.maxZ(), pos.getMaxBlockZ());
        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            int bottom = chunk.getSectionYFromSectionIndex(index) << 4;
            int minY = Math.max(region.minY(), bottom);
            int maxY = Math.min(region.maxY(), bottom + 15);
            if (minY > maxY || section.hasOnlyAir()
                || !section.getStates().maybeHas(state -> !state.is(NATURAL))) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (!state.is(NATURAL)) {
                            return describe(state, new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        return null;
    }

    public static @Nullable String strangerIn(ServerLevel level, LevelChunk chunk, BoundingBox region) {
        ChunkPos pos = chunk.getPos();
        AABB area = new AABB(
            Math.max(region.minX(), pos.getMinBlockX()), region.minY(), Math.max(region.minZ(), pos.getMinBlockZ()),
            Math.min(region.maxX(), pos.getMaxBlockX()) + 1, region.maxY() + 1,
            Math.min(region.maxZ(), pos.getMaxBlockZ()) + 1);
        if (area.getXsize() <= 0 || area.getZsize() <= 0) {
            return null;
        }
        List<Entity> found = level.getEntities((Entity) null, area,
            entity -> placedByPlayer(entity) || (entity instanceof Mob && !wild(entity)));
        return found.isEmpty() ? null : describe(found.get(0));
    }

    private static boolean wild(Entity entity) {
        if (worldgenLoot(entity)) {
            return true;
        }
        return entity instanceof Mob mob
            && !mob.hasCustomName()
            && !mob.isLeashed()
            && !mob.isPersistenceRequired()
            && !(mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null);
    }

    private static boolean worldgenLoot(Entity entity) {
        return entity instanceof ContainerEntity container && container.getLootTable() != null;
    }

    public static boolean placedByPlayer(Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return true;
        }
        return !(entity instanceof LivingEntity) && !passing(entity) && !worldgenLoot(entity);
    }

    static boolean passing(Entity entity) {
        return entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile
            || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt
            || entity instanceof AreaEffectCloud || entity instanceof LightningBolt;
    }

    public static String describe(BlockState state, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at " + pos.toShortString();
    }

    public static String describe(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " at " + entity.blockPosition().toShortString();
    }
}

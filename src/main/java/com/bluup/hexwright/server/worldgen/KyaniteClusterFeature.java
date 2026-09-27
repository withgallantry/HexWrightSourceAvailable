package com.bluup.hexwright.server.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

public final class KyaniteClusterFeature extends Feature<KyaniteClusterFeature.Config> {

    public record Config(
        BlockState state,
        BlockState buddingState,
        TagKey<Block> canAttachTo,
        int tries,
        int xzSpread,
        int ySpread,
        IntProvider maxClusters
    ) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("state").forGetter(Config::state),
            BlockState.CODEC.fieldOf("budding_state").forGetter(Config::buddingState),
            TagKey.codec(Registries.BLOCK).fieldOf("can_attach_to").forGetter(Config::canAttachTo),
            Codec.intRange(1, 256).fieldOf("tries").forGetter(Config::tries),
            Codec.intRange(0, 7).fieldOf("xz_spread").forGetter(Config::xzSpread),
            Codec.intRange(0, 7).fieldOf("y_spread").forGetter(Config::ySpread),
            IntProvider.codec(1, 64).fieldOf("max_clusters").forGetter(Config::maxClusters)
        ).apply(instance, Config::new));
    }

    public KyaniteClusterFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config config = context.config();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int budget = config.maxClusters().sample(random);
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < config.tries() && placed < budget; i++) {
            pos.setWithOffset(
                origin,
                random.nextInt(config.xzSpread() * 2 + 1) - config.xzSpread(),
                random.nextInt(config.ySpread() * 2 + 1) - config.ySpread(),
                random.nextInt(config.xzSpread() * 2 + 1) - config.xzSpread()
            );
            BlockState existing = level.getBlockState(pos);
            boolean water = existing.getFluidState().is(Fluids.WATER) && existing.getFluidState().isSource();
            if (!existing.isAir() && !(water && existing.is(Blocks.WATER))) {
                continue;
            }

            Direction facing = attachFace(level, pos, config, random);
            if (facing == null) {
                continue;
            }
            BlockState cluster = config.state()
                .setValue(AmethystClusterBlock.FACING, facing)
                .setValue(BlockStateProperties.WATERLOGGED, water);
            level.setBlock(pos.relative(facing.getOpposite()), config.buddingState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos, cluster, Block.UPDATE_CLIENTS);
            placed++;
        }
        return placed > 0;
    }

    private static Direction attachFace(WorldGenLevel level, BlockPos pos, Config config, RandomSource random) {
        Direction[] directions = Direction.values();
        int start = random.nextInt(directions.length);
        for (int i = 0; i < directions.length; i++) {
            Direction facing = directions[(start + i) % directions.length];
            BlockPos support = pos.relative(facing.getOpposite());
            BlockState supportState = level.getBlockState(support);
            if (supportState.is(config.canAttachTo()) && supportState.isFaceSturdy(level, support, facing)) {
                return facing;
            }
        }
        return null;
    }
}

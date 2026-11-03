package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.GroundSlam;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class GroundSlamWaveClient {

    private static final int SURFACE_SEARCH_UP = 2;
    private static final int SURFACE_SEARCH_DOWN = 3;

    private static final float COPY_SCALE = 0.998F;

    private static final float MIN_VISIBLE_LIFT = 0.02F;

    private static final int BURST_PARTICLES = 24;
    private static final double BURST_SPEED = 0.32D;

    private static final int CREST_PARTICLES = 2;
    private static final double CREST_SPEED = 0.12D;

    private static final List<Wave> WAVES = new ArrayList<>();

    private GroundSlamWaveClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(GroundSlamWaveClient::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(GroundSlamWaveClient::render);
    }

    public static void handleSlam(BlockPos impact, float radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Wave wave = Wave.build(level, impact, radius);
        if (wave == null) {
            return;
        }
        WAVES.add(wave);
        burst(level, wave);
    }

    private static void onClientTick(Minecraft mc) {
        if (WAVES.isEmpty()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            WAVES.clear();
            return;
        }

        Iterator<Wave> waves = WAVES.iterator();
        while (waves.hasNext()) {
            Wave wave = waves.next();
            if (wave.level != level) {
                waves.remove();
                continue;
            }
            wave.age++;
            wave.shedDebris(level);
            if (wave.age > wave.lifetime) {
                waves.remove();
            }
        }
    }

    private static void render(WorldRenderContext context) {
        if (WAVES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !(context.consumers() instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }

        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        RandomSource random = RandomSource.create();
        Set<RenderType> drawn = new HashSet<>();

        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            for (Wave wave : WAVES) {
                if (wave.level != level) {
                    continue;
                }
                float now = wave.age + context.tickDelta();
                for (Column column : wave.columns) {
                    float lift = column.liftAt(now);
                    if (lift < MIN_VISIBLE_LIFT) {
                        continue;
                    }

                    RenderType type = ItemBlockRenderTypes.getChunkRenderType(column.state);
                    VertexConsumer consumer = buffers.getBuffer(type);
                    pose.pushPose();
                    pose.translate(column.pos.getX(), column.pos.getY() + lift, column.pos.getZ());
                    pose.translate(0.5F, 0.5F, 0.5F);
                    pose.scale(COPY_SCALE, COPY_SCALE, COPY_SCALE);
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    blocks.renderBatched(column.state, column.pos, level, pose, consumer, false, random);
                    pose.popPose();
                    drawn.add(type);
                }
            }
        } finally {
            pose.popPose();
        }

        for (RenderType type : drawn) {
            buffers.endBatch(type);
        }
    }

    private static void burst(ClientLevel level, Wave wave) {
        BlockState struck = level.getBlockState(wave.impact);
        if (struck.isAir()) {
            return;
        }
        RandomSource random = level.random;
        double x = wave.impact.getX() + 0.5D;
        double y = wave.impact.getY() + 1.05D;
        double z = wave.impact.getZ() + 0.5D;

        for (int i = 0; i < BURST_PARTICLES; i++) {
            double angle = (Math.PI * 2.0D) * i / BURST_PARTICLES;
            double speed = BURST_SPEED * (0.6D + random.nextDouble() * 0.8D);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, struck),
                x + Math.cos(angle) * 0.4D, y, z + Math.sin(angle) * 0.4D,
                Math.cos(angle) * speed, 0.18D + random.nextDouble() * 0.22D, Math.sin(angle) * speed);
        }
        level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    private static final class Column {
        private final BlockPos pos;
        private final BlockState state;
        private final float delay;
        private final float amplitude;
        private final double outX;
        private final double outZ;
        private boolean shed;

        private Column(BlockPos pos, BlockState state, float delay, float amplitude, double outX, double outZ) {
            this.pos = pos;
            this.state = state;
            this.delay = delay;
            this.amplitude = amplitude;
            this.outX = outX;
            this.outZ = outZ;
        }

        private float liftAt(float now) {
            float local = (now - this.delay) / GroundSlam.RISE_TICKS;
            if (local <= 0.0F || local >= 1.0F) {
                return 0.0F;
            }
            return this.amplitude * Mth.sin((float) Math.PI * local);
        }
    }

    private static final class Wave {
        private final ClientLevel level;
        private final BlockPos impact;
        private final List<Column> columns;
        private int age;
        private final float lifetime;

        private Wave(ClientLevel level, BlockPos impact, List<Column> columns, float lifetime) {
            this.level = level;
            this.impact = impact;
            this.columns = columns;
            this.lifetime = lifetime;
        }

        @Nullable
        private static Wave build(ClientLevel level, BlockPos impact, float radius) {
            int reach = Mth.ceil(radius);
            List<Column> columns = new ArrayList<>();
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
                    if (distance > radius) {
                        continue;
                    }
                    BlockPos surface = surfaceAt(level, impact.getX() + dx, impact.getY(), impact.getZ() + dz);
                    if (surface == null) {
                        continue;
                    }

                    float taper = 1.0F - GroundSlam.EDGE_FALLOFF * (float) (distance / radius);
                    columns.add(new Column(
                        surface,
                        level.getBlockState(surface),
                        (float) distance * GroundSlam.SPEED_TICKS_PER_BLOCK,
                        GroundSlam.MAX_HEIGHT * taper,
                        distance < 1.0E-4D ? 0.0D : dx / distance,
                        distance < 1.0E-4D ? 0.0D : dz / distance
                    ));
                }
            }
            if (columns.isEmpty()) {
                return null;
            }
            return new Wave(level, impact, columns,
                radius * GroundSlam.SPEED_TICKS_PER_BLOCK + GroundSlam.RISE_TICKS + 1.0F);
        }

        private void shedDebris(ClientLevel level) {
            for (Column column : this.columns) {
                if (column.shed || this.age < column.delay) {
                    continue;
                }
                column.shed = true;
                for (int i = 0; i < CREST_PARTICLES; i++) {
                    double jitterX = level.random.nextDouble() * 0.6D + 0.2D;
                    double jitterZ = level.random.nextDouble() * 0.6D + 0.2D;
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, column.state),
                        column.pos.getX() + jitterX,
                        column.pos.getY() + 1.02D,
                        column.pos.getZ() + jitterZ,
                        column.outX * CREST_SPEED,
                        0.12D + level.random.nextDouble() * 0.1D,
                        column.outZ * CREST_SPEED);
                }
            }
        }
    }

    @Nullable
    private static BlockPos surfaceAt(ClientLevel level, int x, int impactY, int z) {
        for (int y = impactY + SURFACE_SEARCH_UP; y >= impactY - SURFACE_SEARCH_DOWN; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()
                || state.hasBlockEntity()
                || state.getRenderShape() != RenderShape.MODEL
                || !state.getFluidState().isEmpty()
                || state.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }
            BlockPos above = pos.above();
            if (level.getBlockState(above).isSolidRender(level, above)) {
                continue;
            }
            return pos;
        }
        return null;
    }
}

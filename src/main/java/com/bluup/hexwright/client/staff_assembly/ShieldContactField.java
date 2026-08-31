package com.bluup.hexwright.client.staff_assembly;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Arrays;

final class ShieldContactField {

    static final float RANGE = 4.0f;

    private static final float[] CELL_SIZES = {0.25f, 0.5f, 1.0f, 2.0f, 4.0f};
    private static final int MAX_SAMPLES = 96;

    private static final float SLAB = 0.25f;

    private static final int MAX_AGE_TICKS = 10;

    private static final int COLS = 3;
    private static final int ROWS = 2;
    static final int FACE_COUNT = COLS * ROWS;

    private static final float INF = 1.0e9f;

    private static final float ORTHO_SEED = 0.5f;

    private static final float DIAGONAL_SEED = 0.5f;

    private static final int[] FACE_AXIS = {0, 0, 1, 1, 2, 2};
    private static final int[] FACE_SIDE = {-1, 1, -1, 1, -1, 1};
    private static final int[] FACE_U = {1, 1, 0, 0, 0, 0};
    private static final int[] FACE_V = {2, 2, 2, 2, 1, 1};

    private int textureId = -1;
    private int allocatedWidth = -1;
    private int allocatedHeight = -1;

    private float cell;
    private int samples;
    private int tile;
    private int atlasWidth;
    private int atlasHeight;
    private ByteBuffer pixels;

    private boolean[] fine;
    private boolean[] blocks;
    private float[] dist;
    private float[] vecU;
    private float[] vecV;

    private double originX;
    private double originY;
    private double originZ;
    private float scanHalfExtent;
    private boolean ready;

    private final int[] signature = new int[FACE_COUNT * 2 + 3];
    private int age = Integer.MAX_VALUE;

    static int faceAxis(int face) {
        return FACE_AXIS[face];
    }

    static int faceSide(int face) {
        return FACE_SIDE[face];
    }

    static int faceU(int face) {
        return FACE_U[face];
    }

    static int faceV(int face) {
        return FACE_V[face];
    }

    float atlasU(int face, float fraction) {
        return ((face % COLS) * tile + 1.0f + fraction * samples) / atlasWidth;
    }

    float atlasV(int face, float fraction) {
        return ((face / COLS) * tile + 1.0f + fraction * samples) / atlasHeight;
    }

    boolean ready() {
        return ready && textureId >= 0;
    }

    float cellSize() {
        return cell;
    }

    int textureId() {
        return textureId;
    }

    float fraction(int axis, double world) {
        double min = axis == 0 ? originX : axis == 1 ? originY : originZ;
        return (float) ((world - min) / (samples * (double) cell));
    }

    void rebuild(Level level, double cx, double cy, double cz, float halfExtent) {
        if (halfExtent <= 0.0f) {
            ready = false;
            return;
        }
        resize(halfExtent);

        double snappedX = snap(cx - halfExtent);
        double snappedY = snap(cy - halfExtent);
        double snappedZ = snap(cz - halfExtent);

        if (unchanged(snappedX, snappedY, snappedZ, cx, cy, cz, halfExtent) && ready) {
            age++;
            return;
        }
        age = 0;

        originX = snappedX;
        originY = snappedY;
        originZ = snappedZ;
        scanHalfExtent = halfExtent;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int face = 0; face < FACE_COUNT; face++) {
            scanFace(level, face, cx, cy, cz, halfExtent, pos);
        }
        upload();
        ready = true;
    }

    void close() {
        if (textureId >= 0) {
            TextureUtil.releaseTextureId(textureId);
            textureId = -1;
            allocatedWidth = -1;
            allocatedHeight = -1;
        }
        ready = false;
    }

    private double snap(double low) {
        return Math.floor(low / cell) * cell - cell;
    }

    private boolean unchanged(double sx, double sy, double sz, double cx, double cy, double cz, float halfExtent) {
        if (age >= MAX_AGE_TICKS || halfExtent != scanHalfExtent) {
            return false;
        }
        boolean same = sx == originX && sy == originY && sz == originZ;
        for (int face = 0; face < FACE_COUNT; face++) {
            double plane = axisOf(FACE_AXIS[face], cx, cy, cz) + FACE_SIDE[face] * halfExtent;
            int lo = Mth.floor(plane - SLAB);
            int hi = Mth.floor(plane + SLAB);
            same &= signature[face * 2] == lo && signature[face * 2 + 1] == hi;
            signature[face * 2] = lo;
            signature[face * 2 + 1] = hi;
        }
        return same;
    }

    private void resize(float halfExtent) {
        double span = 2.0 * halfExtent;
        float chosen = CELL_SIZES[CELL_SIZES.length - 1];
        int chosenSamples = MAX_SAMPLES;
        for (float candidate : CELL_SIZES) {
            int needed = (int) Math.ceil(span / candidate) + 2;
            if (needed <= MAX_SAMPLES) {
                chosen = candidate;
                chosenSamples = needed;
                break;
            }
        }
        if (chosen == cell && chosenSamples == samples && pixels != null) {
            return;
        }
        cell = chosen;
        samples = chosenSamples;
        tile = samples + 2;
        atlasWidth = COLS * tile;
        atlasHeight = ROWS * tile;
        pixels = BufferUtils.createByteBuffer(atlasWidth * atlasHeight);
        fine = new boolean[samples * samples];
        dist = new float[samples * samples];
        vecU = new float[samples * samples];
        vecV = new float[samples * samples];
        age = Integer.MAX_VALUE;
    }

    private void scanFace(Level level, int face, double cx, double cy, double cz, float half, BlockPos.MutableBlockPos pos) {
        int axis = FACE_AXIS[face];
        int uAxis = FACE_U[face];
        int vAxis = FACE_V[face];

        double plane = axisOf(axis, cx, cy, cz) + FACE_SIDE[face] * half;
        double uMin = originOf(uAxis);
        double vMin = originOf(vAxis);
        double span = samples * (double) cell;

        int planeLo = Mth.floor(plane - SLAB);
        int planeHi = Mth.floor(plane + SLAB);

        int blockU0 = Mth.floor(uMin);
        int blockV0 = Mth.floor(vMin);
        int blockW = Mth.floor(uMin + span) - blockU0 + 1;
        int blockH = Mth.floor(vMin + span) - blockV0 + 1;
        if (blocks == null || blocks.length < blockW * blockH) {
            blocks = new boolean[blockW * blockH];
        }

        for (int bv = 0; bv < blockH; bv++) {
            for (int bu = 0; bu < blockW; bu++) {
                boolean solid = solidAt(level, pos, axis, planeLo, blockU0 + bu, blockV0 + bv);
                if (!solid && planeHi != planeLo) {
                    solid = solidAt(level, pos, axis, planeHi, blockU0 + bu, blockV0 + bv);
                }
                blocks[bv * blockW + bu] = solid;
            }
        }

        for (int j = 0; j < samples; j++) {
            int bv = Mth.clamp(Mth.floor(vMin + (j + 0.5) * cell) - blockV0, 0, blockH - 1);
            for (int i = 0; i < samples; i++) {
                int bu = Mth.clamp(Mth.floor(uMin + (i + 0.5) * cell) - blockU0, 0, blockW - 1);
                fine[j * samples + i] = blocks[bv * blockW + bu];
            }
        }

        distanceTransform();
        writeTile(face);
    }

    private double originOf(int axis) {
        return axis == 0 ? originX : axis == 1 ? originY : originZ;
    }

    private static boolean solidAt(Level level, BlockPos.MutableBlockPos pos, int axis, int planeBlock, int u, int v) {
        switch (axis) {
            case 0 -> pos.set(planeBlock, u, v);
            case 1 -> pos.set(u, planeBlock, v);
            default -> pos.set(u, v, planeBlock);
        }
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static double axisOf(int axis, double x, double y, double z) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    private void distanceTransform() {
        int n = samples;
        int count = n * n;
        Arrays.fill(vecU, 0, count, INF);
        Arrays.fill(vecV, 0, count, INF);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                boolean occupied = fine[k];

                if (i > 0 && fine[k - 1] != occupied) {
                    seed(k, -ORTHO_SEED, 0.0f);
                }
                if (i + 1 < n && fine[k + 1] != occupied) {
                    seed(k, ORTHO_SEED, 0.0f);
                }
                if (j > 0 && fine[k - n] != occupied) {
                    seed(k, 0.0f, -ORTHO_SEED);
                }
                if (j + 1 < n && fine[k + n] != occupied) {
                    seed(k, 0.0f, ORTHO_SEED);
                }
                if (vecU[k] != INF) {
                    continue;
                }

                if (i > 0 && j > 0 && fine[k - n - 1] != occupied) {
                    seed(k, -DIAGONAL_SEED, -DIAGONAL_SEED);
                }
                if (i + 1 < n && j > 0 && fine[k - n + 1] != occupied) {
                    seed(k, DIAGONAL_SEED, -DIAGONAL_SEED);
                }
                if (i > 0 && j + 1 < n && fine[k + n - 1] != occupied) {
                    seed(k, -DIAGONAL_SEED, DIAGONAL_SEED);
                }
                if (i + 1 < n && j + 1 < n && fine[k + n + 1] != occupied) {
                    seed(k, DIAGONAL_SEED, DIAGONAL_SEED);
                }
            }
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                if (i > 0) {
                    relax(k, k - 1, -1.0f, 0.0f);
                }
                if (j > 0) {
                    relax(k, k - n, 0.0f, -1.0f);
                    if (i > 0) {
                        relax(k, k - n - 1, -1.0f, -1.0f);
                    }
                    if (i + 1 < n) {
                        relax(k, k - n + 1, 1.0f, -1.0f);
                    }
                }
            }
        }

        for (int j = n - 1; j >= 0; j--) {
            for (int i = n - 1; i >= 0; i--) {
                int k = j * n + i;
                if (i + 1 < n) {
                    relax(k, k + 1, 1.0f, 0.0f);
                }
                if (j + 1 < n) {
                    relax(k, k + n, 0.0f, 1.0f);
                    if (i + 1 < n) {
                        relax(k, k + n + 1, 1.0f, 1.0f);
                    }
                    if (i > 0) {
                        relax(k, k + n - 1, -1.0f, 1.0f);
                    }
                }
            }
        }

        for (int k = 0; k < count; k++) {
            float u = vecU[k];
            float v = vecV[k];
            dist[k] = u >= INF ? INF : (float) Math.sqrt(u * u + v * v) * cell;
        }
    }

    private void seed(int k, float u, float v) {
        if (u * u + v * v < vecU[k] * vecU[k] + vecV[k] * vecV[k]) {
            vecU[k] = u;
            vecV[k] = v;
        }
    }

    private void relax(int k, int m, float di, float dj) {
        if (vecU[m] >= INF) {
            return;
        }
        seed(k, vecU[m] + di, vecV[m] + dj);
    }

    private void writeTile(int face) {
        int originCol = (face % COLS) * tile;
        int originRow = (face / COLS) * tile;
        for (int j = -1; j <= samples; j++) {
            int sj = Mth.clamp(j, 0, samples - 1);
            int row = (originRow + 1 + j) * atlasWidth;
            for (int i = -1; i <= samples; i++) {
                int si = Mth.clamp(i, 0, samples - 1);
                float blocksAway = dist[sj * samples + si];
                int level = (int) (Mth.clamp(blocksAway / RANGE, 0.0f, 1.0f) * 255.0f + 0.5f);
                pixels.put(row + originCol + 1 + i, (byte) level);
            }
        }
    }

    private void upload() {
        if (textureId < 0) {
            textureId = TextureUtil.generateTextureId();
        }
        GlStateManager._bindTexture(textureId);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        pixels.position(0);
        pixels.limit(atlasWidth * atlasHeight);
        if (atlasWidth != allocatedWidth || atlasHeight != allocatedHeight) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, atlasWidth, atlasHeight, 0,
                GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, pixels);
            allocatedWidth = atlasWidth;
            allocatedHeight = atlasHeight;
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, atlasWidth, atlasHeight,
                GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, pixels);
        }
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
}

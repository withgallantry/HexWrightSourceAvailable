package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.server.dust.DustFormation;
import com.bluup.hexwright.server.dust.DustTuning;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

final class DustConstruct {

    static final int MAX_IMPACTS = 8;
    private static final int IMPACT_LIFE = 60;
    private static final float IMPACT_FADE = 14.0f;

    RegionField field;
    private double fillVolume;
    private double shellVolume;
    float agility = 1.0f;

    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private double driftVx;
    private double driftVy;
    private double driftVz;
    private Vec3 driftTarget = Vec3.ZERO;
    private @Nullable Vec3 destination;
    float migrating;
    float travelX;
    float travelY = 1.0f;
    float travelZ;

    double x;
    double y;
    double z;
    double prevX;
    double prevY;
    double prevZ;

    float bandCentre;
    float bandHalf;

    float build;
    boolean dissolving;
    @Nullable DustFormSound sound;
    float coverage;
    float age;
    final float dirX;
    final float dirY;
    final float dirZ;

    private final float[] colours = DustTint.AUTHORED.clone();
    private final float[] prevColours = DustTint.AUTHORED.clone();

    final float[] impactX = new float[MAX_IMPACTS];
    final float[] impactY = new float[MAX_IMPACTS];
    final float[] impactZ = new float[MAX_IMPACTS];
    final float[] impactRadius = new float[MAX_IMPACTS];
    final float[] impactStrength = new float[MAX_IMPACTS];
    final float[] impactRipple = new float[MAX_IMPACTS];
    final int[] impactAge = new int[MAX_IMPACTS];
    private int impactCursor;
    private int lastImpact = -1;

    private int texture;
    private RegionField.Grid uploaded;

    private final float[] scratch = new float[3];

    DustConstruct(RegionField field, double fromX, double fromY, double fromZ) {
        this.field = field;
        measure(field);
        this.x = field.centerX;
        this.y = field.centerY;
        this.z = field.centerZ;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        double dx = fromX - x;
        double dy = fromY - y;
        double dz = fromZ - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0e-3) {
            dirX = (float) (dx / length);
            dirY = (float) (dy / length);
            dirZ = (float) (dz / length);
        } else {
            dirX = 0.0f;
            dirY = 1.0f;
            dirZ = 0.0f;
        }
        java.util.Arrays.fill(impactAge, IMPACT_LIFE);
        bandCentre = targetBandCentre();
        bandHalf = targetBandHalf();
    }

    void rebind(RegionField next) {
        field = next;
        measure(next);
    }

    void drift(Vec3 drift, Vec3 velocity, Vec3 target, @Nullable Vec3 destination) {
        driftTarget = target;
        this.destination = destination;
        offsetX = drift.x;
        offsetY = drift.y;
        offsetZ = drift.z;
        driftVx = velocity.x;
        driftVy = velocity.y;
        driftVz = velocity.z;
    }

    private void measure(RegionField next) {
        DustFormation formation = new DustFormation(next.region);
        fillVolume = formation.fillVolume;
        shellVolume = formation.shellVolume;
        agility = (float) formation.agility;
    }

    void dissolve() {
        dissolving = true;
    }

    void tick(float mass, float arrival, boolean forming) {
        prevX = x;
        prevY = y;
        prevZ = z;
        Vec3 velocity = DustTuning.accelerate(driftVx, driftVy, driftVz, requestedVelocity(), agility);
        driftVx = velocity.x;
        driftVy = velocity.y;
        driftVz = velocity.z;
        offsetX += driftVx;
        offsetY += driftVy;
        offsetZ += driftVz;
        double speed = Math.sqrt(driftVx * driftVx + driftVy * driftVy + driftVz * driftVz);
        migrating = (float) Math.min(1.0, speed / Math.max(DustTuning.maxDriftSpeed, 1.0e-3));
        if (speed > 1.0e-4) {
            travelX = (float) (driftVx / speed);
            travelY = (float) (driftVy / speed);
            travelZ = (float) (driftVz / speed);
        }
        x = field.centerX + offsetX;
        y = field.centerY + offsetY;
        z = field.centerZ + offsetZ;
        age++;

        if (dissolving) {
            build = Math.max(0.0f, build - 1.0f / Math.max(DustConfig.constructDissolveTicks, 1.0f));
        } else if (forming) {
            float rate = (0.25f + 0.75f * arrival) * agility / Math.max(DustConfig.constructBuildTicks, 1.0f);
            build = Math.min(1.0f, build + rate);
        }

        if (!dissolving) {
            double volume = field.shell ? shellVolume : fillVolume;
            float strength = (float) DustTuning.strength(mass / Math.max(volume, 1.0e-3));
            float wanted = smoothstep(DustConfig.constructStrengthLow, DustConfig.constructStrengthHigh, strength);
            coverage += (wanted - coverage) * 0.06f;
            if (coverage > 0.995f) {
                coverage = 1.0f;
            }
        }

        bandCentre += (targetBandCentre() - bandCentre) * 0.12f;
        bandHalf += (targetBandHalf() - bandHalf) * 0.12f;

        for (int i = 0; i < MAX_IMPACTS; i++) {
            if (impactAge[i] < IMPACT_LIFE) {
                impactAge[i]++;
            }
        }
    }

    private float thickness() {
        float full = field.shell ? DustTuning.shellThickness
            : DustTuning.surfaceProud + DustConfig.constructFillDepth;
        float thin = Math.min(Math.max(DustConfig.constructThinnest, 0.1f), 1.0f);
        return full * (thin + (1.0f - thin) * coverage);
    }

    private float targetBandCentre() {
        return DustTuning.surfaceProud - thickness() * 0.5f;
    }

    private float targetBandHalf() {
        return thickness() * 0.5f;
    }

    boolean finished() {
        return dissolving && build <= 0.0f;
    }

    float stretch() {
        return 1.0f + DustConfig.constructStretch * migrating;
    }

    float solidity(float settled) {
        return build * coverage * settled;
    }

    double volume() {
        return Math.max(field.shell ? shellVolume : fillVolume, 1.0e-3);
    }

    Vec3 nextStep() {
        Vec3 velocity = DustTuning.accelerate(driftVx, driftVy, driftVz, requestedVelocity(), agility);
        double dx = field.centerX + offsetX + velocity.x - x;
        double dy = field.centerY + offsetY + velocity.y - y;
        double dz = field.centerZ + offsetZ + velocity.z - z;
        return dx * dx + dy * dy + dz * dz > 4.0 ? Vec3.ZERO : new Vec3(dx, dy, dz);
    }

    private Vec3 requestedVelocity() {
        return destination != null ? DustTuning.seek(offsetX, offsetY, offsetZ, destination, agility) : driftTarget;
    }

    boolean visible() {
        return build * coverage > 0.003f;
    }

    float outer() {
        return bandCentre + bandHalf;
    }

    float outerDistance(float px, float py, float pz, float[] normal) {
        float d = field.sample(px, py, pz, normal);
        if (field.shell) {
            float offset = d - bandCentre;
            if (offset < 0.0f) {
                normal[0] = -normal[0];
                normal[1] = -normal[1];
                normal[2] = -normal[2];
            }
            return Math.abs(offset) - bandHalf;
        }
        return d - outer();
    }

    float outerDistanceWorld(double wx, double wy, double wz, float[] normal) {
        return outerDistance((float) (wx - x), (float) (wy - y), (float) (wz - z), normal);
    }

    AABB worldBounds(double pad) {
        double px = field.halfX + outer() + pad;
        double py = field.halfY + outer() + pad;
        double pz = field.halfZ + outer() + pad;
        return new AABB(x - px, y - py, z - pz, x + px, y + py, z + pz);
    }


    void addImpact(float lx, float ly, float lz, float radius, float strength, boolean ripple) {
        int i = impactCursor;
        impactCursor = (impactCursor + 1) % MAX_IMPACTS;
        impactX[i] = lx;
        impactY[i] = ly;
        impactZ[i] = lz;
        impactRadius[i] = radius;
        impactStrength[i] = strength;
        impactRipple[i] = ripple ? 1.0f : 0.0f;
        impactAge[i] = 0;
        lastImpact = i;
    }

    boolean destabilize(float share, float pick, float[] out) {
        float strength = Math.min(0.2f + share * 6.0f, 0.8f);
        if (lastImpact >= 0 && impactAge[lastImpact] < 20) {
            impactStrength[lastImpact] = Math.min(1.0f, impactStrength[lastImpact] + strength * 0.5f);
            impactAge[lastImpact] = Math.min(impactAge[lastImpact], 4);
            return false;
        }
        if (!field.surfacePoint(pick, out, scratch)) {
            return false;
        }
        addImpact(out[0], out[1], out[2], 0.7f + strength, strength, false);
        return true;
    }

    void tint(int rgb) {
        System.arraycopy(colours, 0, prevColours, 0, colours.length);
        if (rgb < 0) {
            System.arraycopy(DustTint.AUTHORED, 0, colours, 0, colours.length);
        } else {
            DustTint.palette(rgb, colours);
        }
    }

    float colour(int i, float partialTick) {
        return prevColours[i] + (colours[i] - prevColours[i]) * partialTick;
    }

    float impactIntensity(int i) {
        int a = impactAge[i];
        if (a >= IMPACT_LIFE) {
            return 0.0f;
        }
        float fadeOut = 1.0f - smoothstep(IMPACT_LIFE * 0.6f, IMPACT_LIFE, a);
        return impactStrength[i] * (float) Math.exp(-a / IMPACT_FADE) * fadeOut;
    }


    int texture() {
        RegionField.Grid grid = field.grid;
        if (texture != 0 && uploaded == grid) {
            return texture;
        }
        if (texture == 0) {
            texture = GL11.glGenTextures();
        }
        int total = grid.nx * grid.ny * grid.nz;
        FloatBuffer data = MemoryUtil.memAllocFloat(total);
        try {
            data.put(grid.dist, 0, total).flip();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R32F, grid.nz, grid.ny, grid.nx, 0,
                GL11.GL_RED, GL11.GL_FLOAT, data);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        } finally {
            MemoryUtil.memFree(data);
        }
        uploaded = grid;
        return texture;
    }

    void dispose() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
            uploaded = null;
        }
    }

    static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0f : 0.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}

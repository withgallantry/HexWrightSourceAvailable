package com.bluup.hexwright.client.dust;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

final class RegionFlow implements DustFlowField {

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Vector3f noise = new Vector3f();
    private final float[] normal = new float[3];
    @Nullable RegionField field;

    @Override
    public void sample(DustFrame frame, DustGrain grain, Vector3f out) {
        RegionField shape = field;
        if (shape == null) {
            out.set(0.0f, 0.0f, 0.0f);
            return;
        }
        RegionField.Grid grid = shape.grid;
        float px = grain.x - frame.regionOffX;
        float py = grain.y - frame.regionOffY;
        float pz = grain.z - frame.regionOffZ;
        float max = DustConfig.regionMaxSpeed;
        float follow = DustConfig.regionFollow;

        int part = shape.partFor(grain.speed);
        if (grid.parts > 1 && shape.partAt(px, py, pz) != part) {
            float tx = grid.partX[part] - px;
            float ty = grid.partY[part] - py;
            float tz = grid.partZ[part] - pz;
            float distance = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            float speed = Math.min(max, distance * DustConfig.surfacePull) / Math.max(distance, 1.0e-3f);
            out.set(tx * speed + frame.regionVx * follow, ty * speed + frame.regionVy * follow,
                tz * speed + frame.regionVz * follow);
            return;
        }

        float d = shape.sample(px, py, pz, normal);
        float nx = normal[0];
        float ny = normal[1];
        float nz = normal[2];

        float calm = frame.formCalm;
        float solid = frame.constructSolidity;

        float agitation = 1.0f - calm * (1.0f - DustConfig.formedSwirlScale);
        float shoveX = 0.0f;
        float shoveY = 0.0f;
        float shoveZ = 0.0f;
        for (int i = 0; i < frame.impactCount; i++) {
            float ix = px - frame.impactX[i];
            float iy = py - frame.impactY[i];
            float iz = pz - frame.impactZ[i];
            float reach = frame.impactRadius[i] * 1.6f;
            float distSq = ix * ix + iy * iy + iz * iz;
            if (distSq >= reach * reach) {
                continue;
            }
            float dist = (float) Math.sqrt(distSq);
            float felt = frame.impactIntensity[i] * (1.0f - dist / reach);
            agitation = Math.max(agitation, felt);
            float shove = felt * 0.22f / Math.max(dist, 0.2f);
            shoveX += ix * shove;
            shoveY += iy * shove;
            shoveZ += iz * shove;
        }

        float depth = shape.shell
            ? grain.spread * DustConfig.shellDepth
            : (float) Math.pow(grain.spread, DustConfig.fillSurfaceBias) * grid.partDepth[part];
        if (solid > 0.0f) {
            float ride = frame.constructOuter + 0.04f + grain.spread * DustConfig.accentLayer;
            float accent = shape.shell && grain.lead < 0.5f ? ride : -ride;
            depth += (accent - depth) * solid;
        }
        float radialNow = (float) Math.sqrt(
            (px - grid.partX[part]) * (px - grid.partX[part]) + (pz - grid.partZ[part]) * (pz - grid.partZ[part]));
        float edge = OrbitFlow.clamp((radialNow / Math.max(grid.partRadial[part], 0.1f) - 0.6f) * 2.5f, 0.0f, 1.0f);
        float lift = 0.0f;
        if (calm > 0.0f && grain.lead > 1.0f - DustConfig.breakAwayShare * (1.0f + 2.0f * edge)) {
            float cycle = frame.time * DustConfig.breakAwayRate * (0.7f + 0.6f * grain.speed)
                + grain.phase / TWO_PI;
            cycle -= (float) Math.floor(cycle);
            if (cycle < 0.3f) {
                lift = (float) Math.sin(cycle / 0.3f * Math.PI) * DustConfig.breakAwayHeight * calm
                    * (0.6f + 0.8f * edge);
            }
        }
        depth -= lift;
        float along = -(d + depth) * DustConfig.surfacePull;
        along = OrbitFlow.clamp(along, -max, max);
        float vx = nx * along + shoveX;
        float vy = ny * along + shoveY - lift * 0.03f;
        float vz = nz * along + shoveZ;

        int axis = grid.partAxis[part];
        float ra = pick(axis, 0, px - grid.partX[part], py - grid.partY[part], pz - grid.partZ[part]);
        float rh = pick(axis, 1, px - grid.partX[part], py - grid.partY[part], pz - grid.partZ[part]);
        float rb = pick(axis, 2, px - grid.partX[part], py - grid.partY[part], pz - grid.partZ[part]);
        float na = pick(axis, 0, nx, ny, nz);
        float nh = pick(axis, 1, nx, ny, nz);
        float nb = pick(axis, 2, nx, ny, nz);
        float wa = 0.0f;
        float wh = 0.0f;
        float wb = 0.0f;

        float sa = rb;
        float sb = -ra;
        float dot = sa * na + sb * nb;
        float ta = sa - na * dot;
        float th = -nh * dot;
        float tb = sb - nb * dot;
        float tangentLength = (float) Math.sqrt(ta * ta + th * th + tb * tb);
        if (tangentLength > 1.0e-4f) {
            ta /= tangentLength;
            th /= tangentLength;
            tb /= tangentLength;
            float radial = (float) Math.sqrt(ra * ra + rb * rb);
            float speed = Math.min(DustConfig.swirlSpeed, radial * DustConfig.swirlMaxAngular)
                * (0.75f + 0.5f * grain.turbulence) * agitation;

            float ba = nh * tb - nb * th;
            float bh = nb * ta - na * tb;
            float bb = na * th - nh * ta;
            int streams = Math.max(DustConfig.swirlStreams, 1);
            int stream = Math.min((int) (grain.current * streams), streams - 1);
            float theta = (float) Math.atan2(rb, ra);
            float weave = (float) Math.sin(theta * streams + stream * (TWO_PI / streams)
                - frame.time * 0.06f + grain.phase * 0.25f) * DustConfig.swirlWeave;

            wa += (ta + ba * weave) * speed;
            wh += (th + bh * weave) * speed;
            wb += (tb + bb * weave) * speed;

            if (radial > 1.0e-3f) {
                float inward = DustConfig.swirlCentripetal * speed * speed / Math.max(radial, 0.3f)
                    / Math.max(DustConfig.steerRegion, 0.05f);
                float ia = -ra / radial;
                float ib = -rb / radial;
                float inDot = ia * na + ib * nb;
                wa += (ia - na * inDot) * inward;
                wh += (-nh * inDot) * inward;
                wb += (ib - nb * inDot) * inward;
            }
        }

        float preferred = (grain.height * 2.0f - 1.0f) * grid.partHalfAxis[part];
        float climb = OrbitFlow.clamp((preferred - rh) * DustConfig.heightPull, -0.2f, 0.2f);
        float upLength = (float) Math.sqrt(Math.max(1.0f - nh * nh, 0.0f));
        if (upLength > 0.05f) {
            climb /= upLength;
            wa += -na * nh * climb;
            wh += (1.0f - nh * nh) * climb;
            wb += -nb * nh * climb;
        }

        float radial = (float) Math.sqrt(ra * ra + rb * rb);
        float facing = nh * nh;
        if (facing > 0.05f && radial > 1.0e-3f) {
            float preferredRadial = (float) Math.sqrt(grain.radius) * grid.partRadial[part];
            float spread = OrbitFlow.clamp((preferredRadial - radial) * DustConfig.heightPull, -0.2f, 0.2f) * facing;
            float oa = ra / radial;
            float ob = rb / radial;
            float outDot = oa * na + ob * nb;
            wa += (oa - na * outDot) * spread;
            wh += (-nh * outDot) * spread;
            wb += (ob - nb * outDot) * spread;
        }

        vx += unpick(axis, 0, wa, wh, wb);
        vy += unpick(axis, 1, wa, wh, wb);
        vz += unpick(axis, 2, wa, wh, wb);

        DustFlowField.turbulence(frame, grain, noise);
        float turbulence = DustConfig.regionTurbulence * (0.5f + grain.turbulence) * Math.max(agitation, 0.3f);
        float drift = DustConfig.surfaceDrift * calm;
        float noiseDot = noise.x * nx + noise.y * ny + noise.z * nz;
        out.set(
            vx + noise.x * turbulence + (noise.x - nx * noiseDot) * drift + frame.regionVx * follow,
            vy + noise.y * turbulence + (noise.y - ny * noiseDot) * drift + frame.regionVy * follow,
            vz + noise.z * turbulence + (noise.z - nz * noiseDot) * drift + frame.regionVz * follow
        );
    }

    private static float pick(int axis, int slot, float x, float y, float z) {
        int world = (slot + axis + 2) % 3;
        return world == 0 ? x : world == 1 ? y : z;
    }

    private static float unpick(int axis, int world, float a, float h, float b) {
        int slot = (world - axis + 4) % 3;
        return slot == 0 ? a : slot == 1 ? h : b;
    }
}

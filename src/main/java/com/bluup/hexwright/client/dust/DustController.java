package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.mixin.TileParticleAccessor;
import com.bluup.hexwright.server.dust.DustSupport;
import com.bluup.hexwright.server.dust.DustTuning;
import com.bluup.hexwright.server.region.Region;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

final class DustController {

    enum Mode { FORMING, HOME, ATTACKING, RETURNING, DISPERSING }

    private static final ResourceLocation DUST_FX = Hexwright.id("dust");
    private static boolean warnedMissingFx;

    private static final int MISSING_GRACE_TICKS = 40;
    private static final double TELEPORT_DISTANCE_SQ = 16.0;
    private static final float WEIGHT_EPSILON = 1.0e-3f;
    private static final double NEWBORN_RADIUS_SQ = 6.25;
    private static final int MAX_BURSTS = 4;
    private static final int MAX_IMPACT_CHECKS = 24;
    private static final int ABSORB_PATIENCE = 60;

    final int entityId;

    private Mode mode = Mode.FORMING;
    private int modeAge;

    private final DustFrame frame = new DustFrame();
    private final DustGrain grain = new DustGrain();
    private final OrbitFlow orbit = new OrbitFlow();
    private final RegionFlow formation = new RegionFlow();
    private final AttackFlow attack = new AttackFlow();
    private final ReturnFlow returning = new ReturnFlow(orbit);
    private final DisperseFlow disperse = new DisperseFlow();
    private final Vector3f sample = new Vector3f();
    private final Vector3f sizeScratch = new Vector3f();
    private final Vector3f scratch = new Vector3f();

    private float orbitWeight = 1.0f;
    private float regionWeight;
    private float attackWeight;
    private float returnWeight;
    private float disperseWeight;
    private float steer = DustConfig.steerForming;

    private float attackReach;
    private float attackTopSpeed;
    private int attackDuration;
    private boolean skipWindup;

    private float mass = DustConfig.massReference;
    private float targetMass = DustConfig.massReference;
    private @Nullable RegionField shape;
    private boolean constrained;
    private int ticksSinceRegion;
    private boolean massKnown;

    private @Nullable DustConstruct construct;
    private final List<DustConstruct> dissolving = new ArrayList<>();
    private float settled = 1.0f;
    private float compacted;
    private int surplusTicks;
    private final Map<Integer, Integer> impactCooldowns = new HashMap<>();
    private final float[] probeNormal = new float[3];
    private final float[] surfacePoint = new float[3];
    private final float[] surfaceNormal = new float[3];
    private final double[] burstX = new double[MAX_BURSTS];
    private final double[] burstY = new double[MAX_BURSTS];
    private final double[] burstZ = new double[MAX_BURSTS];
    private final float[] burstNormal = new float[MAX_BURSTS * 3];
    private final int[] burstLeft = new int[MAX_BURSTS];
    private int pendingBurst;

    private boolean hasAnchor;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private int missingTicks;

    private @Nullable FX privateFx;
    private @Nullable Entity boundEntity;
    private @Nullable EntityEffect effect;
    private @Nullable FXRuntime runtime;
    private final List<ParticleEmitter> emitters = new ArrayList<>();
    private final List<Constant> emissionRates = new ArrayList<>();
    private float meanLifetime = DustConfig.fallbackLifetime;
    private float authoredSize;
    private float grainSize;
    private float resizeUntil;
    private boolean culled;

    private float steerMillis;

    private int liveGrains;
    private int fadingGrains;
    private int targetGrains;
    private float lod = 1.0f;
    private float envelopeFraction = 1.0f;

    DustController(int entityId) {
        this.entityId = entityId;
        frame.orbitRadiusScale = DustConfig.formingRadiusScale;
    }


    void manifest() {
        if (mode == Mode.DISPERSING) {
            setMode(Mode.FORMING);
        }
    }

    void release() {
        setMode(Mode.DISPERSING);
        dissolveConstruct();
    }

    void setMass(float current, float target) {
        float now = Math.max(current, 0.0f);
        float lost = mass - now;
        DustConstruct body = construct;
        if (massKnown && body != null && body.build > 0.5f && lost > Math.max(1.0f, target * 0.02f)) {
            float pick = (frame.time * 0.618034f) % 1.0f;
            if (body.destabilize(lost / Math.max(target, 1.0f), pick, surfacePoint)) {
                queueBurst(body, surfacePoint[0], surfacePoint[1], surfacePoint[2], 0.3f);
            }
        }
        mass = now;
        targetMass = Math.max(target, 0.0f);
        massKnown = true;
    }

    void setFormation(@Nullable CompoundTag regionTag, boolean constrain) {
        Region region = null;
        if (regionTag != null) {
            try {
                region = Region.load(regionTag);
            } catch (IllegalArgumentException malformed) {
                Hexwright.LOGGER.warn("Ignoring malformed dust formation for entity {}", entityId, malformed);
            }
        }
        if (region != null && region.isEmpty()) {
            region = null;
        }
        constrained = region != null && constrain;

        RegionField previous = shape;
        if (region == null) {
            if (previous != null) {
                shape = null;
                formation.field = null;
                frame.regionVx = 0.0f;
                frame.regionVy = 0.0f;
                frame.regionVz = 0.0f;
                dissolveConstruct();
                if (mode == Mode.HOME) {
                    setMode(Mode.RETURNING);
                }
            }
            return;
        }
        if (previous != null && previous.shell == constrained && previous.region.equals(region)) {
            return;
        }

        RegionField next = RegionField.build(region, constrained, previous);
        boolean quick = previous != null && ticksSinceRegion <= 20;
        if (quick) {
            float ticks = Math.max(ticksSinceRegion, 1);
            float vx = (float) (next.centerX - previous.centerX) / ticks;
            float vy = (float) (next.centerY - previous.centerY) / ticks;
            float vz = (float) (next.centerZ - previous.centerZ) / ticks;
            boolean jump = vx * vx + vy * vy + vz * vz > 4.0f;
            frame.regionVx = jump ? 0.0f : vx;
            frame.regionVy = jump ? 0.0f : vy;
            frame.regionVz = jump ? 0.0f : vz;
        }
        shape = next;
        formation.field = next;
        ticksSinceRegion = 0;
        updateConstruct(next, quick);
    }

    private void updateConstruct(RegionField next, boolean quick) {
        DustConstruct current = construct;
        if (current != null && current.field.grid == next.grid) {
            current.retarget(next);
            return;
        }
        if (current != null && quick && !current.dissolving && current.build > 0.2f) {
            current.reshape(next);
            return;
        }
        double fromX = current != null ? current.x : anchorX;
        double fromY = current != null ? current.y : anchorY;
        double fromZ = current != null ? current.z : anchorZ;
        dissolveConstruct();
        construct = new DustConstruct(next, fromX, fromY, fromZ);
    }

    private void dissolveConstruct() {
        DustConstruct current = construct;
        if (current != null) {
            current.dissolve();
            if (current.build > 0.0f) {
                dissolving.add(current);
            } else {
                current.dispose();
            }
            construct = null;
        }
    }

    void direct(double x, double y, double z, int durationTicks) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-6) {
            return;
        }
        frame.attackDirX = (float) (x / length);
        frame.attackDirY = (float) (y / length);
        frame.attackDirZ = (float) (z / length);
        attackReach = OrbitFlow.clamp((float) length, DustConfig.attackMinReach, DustConfig.attackMaxReach);
        attackTopSpeed = Math.min(DustConfig.attackBaseSpeed + attackReach * DustConfig.attackSpeedPerBlock,
            DustConfig.attackMaxAttackSpeed);
        frame.attackReach = attackReach;
        skipWindup = attackWeight > 0.5f || shape != null;
        attackDuration = Math.max(durationTicks, DustConfig.attackWindupTicks + 1);
        setMode(Mode.ATTACKING);
    }

    private void setMode(Mode next) {
        mode = next;
        modeAge = 0;
    }


    boolean tick(Minecraft mc, @Nullable Entity caster) {
        frame.time++;
        modeAge++;
        ticksSinceRegion++;

        if (caster == null || caster.isRemoved()) {
            if (++missingTicks > MISSING_GRACE_TICKS && mode != Mode.DISPERSING) {
                setMode(Mode.DISPERSING);
            }
            frame.casterVx = 0.0f;
            frame.casterVy = 0.0f;
            frame.casterVz = 0.0f;
            caster = null;
        } else {
            missingTicks = 0;
            updateAnchor(caster);
        }
        if (!hasAnchor) {
            return missingTicks < 200;
        }
        updateLod(mc, caster);
        if (culled) {
            destroyRuntime(true);
        } else if (caster != null && mode != Mode.DISPERSING) {
            ensureRuntime(caster);
        }
        if (runtime != null && !runtime.isAlive()) {
            forgetRuntime();
        }

        liveGrains = 0;
        for (ParticleEmitter emitter : emitters) {
            liveGrains += emitter.getParticleAmount();
        }
        updateConstructs(mc);
        updateFrameGeometry();
        targetGrains = loosePopulation();
        updateGrainSize();

        advanceMode();
        updateBlend();
        updatePopulation();
        if (!emitters.isEmpty()) {
            long started = System.nanoTime();
            steerGrains();
            steerMillis += ((System.nanoTime() - started) / 1.0e6f - steerMillis) * 0.1f;
        }

        if (mode == Mode.DISPERSING && modeAge > 1 && (runtime == null || liveGrains == 0)
            && construct == null && dissolving.isEmpty()) {
            destroyRuntime(true);
            return false;
        }
        return true;
    }


    private void updateConstructs(Minecraft mc) {
        float settleTarget = mode == Mode.ATTACKING || (mode == Mode.RETURNING && shape != null) ? 0.0f : 1.0f;
        float rate = DustTuning.formationRate;
        settled += OrbitFlow.clamp(settleTarget - settled, -rate, rate);

        boolean enabled = DustConstructRenderer.available();
        DustConstruct current = construct;
        float solid = 0.0f;
        if (current != null) {
            boolean home = mode == Mode.HOME || mode == Mode.FORMING;
            current.tick(mass, envelopeFraction, home);
            if (enabled && current.build > 0.3f && current.coverage > 0.05f) {
                detectImpacts(mc, current);
            }
            solid = current.solidity(settled);
        }
        for (Iterator<DustConstruct> it = dissolving.iterator(); it.hasNext(); ) {
            DustConstruct old = it.next();
            old.tick(mass, 0.0f, false);
            if (old.finished()) {
                old.dispose();
                it.remove();
            } else {
                solid += old.solidity(1.0f);
            }
        }
        compacted = enabled ? Math.min(solid, 1.0f) : 0.0f;

        frame.formCalm = current != null && enabled
            ? current.build * settled * (1.0f - 0.6f * current.migrating) : 0.0f;
        frame.constructSolidity = current != null && enabled ? current.solidity(settled) : 0.0f;
        frame.constructOuter = current != null ? current.outer() : 0.0f;
        int impacts = 0;
        if (current != null) {
            for (int i = 0; i < DustConstruct.MAX_IMPACTS; i++) {
                float intensity = current.impactIntensity(i);
                if (intensity > 0.01f) {
                    frame.impactX[impacts] = current.impactX[i];
                    frame.impactY[impacts] = current.impactY[i];
                    frame.impactZ[impacts] = current.impactZ[i];
                    frame.impactRadius[impacts] = current.impactRadius[i];
                    frame.impactIntensity[impacts] = intensity;
                    impacts++;
                }
            }
        }
        frame.impactCount = impacts;

        if (!impactCooldowns.isEmpty() && ((int) frame.time) % 40 == 0) {
            int now = (int) frame.time;
            impactCooldowns.values().removeIf(until -> until < now);
        }
    }

    private void detectImpacts(Minecraft mc, DustConstruct body) {
        if (mc.level == null) {
            return;
        }
        AABB area = body.worldBounds(1.0);
        List<Entity> nearby = mc.level.getEntities((Entity) null, area,
            entity -> entity.isAlive() && !entity.isSpectator());
        int now = (int) frame.time;
        int checked = 0;
        for (Entity entity : nearby) {
            if (++checked > MAX_IMPACT_CHECKS) {
                break;
            }
            Integer until = impactCooldowns.get(entity.getId());
            if (until != null && until > now) {
                continue;
            }
            double dx = entity.getX() - entity.xo;
            double dy = entity.getY() - entity.yo;
            double dz = entity.getZ() - entity.zo;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < 0.02) {
                continue;
            }
            double mid = entity.getBbHeight() * 0.5;
            float before = Math.min(body.outerDistanceWorld(entity.xo, entity.yo, entity.zo, probeNormal),
                body.outerDistanceWorld(entity.xo, entity.yo + mid, entity.zo, probeNormal));

            if (before > 0.08f && speed >= DustConfig.impactMinSpeed) {
                for (int step = 1; step <= 3; step++) {
                    double t = step / 3.0;
                    double x = entity.xo + dx * t;
                    double y = entity.yo + dy * t;
                    double z = entity.zo + dz * t;
                    double probeY = y;
                    float d = body.outerDistanceWorld(x, y, z, probeNormal);
                    if (d > 0.1f) {
                        d = body.outerDistanceWorld(x, y + mid, z, probeNormal);
                        probeY = y + mid;
                    }
                    if (d <= 0.1f) {
                        float strength = OrbitFlow.clamp((float) (speed - 0.08) * 2.2f, 0.15f, 1.0f);
                        float lx = (float) (x - body.x) - probeNormal[0] * d;
                        float ly = (float) (probeY - body.y) - probeNormal[1] * d;
                        float lz = (float) (z - body.z) - probeNormal[2] * d;
                        float radius = 0.5f + Math.min(entity.getBbWidth(), 1.5f) * 0.5f + strength * 0.6f;
                        body.addImpact(lx, ly, lz, radius, strength, !(entity instanceof Projectile));
                        queueBurst(body, lx, ly, lz, strength);
                        impactCooldowns.put(entity.getId(), now + DustConfig.impactCooldown);
                        break;
                    }
                }
                continue;
            }

            if (body.field.shell) {
                float cx = (float) (entity.getX() - body.x);
                float cy = (float) (entity.getY() + mid - body.y);
                float cz = (float) (entity.getZ() - body.z);
                float inside = body.field.sample(cx, cy, cz, probeNormal);
                if (inside < 0.0f && inside > -1.2f) {
                    double outward = dx * probeNormal[0] + dy * probeNormal[1] + dz * probeNormal[2];
                    if (outward > 0.04) {
                        float lx = cx - probeNormal[0] * inside;
                        float ly = cy - probeNormal[1] * inside;
                        float lz = cz - probeNormal[2] * inside;
                        body.addImpact(lx, ly, lz, 0.9f, 0.3f, false);
                        queueBurst(body, lx, ly, lz, 0.2f);
                        impactCooldowns.put(entity.getId(), now + DustConfig.impactCooldown + 4);
                    }
                }
            }
        }
    }

    private void queueBurst(DustConstruct body, float lx, float ly, float lz, float strength) {
        int grains = Math.round(DustConfig.impactBurstGrains * strength);
        if (grains <= 0) {
            return;
        }
        int slot = 0;
        for (int i = 0; i < MAX_BURSTS; i++) {
            if (burstLeft[i] <= 0) {
                slot = i;
                break;
            }
            if (burstLeft[i] < burstLeft[slot]) {
                slot = i;
            }
        }
        body.outerDistance(lx, ly, lz, probeNormal);
        burstX[slot] = body.x + lx;
        burstY[slot] = body.y + ly;
        burstZ[slot] = body.z + lz;
        burstNormal[slot * 3] = probeNormal[0];
        burstNormal[slot * 3 + 1] = probeNormal[1];
        burstNormal[slot * 3 + 2] = probeNormal[2];
        pendingBurst += grains - Math.max(burstLeft[slot], 0);
        burstLeft[slot] = grains;
    }

    private int loosePopulation() {
        int full = populationFor(mass, shape != null);
        if (full <= 0) {
            return 0;
        }
        float loose = 1.0f - compacted * (1.0f - DustConfig.constructAccentShare);
        return Math.max(DustConfig.minGrains, Math.round(full * loose));
    }

    private @Nullable DustConstruct emissionSource(int identity) {
        if (!dissolving.isEmpty()) {
            return dissolving.get(Math.floorMod(identity, dissolving.size()));
        }
        DustConstruct current = construct;
        if (current != null && (current.build > 0.5f || settled < 0.95f)) {
            return current;
        }
        return null;
    }

    @Nullable DustSupport.Surface surface(Level level) {
        DustConstruct body = construct;
        if (body == null || body.dissolving || mode == Mode.DISPERSING) {
            return null;
        }
        double hold = DustSupport.hold(DustTuning.strength(mass * settled / body.volume()));
        if (hold <= 0.0) {
            return null;
        }
        RegionField shape = body.field;
        return new DustSupport.Surface(level, shape.region,
            body.x - shape.centerX, body.y - shape.centerY, body.z - shape.centerZ, shape.shell,
            hold, body.travelX(), body.travelY(), body.travelZ(), entityId);
    }

    void collectConstructs(List<DustConstruct> into) {
        if (culled) {
            return;
        }
        DustConstruct current = construct;
        if (current != null && current.visible()) {
            into.add(current);
        }
        for (DustConstruct old : dissolving) {
            if (old.visible()) {
                into.add(old);
            }
        }
    }

    void disposeConstructs() {
        if (construct != null) {
            construct.dispose();
            construct = null;
        }
        for (DustConstruct old : dissolving) {
            old.dispose();
        }
        dissolving.clear();
    }

    private int populationFor(float dustMass, boolean formed) {
        if (dustMass <= 0.0f) {
            return 0;
        }
        float scale = (float) Math.sqrt(dustMass / Math.max(DustConfig.massReference, 1.0f));
        if (formed) {
            scale *= DustConfig.formationPopulationScale;
        }
        int grains = Math.round(DustConfig.quality.population * lod * scale);
        return Math.min(DustConfig.hardCap, Math.max(DustConfig.minGrains, grains));
    }

    private void updateGrainSize() {
        float size = authoredSize;
        RegionField formed = shape;
        if (formed != null && DustConfig.formationCoverage > 0.0f) {
            int population = Math.max(populationFor(targetMass, true), 1);
            float wanted = (float) Math.sqrt(DustConfig.formationCoverage * formed.grid.surfaceArea / population);
            size = OrbitFlow.clamp(wanted, DustConfig.grainMinSize, DustConfig.grainMaxSize);
        } else if (construct != null || !dissolving.isEmpty()) {
            size = OrbitFlow.clamp(authoredSize * DustConfig.looseGrainScale,
                DustConfig.grainMinSize, DustConfig.grainMaxSize);
        }
        grainSize = size * DustConfig.grainScale;
        if (Math.abs(grainSize - authoredSize) > 1.0e-4f) {
            resizeUntil = frame.time + meanLifetime * 2.0f + 20.0f;
        }
    }

    private void updateAnchor(Entity caster) {
        double x = caster.getX();
        double y = caster.getY() + DustConfig.anchorHeight;
        double z = caster.getZ();
        if (hasAnchor) {
            double dx = x - anchorX;
            double dy = y - anchorY;
            double dz = z - anchorZ;
            boolean teleported = dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQ;
            frame.casterVx = teleported ? 0.0f : (float) dx;
            frame.casterVy = teleported ? 0.0f : (float) dy;
            frame.casterVz = teleported ? 0.0f : (float) dz;
        }
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        hasAnchor = true;
    }

    private void updateFrameGeometry() {
        RegionField cloud = shape;
        DustConstruct body = construct;
        if (cloud != null) {
            double centreX = body != null ? body.x : cloud.centerX;
            double centreY = body != null ? body.y : cloud.centerY;
            double centreZ = body != null ? body.z : cloud.centerZ;
            frame.regionOffX = (float) (centreX - anchorX);
            frame.regionOffY = (float) (centreY - anchorY);
            frame.regionOffZ = (float) (centreZ - anchorZ);
            frame.attackOriginX = frame.regionOffX;
            frame.attackOriginY = frame.regionOffY;
            frame.attackOriginZ = frame.regionOffZ;
            frame.attackFollow = 0.0f;
            if (body != null) {
                frame.regionVx = (float) (body.x - body.prevX);
                frame.regionVy = (float) (body.y - body.prevY);
                frame.regionVz = (float) (body.z - body.prevZ);
            } else if (ticksSinceRegion > 1) {
                frame.regionVx *= 0.85f;
                frame.regionVy *= 0.85f;
                frame.regionVz *= 0.85f;
            }
        } else {
            frame.attackOriginX = 0.0f;
            frame.attackOriginY = 0.0f;
            frame.attackOriginZ = 0.0f;
            frame.attackFollow = DustConfig.attackFollow;
        }
    }

    private void updateLod(Minecraft mc, @Nullable Entity caster) {
        boolean own = caster != null && (caster == mc.player || caster == mc.getCameraEntity());
        if (own) {
            lod = 1.0f;
            culled = false;
            return;
        }
        double x = shape != null ? shape.centerX : anchorX;
        double y = shape != null ? shape.centerY : anchorY;
        double z = shape != null ? shape.centerZ : anchorZ;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        culled = culled ? distance > DustConfig.cullDistance - 8.0f : distance > DustConfig.cullDistance;
        if (distance <= DustConfig.lodNearDistance) {
            lod = 1.0f;
        } else if (distance <= DustConfig.lodMidDistance) {
            lod = DustConfig.lodMidFactor;
        } else {
            lod = DustConfig.lodFarFactor;
        }
    }

    private void advanceMode() {
        switch (mode) {
            case FORMING -> {
                if ((targetGrains > 0 && liveGrains >= targetGrains * 0.9f) || modeAge > 140) {
                    setMode(Mode.HOME);
                }
            }
            case ATTACKING -> {
                if (modeAge >= attackDuration) {
                    setMode(Mode.RETURNING);
                }
            }
            case RETURNING -> {
                if ((modeAge >= 10 && envelopeFraction >= DustConfig.reformedFraction)
                    || modeAge >= DustConfig.returnMaxTicks) {
                    setMode(Mode.HOME);
                }
            }
            default -> {
            }
        }
    }

    private void updateBlend() {
        boolean formed = shape != null;
        float orbitTarget = 0.0f;
        float regionTarget = 0.0f;
        float attackTarget = 0.0f;
        float returnTarget = 0.0f;
        float disperseTarget = 0.0f;
        float radiusTarget = 1.0f;
        float speedTarget = 1.0f;
        float shapeRate = 0.05f;
        float weightRate = 0.2f;
        float steerTarget = formed ? DustConfig.steerRegion : DustConfig.steerOrbit;

        switch (mode) {
            case FORMING -> {
                if (formed) {
                    regionTarget = 1.0f;
                } else {
                    orbitTarget = 1.0f;
                    steerTarget = DustConfig.steerForming;
                }
                shapeRate = 0.025f;
            }
            case HOME -> {
                if (formed) {
                    regionTarget = 1.0f;
                } else {
                    orbitTarget = 1.0f;
                }
            }
            case ATTACKING -> {
                int windup = skipWindup ? 0 : DustConfig.attackWindupTicks;
                radiusTarget = DustConfig.compressedRadiusScale;
                shapeRate = 0.35f;
                if (modeAge <= windup) {
                    orbitTarget = 1.0f;
                    speedTarget = DustConfig.compressedSpeedScale;
                    steerTarget = DustConfig.steerOrbit * 1.5f;
                } else {
                    attackTarget = 1.0f;
                    weightRate = 0.3f;
                    steerTarget = DustConfig.steerAttack;
                }
                float launch = OrbitFlow.clamp((modeAge - windup) / 6.0f, 0.0f, 1.0f);
                launch = launch * launch * (3.0f - 2.0f * launch);
                frame.attackSpeed = attackTopSpeed * (0.25f + 0.75f * launch);
            }
            case RETURNING -> {
                if (formed) {
                    regionTarget = 1.0f;
                } else {
                    returnTarget = 1.0f;
                }
                shapeRate = 0.04f;
                weightRate = 0.15f;
                steerTarget = DustConfig.steerReturn;
            }
            case DISPERSING -> {
                disperseTarget = 1.0f;
                steerTarget = DustConfig.steerDisperse;
            }
        }

        orbitWeight += (orbitTarget - orbitWeight) * weightRate;
        regionWeight += (regionTarget - regionWeight) * weightRate;
        attackWeight += (attackTarget - attackWeight) * weightRate;
        returnWeight += (returnTarget - returnWeight) * weightRate;
        disperseWeight += (disperseTarget - disperseWeight) * weightRate;
        frame.orbitRadiusScale += (radiusTarget - frame.orbitRadiusScale) * shapeRate;
        frame.orbitSpeedScale += (speedTarget - frame.orbitSpeedScale) * shapeRate;
        frame.orbitHeadAngle = (frame.orbitHeadAngle + OrbitFlow.angularSpeed(frame)) % (float) (Math.PI * 2.0);
        steer += (steerTarget - steer) * 0.2f;
    }

    private void updatePopulation() {
        if (emitters.isEmpty()) {
            return;
        }
        int perEmitterCap;
        float rate;
        if (mode == Mode.DISPERSING) {
            perEmitterCap = 0;
            rate = 0.0f;
        } else {
            perEmitterCap = Math.max(1, (targetGrains + pendingBurst) / emitters.size());
            float steady = targetGrains / Math.max(meanLifetime, 1.0f) * DustConfig.replenishFactor;
            int deficit = targetGrains - liveGrains;
            if (deficit <= 0) {
                rate = 0.0f;
            } else if (mode == Mode.FORMING && construct == null) {
                rate = Math.max(steady, targetGrains / Math.max(DustConfig.formFillTicks, 1.0f));
            } else if (mode == Mode.ATTACKING || !dissolving.isEmpty() || deficit > targetGrains * 0.5f) {
                rate = Math.max(steady, deficit * 0.2f);
            } else {
                rate = Math.min(deficit, steady + deficit * 0.05f);
            }
            rate += pendingBurst;
        }
        float perEmitterRate = rate / emitters.size();
        for (int i = 0; i < emitters.size(); i++) {
            emitters.get(i).config.setMaxParticles(perEmitterCap);
            emissionRates.get(i).setNumber(perEmitterRate);
        }
    }

    private void steerGrains() {
        float orbitW = orbitWeight > WEIGHT_EPSILON ? orbitWeight : 0.0f;
        float regionW = regionWeight > WEIGHT_EPSILON && shape != null ? regionWeight : 0.0f;
        float attackW = attackWeight > WEIGHT_EPSILON ? attackWeight : 0.0f;
        float returnW = returnWeight > WEIGHT_EPSILON ? returnWeight : 0.0f;
        float disperseW = disperseWeight > WEIGHT_EPSILON ? disperseWeight : 0.0f;
        float total = orbitW + regionW + attackW + returnW + disperseW;
        if (total < WEIGHT_EPSILON) {
            return;
        }
        orbitW /= total;
        regionW /= total;
        attackW /= total;
        returnW /= total;
        disperseW /= total;

        boolean resize = authoredSize > 0.0f
            && (Math.abs(grainSize - authoredSize) > 1.0e-4f || frame.time < resizeUntil);

        float steerNow = steer;
        float maxSpeedSq = DustConfig.maxSpeed * DustConfig.maxSpeed;
        RegionField cloud = shape;
        float envelope = DustConfig.orbitRadius * 1.6f * frame.orbitRadiusScale + 1.0f;
        float envelopeSq = envelope * envelope;
        int counted = 0;
        int inside = 0;

        int fadeTicks = Math.max(DustConfig.depletionFadeTicks, 1);
        int surplus = mode == Mode.DISPERSING ? 0 : liveGrains - fadingGrains - targetGrains;
        int toFade = surplus > targetGrains * 0.04f + 4 ? (surplus + 5) / 6 : 0;
        surplusTicks = toFade > 0 ? surplusTicks + 1 : 0;
        DustConstruct absorber = construct != null && construct.visible() && surplusTicks < ABSORB_PATIENCE
            ? construct : null;
        int fadeStride = toFade > 0 ? Math.max(1, liveGrains / (toFade * (absorber != null ? 4 : 1))) : Integer.MAX_VALUE;
        int fading = 0;

        int index = 0;

        for (ParticleEmitter emitter : emitters) {
            for (Queue<IParticle> queue : emitter.getParticles().values()) {
                for (IParticle particle : queue) {
                    if (!(particle instanceof TileParticle tile) || tile.isRemoved()) {
                        continue;
                    }
                    TileParticleAccessor raw = (TileParticleAccessor) tile;
                    Matrix4f toWorld = raw.hexwright$getInitialTransform();
                    Matrix4f toLocal = raw.hexwright$getInitialTransformInverse();
                    if (toWorld == null || toLocal == null) {
                        continue;
                    }
                    index++;
                    int identity = System.identityHashCode(tile);

                    toWorld.transformPosition(raw.hexwright$getLocalX(), raw.hexwright$getLocalY(),
                        raw.hexwright$getLocalZ(), scratch);
                    grain.x = (float) (scratch.x - anchorX);
                    grain.y = (float) (scratch.y - anchorY);
                    grain.z = (float) (scratch.z - anchorZ);
                    toWorld.transformDirection(raw.hexwright$getVelocityX(), raw.hexwright$getVelocityY(),
                        raw.hexwright$getVelocityZ(), scratch);
                    float vx = scratch.x;
                    float vy = scratch.y;
                    float vz = scratch.z;
                    int age = tile.getAge();
                    int lifetime = tile.getLifetime();

                    if (age <= 1 && mode != Mode.DISPERSING
                        && grain.x * grain.x + grain.y * grain.y + grain.z * grain.z < NEWBORN_RADIUS_SQ) {
                        float[] placed = placeNewborn(identity);
                        if (placed != null) {
                            grain.x = placed[0];
                            grain.y = placed[1];
                            grain.z = placed[2];
                            vx = placed[3];
                            vy = placed[4];
                            vz = placed[5];
                            toLocal.transformPosition((float) (anchorX + grain.x), (float) (anchorY + grain.y),
                                (float) (anchorZ + grain.z), scratch);
                            tile.setLocalPos(scratch.x, scratch.y, scratch.z, true);
                        }
                    }

                    if (lifetime > 0 && lifetime - age <= fadeTicks) {
                        fading++;
                    } else if (toFade > 0 && lifetime > fadeTicks && index % fadeStride == 0
                        && (absorber == null || age > 20 && absorber.outerDistance(grain.x - frame.regionOffX,
                            grain.y - frame.regionOffY, grain.z - frame.regionOffZ, probeNormal) < 0.5f)) {
                        tile.setAge(lifetime - fadeTicks);
                        age = lifetime - fadeTicks;
                        toFade--;
                        fading++;
                    }

                    grain.vx = vx;
                    grain.vy = vy;
                    grain.vz = vz;
                    grain.life = lifetime > 0 ? age / (float) lifetime : 0.0f;
                    grain.load(identity);

                    if (resize) {
                        float size = grainSize * (0.75f + 0.5f * grain.radius);
                        sizeScratch.set(size, size, size);
                        Vector3f initial = raw.hexwright$getInitialSize();
                        if (initial != null) {
                            initial.set(sizeScratch);
                        }
                        tile.setSize(sizeScratch);
                    }

                    float dx = 0.0f;
                    float dy = 0.0f;
                    float dz = 0.0f;
                    if (orbitW > 0.0f) {
                        orbit.sample(frame, grain, sample);
                        dx += sample.x * orbitW;
                        dy += sample.y * orbitW;
                        dz += sample.z * orbitW;
                    }
                    if (regionW > 0.0f) {
                        formation.sample(frame, grain, sample);
                        dx += sample.x * regionW;
                        dy += sample.y * regionW;
                        dz += sample.z * regionW;
                    }
                    if (attackW > 0.0f) {
                        attack.sample(frame, grain, sample);
                        dx += sample.x * attackW;
                        dy += sample.y * attackW;
                        dz += sample.z * attackW;
                    }
                    if (returnW > 0.0f) {
                        returning.sample(frame, grain, sample);
                        dx += sample.x * returnW;
                        dy += sample.y * returnW;
                        dz += sample.z * returnW;
                    }
                    if (disperseW > 0.0f) {
                        disperse.sample(frame, grain, sample);
                        dx += sample.x * disperseW;
                        dy += sample.y * disperseW;
                        dz += sample.z * disperseW;
                    }

                    vx += (dx - vx) * steerNow;
                    vy += (dy - vy) * steerNow;
                    vz += (dz - vz) * steerNow;
                    float speedSq = vx * vx + vy * vy + vz * vz;
                    if (speedSq > maxSpeedSq) {
                        float scale = DustConfig.maxSpeed / (float) Math.sqrt(speedSq);
                        vx *= scale;
                        vy *= scale;
                        vz *= scale;
                    }

                    toLocal.transformDirection(vx, vy, vz, scratch);
                    raw.hexwright$setVelocityX(scratch.x);
                    raw.hexwright$setVelocityY(scratch.y);
                    raw.hexwright$setVelocityZ(scratch.z);

                    counted++;
                    if (cloud != null) {
                        if (Math.abs(grain.x - frame.regionOffX) < cloud.halfX + 1.5f
                            && Math.abs(grain.y - frame.regionOffY) < cloud.halfY + 1.5f
                            && Math.abs(grain.z - frame.regionOffZ) < cloud.halfZ + 1.5f) {
                            inside++;
                        }
                    } else if (grain.x * grain.x + grain.y * grain.y + grain.z * grain.z < envelopeSq) {
                        inside++;
                    }
                }
            }
        }
        envelopeFraction = counted == 0 ? 1.0f : inside / (float) counted;
        fadingGrains = fading;
    }

    private final float[] placed = new float[6];

    private @Nullable float[] placeNewborn(int identity) {
        grain.load(identity * 0x2C1B3C6D);
        if (pendingBurst > 0) {
            for (int i = 0; i < MAX_BURSTS; i++) {
                if (burstLeft[i] <= 0) {
                    continue;
                }
                burstLeft[i]--;
                pendingBurst--;
                float nx = burstNormal[i * 3];
                float ny = burstNormal[i * 3 + 1];
                float nz = burstNormal[i * 3 + 2];
                float kick = 0.1f + 0.2f * grain.speed;
                placed[0] = (float) (burstX[i] - anchorX) + (grain.radius - 0.5f) * 0.3f;
                placed[1] = (float) (burstY[i] - anchorY) + (grain.height - 0.5f) * 0.3f;
                placed[2] = (float) (burstZ[i] - anchorZ) + (grain.current - 0.5f) * 0.3f;
                placed[3] = nx * kick + (grain.turbulence - 0.5f) * 0.12f;
                placed[4] = ny * kick + (grain.spread - 0.5f) * 0.12f + 0.04f;
                placed[5] = nz * kick + (grain.lead - 0.5f) * 0.12f;
                return placed;
            }
            pendingBurst = 0;
        }
        DustConstruct source = emissionSource(identity);
        if (source == null || !source.field.surfacePoint(grain.radius, surfacePoint, surfaceNormal)) {
            return null;
        }
        float lift = source.outer() + 0.05f + grain.spread * 0.1f;
        placed[0] = (float) (source.x - anchorX) + surfacePoint[0] + surfaceNormal[0] * lift;
        placed[1] = (float) (source.y - anchorY) + surfacePoint[1] + surfaceNormal[1] * lift;
        placed[2] = (float) (source.z - anchorZ) + surfacePoint[2] + surfaceNormal[2] * lift;
        float peel = source.dissolving || settled < 0.95f ? 0.05f + 0.07f * grain.speed : 0.0f;
        placed[3] = surfaceNormal[0] * peel;
        placed[4] = surfaceNormal[1] * peel;
        placed[5] = surfaceNormal[2] * peel;
        return placed;
    }


    private void ensureRuntime(Entity caster) {
        if (runtime != null && boundEntity == caster) {
            return;
        }
        if (runtime != null) {
            destroyRuntime(false);
        }
        FX fx = privateFx();
        if (fx == null) {
            return;
        }
        EntityEffect started = new EntityEffect(fx, caster.level(), caster, EntityEffect.AutoRotate.NONE);
        started.setAllowMulti(true);
        started.setOffset(new Vector3f(0.0f, DustConfig.anchorHeight, 0.0f));
        try {
            started.start();
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Failed to start dust effect for entity {}", entityId, e);
            return;
        }
        FXRuntime created = started.getRuntime();
        if (created == null) {
            return;
        }
        effect = started;
        runtime = created;
        boundEntity = caster;
        emitters.clear();
        emissionRates.clear();
        for (IFXObject object : created.getObjects().values()) {
            if (object instanceof ParticleEmitter emitter) {
                Constant rate = configure(emitter.config);
                emitters.add(emitter);
                emissionRates.add(rate);
            }
        }
        if (emitters.isEmpty()) {
            Hexwright.LOGGER.warn("{} has no particle emitter; dust has nothing to steer", DUST_FX);
        }
    }

    private Constant configure(ParticleConfig config) {
        config.setSimulationSpace(ParticleConfig.Space.World);
        config.setLooping(true);
        config.velocityOverLifetime.setEnable(false);
        config.forceOverLifetime.setEnable(false);
        config.inheritVelocity.setEnable(false);
        config.physics.setEnable(false);
        config.renderer.getCull().setEnable(false);
        config.emission.setBursts(new ArrayList<>());
        config.emission.setEmissionMode(EmissionSetting.Mode.Random);
        Constant rate = new Constant(0.0f);
        config.emission.setEmissionRate(rate);
        config.setMaxParticles(0);
        meanLifetime = meanOf(config.getStartLifetime());
        authoredSize = meanSizeOf(config.getStartSize());
        return rate;
    }

    private static float meanSizeOf(NumberFunction3 size) {
        RandomSource random = RandomSource.create(0x5EED);
        float total = 0.0f;
        int samples = 32;
        for (int i = 0; i < samples; i++) {
            Vector3f drawn = size.get(random, 0.0f);
            total += (drawn.x + drawn.y) * 0.5f;
        }
        return total / samples;
    }

    private static float meanOf(NumberFunction function) {
        if (function instanceof Constant constant) {
            return constant.getNumber().floatValue();
        }
        if (function instanceof RandomConstant random) {
            return (random.getA().floatValue() + random.getB().floatValue()) * 0.5f;
        }
        return DustConfig.fallbackLifetime;
    }

    private @Nullable FX privateFx() {
        if (privateFx != null) {
            return privateFx;
        }
        FX base = FXHelper.getFX(DUST_FX);
        if (base == null) {
            if (!warnedMissingFx) {
                warnedMissingFx = true;
                Hexwright.LOGGER.warn("Photon effect {} not found (expected assets/hexwright/fx/dust.fx); "
                    + "controlled dust will be invisible", DUST_FX);
            }
            return null;
        }
        FX copy = new FX();
        copy.deserializeNBT(base.serializeNBT());
        copy.setFxLocation(base.getFxLocation());
        privateFx = copy;
        return copy;
    }

    private void destroyRuntime(boolean forced) {
        if (runtime != null) {
            runtime.destroy(forced);
        }
        forgetRuntime();
    }

    void forgetRuntime() {
        if (effect != null && boundEntity != null) {
            List<EntityEffect> cached = EntityEffect.CACHE.get(boundEntity);
            if (cached != null) {
                cached.remove(effect);
                if (cached.isEmpty()) {
                    EntityEffect.CACHE.remove(boundEntity);
                }
            }
        }
        effect = null;
        runtime = null;
        boundEntity = null;
        emitters.clear();
        emissionRates.clear();
    }

    void discard() {
        destroyRuntime(true);
        disposeConstructs();
    }


    Mode mode() {
        return mode;
    }

    int liveGrains() {
        return liveGrains;
    }

    int targetGrains() {
        return targetGrains;
    }

    float steerMillis() {
        return steerMillis;
    }

    float lod() {
        return lod;
    }

    boolean culled() {
        return culled;
    }

    boolean hasRuntime() {
        return runtime != null;
    }

    String describeState() {
        String home = shape == null ? "orbit" : (constrained ? "constrained " : "formed ") + shape.region.describe();
        String body = "";
        if (construct != null) {
            double density = mass * settled / construct.volume();
            body = String.format(", construct build %.2f coverage %.2f, density %.1f in %.0f (%s), hold %.2f",
                construct.build, construct.coverage, density, construct.volume(),
                shape != null && shape.shell ? "shell" : "fill",
                DustSupport.hold(DustTuning.strength(density)));
        }
        return String.format("mass %.0f/%.0f, %s%s, compacted %.2f%s", mass, targetMass, home, body, compacted,
            dissolving.isEmpty() ? "" : ", " + dissolving.size() + " dissolving");
    }

    static boolean isFxLoadable() {
        return FXHelper.getFX(DUST_FX) != null;
    }
}

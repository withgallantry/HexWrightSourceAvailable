package com.bluup.hexwright.server.dust;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.MediaHelper;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.region.Region;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DustBody {

    enum Phase { HOME, SURGE, RETURN }

    public enum Awaited {
        LASH,
        ARRIVAL
    }

    public record Awaiting(LivingEntity caster, Awaited event, int homeSerial, List<Iota> hex, CastingImage image) {
    }

    public static final int MAX_AWAITING = 16;

    final LivingEntity caster;

    double targetMass;
    double mass;

    @Nullable DustFormation formation;
    boolean constrained;
    private int captureTimer;
    private static final int CAPTURE_INTERVAL = 5;
    private final List<Entity> captured = new ArrayList<>();

    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private double driftVx;
    private double driftVy;
    private double driftVz;
    private Vec3 driftTarget = Vec3.ZERO;
    private @Nullable Vec3 destination;
    private int driftSyncTimer;
    private static final int DRIFT_SYNC_INTERVAL = 20;
    private int formationSerial;
    private int homeSerial;
    private boolean lastCommandThrow;
    private boolean lashed;
    private final List<Awaiting> awaiting = new ArrayList<>();
    private double homeX;
    private double homeY;
    private double homeZ;
    private boolean hasHome;
    private double homeVx;
    private double homeVy;
    private double homeVz;

    private Phase phase = Phase.HOME;
    private int phaseAge;
    private double settled = 1.0;
    private double slugX;
    private double slugY;
    private double slugZ;
    private double slugPrevX;
    private double slugPrevY;
    private double slugPrevZ;
    private double slugVx;
    private double slugVy;
    private double slugVz;
    private double surgeDirX;
    private double surgeDirY;
    private double surgeDirZ;
    private double surgeReach;
    private double surgeSpeed;
    private int surgeDuration;

    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double casterVx;
    private double casterVy;
    private double casterVz;
    private boolean hasAnchor;

    private int replenishTimer;
    private float lastSentMass = -1.0f;
    private int massSyncCooldown;

    private final double[] reaction = new double[3];
    private final double[] normal = new double[3];

    private final DustPhysics.Shape formationShape = this::formationDistance;
    private final DustPhysics.Shape formationMaterial = this::formationMaterialDistance;
    private final DustPhysics.Shape orbitShape = this::orbitDistance;
    private final DustPhysics.Shape slugShape = this::slugDistance;

    private @Nullable DustSupport.Surface surface;

    DustBody(LivingEntity caster, double targetMass) {
        this.caster = caster;
        this.targetMass = targetMass;
        this.mass = targetMass;
        updateAnchor();
        updateHome();
    }


    void setTarget(double target) {
        targetMass = target;
        if (mass > target) {
            mass = target;
        }
        syncState();
    }

    void direct(Vec3 vector) {
        double length = vector.length();
        surgeDirX = vector.x / length;
        surgeDirY = vector.y / length;
        surgeDirZ = vector.z / length;
        surgeReach = Math.max(DustTuning.surgeMinReach, Math.min(length, DustTuning.surgeMaxReach));
        surgeSpeed = Math.min(DustTuning.surgeBaseSpeed + surgeReach * DustTuning.surgeSpeedPerBlock,
            DustTuning.surgeMaxSpeed);
        surgeDuration = DustTuning.SURGE_DURATION_TICKS;
        lastCommandThrow = true;
        if (phase == Phase.HOME) {
            slugX = homeX;
            slugY = homeY;
            slugZ = homeZ;
            slugVx = homeVx;
            slugVy = homeVy;
            slugVz = homeVz;
            slugPrevX = slugX;
            slugPrevY = slugY;
            slugPrevZ = slugZ;
        }
        phase = Phase.SURGE;
        phaseAge = 0;
        HexwrightNetworking.broadcastDust(caster, DustPacket.direct(caster.getId(), vector, surgeDuration));
    }

    void form(Region region) {
        boolean holding = formation != null && formation.region.equals(region)
            && offsetX == 0.0 && offsetY == 0.0 && offsetZ == 0.0;
        if (!holding) {
            unsettle();
            formation = new DustFormation(region);
            formationSerial++;
            homeSerial++;
            captured.clear();
            surface = null;
        }
        lastCommandThrow = false;
        stopDrift();
        syncState();
    }

    void impel(Vec3 velocity) {
        if (formation == null) {
            return;
        }
        if (destination != null) {
            destination = null;
            homeSerial++;
        }
        driftTarget = velocity;
        surface = buildSurface();
        syncState();
    }

    void move(Vec3 center) {
        if (formation == null) {
            return;
        }
        destination = new Vec3(center.x - formation.centerX, center.y - formation.centerY,
            center.z - formation.centerZ);
        driftTarget = Vec3.ZERO;
        homeSerial++;
        lastCommandThrow = false;
        surface = buildSurface();
        syncState();
    }

    private void stopDrift() {
        offsetX = 0.0;
        offsetY = 0.0;
        offsetZ = 0.0;
        driftVx = 0.0;
        driftVy = 0.0;
        driftVz = 0.0;
        driftTarget = Vec3.ZERO;
        destination = null;
    }

    private boolean drifting() {
        return driftVx != 0.0 || driftVy != 0.0 || driftVz != 0.0 || driftTarget.lengthSqr() > 0.0
            || destination != null;
    }

    private Vec3 requestedVelocity(DustFormation current) {
        return destination != null
            ? DustTuning.seek(offsetX, offsetY, offsetZ, destination, current.agility)
            : driftTarget;
    }

    private void finishMove() {
        if (destination == null) {
            return;
        }
        double dx = destination.x - offsetX;
        double dy = destination.y - offsetY;
        double dz = destination.z - offsetZ;
        if (dx * dx + dy * dy + dz * dz > 1.0e-6
            || driftVx * driftVx + driftVy * driftVy + driftVz * driftVz > 1.0e-8) {
            return;
        }
        offsetX = destination.x;
        offsetY = destination.y;
        offsetZ = destination.z;
        driftVx = 0.0;
        driftVy = 0.0;
        driftVz = 0.0;
        destination = null;
        syncState();
    }

    private boolean arrived() {
        return phase == Phase.HOME && settled >= 1.0 && destination == null;
    }

    void recall() {
        if (formation == null) {
            return;
        }
        double dx = homeX - anchorX;
        double dy = homeY - anchorY;
        double dz = homeZ - anchorZ;
        if (phase == Phase.HOME && dx * dx + dy * dy + dz * dz > 9.0) {
            unsettle();
        }
        formation = null;
        homeSerial++;
        lastCommandThrow = false;
        constrained = false;
        captured.clear();
        surface = null;
        stopDrift();
        syncState();
    }

    private void unsettle() {
        if (phase == Phase.HOME) {
            slugX = homeX;
            slugY = homeY;
            slugZ = homeZ;
            slugPrevX = slugX;
            slugPrevY = slugY;
            slugPrevZ = slugZ;
            slugVx = homeVx;
            slugVy = homeVy;
            slugVz = homeVz;
            phase = Phase.RETURN;
            phaseAge = 0;
        }
        settled = 0.0;
    }

    void setConstrained(boolean value) {
        if (value && formation == null) {
            return;
        }
        constrained = value;
        captured.clear();
        if (value) {
            capture();
        }
        syncState();
    }

    void changedWorld() {
        formation = null;
        homeSerial++;
        lastCommandThrow = false;
        awaiting.clear();
        constrained = false;
        captured.clear();
        surface = null;
        stopDrift();
        phase = Phase.HOME;
        settled = 1.0;
        hasAnchor = false;
        hasHome = false;
        updateAnchor();
        updateHome();
    }

    boolean isCaptured(Entity entity) {
        return captured.contains(entity);
    }


    @Nullable Awaited pending() {
        if (lastCommandThrow) {
            return phase == Phase.SURGE ? Awaited.LASH : null;
        }
        return arrived() ? null : Awaited.ARRIVAL;
    }

    boolean canAwait() {
        return awaiting.size() < MAX_AWAITING;
    }

    void await(List<Iota> hex, CastingImage image) {
        Awaited event = pending();
        awaiting.add(new Awaiting(caster, event == null ? Awaited.ARRIVAL : event, homeSerial, hex, image));
    }

    void collectDue(List<Awaiting> into) {
        boolean home = arrived();
        Iterator<Awaiting> it = awaiting.iterator();
        while (it.hasNext()) {
            Awaiting next = it.next();
            boolean due;
            if (next.event() == Awaited.LASH) {
                due = lashed;
            } else if (next.homeSerial() != homeSerial) {
                it.remove();
                continue;
            } else {
                due = home;
            }
            if (due) {
                it.remove();
                into.add(next);
            }
        }
    }


    void tick(ServerLevel level) {
        updateAnchor();
        updateHome();
        updateSurge();

        if (formation != null && !withinLeash()) {
            recall();
        }
        if (drifting() && ++driftSyncTimer >= DRIFT_SYNC_INTERVAL) {
            driftSyncTimer = 0;
            syncState();
        }

        double spent = 0.0;
        double homeMass = mass * settled;
        if (homeMass > 1.0e-3) {
            if (formation != null) {
                double volume = formation.volume(constrained);
                AABB area = formation.bounds.move(offsetX, offsetY, offsetZ);
                double heft = DustTuning.heft(homeMass);
                if (constrained) {
                    spent += DustPhysics.contact(this, level, area, formationMaterial, true, false,
                        formationShape, DustTuning.strength(homeMass / volume), heft,
                        homeVx, homeVy, homeVz, null);
                    if (++captureTimer >= CAPTURE_INTERVAL) {
                        captureTimer = 0;
                        capture();
                    }
                } else {
                    spent += DustPhysics.contact(this, level, area, formationMaterial, false, false,
                        null, DustTuning.strength(homeMass / volume), heft, homeVx, homeVy, homeVz,
                        null);
                }
                spent += bearing(level);
            } else if (DustTuning.orbitContact != 0) {
                double outer = DustTuning.orbitOuterRadius;
                double inner = DustTuning.orbitInnerRadius;
                double volume = 4.0 / 3.0 * Math.PI * (outer * outer * outer - inner * inner * inner);
                AABB area = new AABB(anchorX - outer, anchorY - outer, anchorZ - outer,
                    anchorX + outer, anchorY + outer, anchorZ + outer);
                spent += DustPhysics.contact(this, level, area, orbitShape, false, true, null,
                    DustTuning.strength(homeMass / volume * DustTuning.orbitDensityScale), 1.0,
                    casterVx, casterVy, casterVz, null);
            }
        }

        double slugMass = mass * (1.0 - settled);
        if (phase != Phase.HOME && slugMass > 1.0e-3) {
            double r = DustTuning.surgeRadius;
            double volume = Math.max(4.0 / 3.0 * Math.PI * r * r * r, DustTuning.minVolume);
            AABB area = new AABB(Math.min(slugX, slugPrevX) - r, Math.min(slugY, slugPrevY) - r,
                Math.min(slugZ, slugPrevZ) - r, Math.max(slugX, slugPrevX) + r,
                Math.max(slugY, slugPrevY) + r, Math.max(slugZ, slugPrevZ) + r);
            reaction[0] = 0.0;
            reaction[1] = 0.0;
            reaction[2] = 0.0;
            spent += DustPhysics.contact(this, level, area, slugShape, false, true, null,
                DustTuning.strength(slugMass / volume), 1.0, slugVx, slugVy, slugVz, reaction);
            double share = 2.0 / Math.max(slugMass, 2.0);
            slugVx += reaction[0] * share;
            slugVy += reaction[1] * share;
            slugVz += reaction[2] * share;
        }

        if (constrained && formation != null && !captured.isEmpty()) {
            double strength = DustTuning.strength(homeMass / formation.shellVolume);
            for (int i = captured.size() - 1; i >= 0; i--) {
                Entity entity = captured.get(i);
                if (entity.isRemoved() || !entity.isAlive() || entity.level() != level
                    || DustPhysics.negated(entity)) {
                    captured.remove(i);
                    continue;
                }
                double cost = DustPhysics.constrain(entity, formationShape, formation,
                    offsetX, offsetY, offsetZ, strength, homeVx, homeVy, homeVz, normal);
                if (cost < 0.0) {
                    captured.remove(i);
                } else {
                    spent += cost;
                }
            }
        }

        if (spent > 0.0) {
            mass = Math.max(0.0, mass - spent);
        }
        replenish();
        syncMass();
        surface = buildSurface();
    }


    @Nullable DustSupport.Surface surface() {
        return surface;
    }

    private @Nullable DustSupport.Surface buildSurface() {
        DustFormation current = formation;
        Level level = caster.level();
        if (current == null) {
            return null;
        }
        double homeMass = mass * settled;
        double hold = DustSupport.hold(DustTuning.strength(homeMass / current.volume(constrained)));
        if (hold <= 0.0) {
            return null;
        }
        Vec3 step = DustTuning.accelerate(driftVx, driftVy, driftVz, requestedVelocity(current), current.agility);
        return new DustSupport.Surface(level, current.region, offsetX, offsetY, offsetZ, constrained,
            hold, step.x, step.y, step.z, caster.getId());
    }

    private double bearing(ServerLevel level) {
        DustSupport.Surface current = surface;
        if (current == null || DustTuning.massPerBearing <= 0.0f) {
            return 0.0;
        }
        AABB area = formation.bounds.move(offsetX, offsetY, offsetZ)
            .inflate(DustFormation.surfaceReach() + 1.0);
        double spent = 0.0;
        int counted = 0;
        for (Entity entity : level.getEntities((Entity) null, area, DustSupport::bearable)) {
            if (counted >= DustTuning.maxBorne) {
                break;
            }
            double held = current.bearing(entity, normal);
            if (held <= 0.0) {
                continue;
            }
            counted++;
            spent += held * Math.max(DustPhysics.inertia(entity), 1.0) * DustTuning.massPerBearing;
        }
        return spent;
    }

    private void updateAnchor() {
        double x = caster.getX();
        double y = caster.getY() + DustTuning.anchorHeight;
        double z = caster.getZ();
        if (hasAnchor) {
            double dx = x - anchorX;
            double dy = y - anchorY;
            double dz = z - anchorZ;
            boolean teleported = dx * dx + dy * dy + dz * dz > 16.0;
            casterVx = teleported ? 0.0 : dx;
            casterVy = teleported ? 0.0 : dy;
            casterVz = teleported ? 0.0 : dz;
        }
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        hasAnchor = true;
    }

    private void updateHome() {
        double x;
        double y;
        double z;
        if (formation != null) {
            Vec3 velocity = DustTuning.accelerate(driftVx, driftVy, driftVz, requestedVelocity(formation),
                formation.agility);
            driftVx = velocity.x;
            driftVy = velocity.y;
            driftVz = velocity.z;
            offsetX += driftVx;
            offsetY += driftVy;
            offsetZ += driftVz;
            finishMove();
            x = formation.centerX + offsetX;
            y = formation.centerY + offsetY;
            z = formation.centerZ + offsetZ;
        } else {
            x = anchorX;
            y = anchorY;
            z = anchorZ;
        }
        if (hasHome) {
            double vx = x - homeX;
            double vy = y - homeY;
            double vz = z - homeZ;
            if (vx * vx + vy * vy + vz * vz > 4.0) {
                vx = 0.0;
                vy = 0.0;
                vz = 0.0;
            }
            homeVx = vx;
            homeVy = vy;
            homeVz = vz;
        }
        homeX = x;
        homeY = y;
        homeZ = z;
        hasHome = true;
    }

    private void updateSurge() {
        phaseAge++;
        lashed = false;
        double settleTarget = phase == Phase.HOME ? 1.0 : 0.0;
        double agility = formation != null ? formation.agility : 1.0;
        double rate = DustTuning.formationRate * agility;
        settled += Math.max(-rate, Math.min(rate, settleTarget - settled));
        if (phase == Phase.HOME) {
            return;
        }

        slugPrevX = slugX;
        slugPrevY = slugY;
        slugPrevZ = slugZ;
        double tx;
        double ty;
        double tz;
        double topSpeed;
        double steer;
        if (phase == Phase.SURGE) {
            tx = homeX + surgeDirX * surgeReach;
            ty = homeY + surgeDirY * surgeReach;
            tz = homeZ + surgeDirZ * surgeReach;
            topSpeed = surgeSpeed;
            steer = DustTuning.surgeSteer;
        } else {
            tx = homeX;
            ty = homeY;
            tz = homeZ;
            topSpeed = DustTuning.returnSpeed * agility;
            steer = Math.min(DustTuning.returnSteer * agility, 0.5);
        }
        double dx = tx - slugX;
        double dy = ty - slugY;
        double dz = tz - slugZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double desiredVx = 0.0;
        double desiredVy = 0.0;
        double desiredVz = 0.0;
        if (distance > 1.0e-4) {
            double speed = Math.min(topSpeed, distance * 0.35);
            desiredVx = dx / distance * speed;
            desiredVy = dy / distance * speed;
            desiredVz = dz / distance * speed;
        }
        if (phase == Phase.RETURN) {
            desiredVx += homeVx;
            desiredVy += homeVy;
            desiredVz += homeVz;
        }
        slugVx += (desiredVx - slugVx) * steer;
        slugVy += (desiredVy - slugVy) * steer;
        slugVz += (desiredVz - slugVz) * steer;
        slugX += slugVx;
        slugY += slugVy;
        slugZ += slugVz;

        if (phase == Phase.SURGE && phaseAge >= surgeDuration) {
            phase = Phase.RETURN;
            phaseAge = 0;
            lashed = true;
        } else if (phase == Phase.RETURN && (distance < 1.5 || phaseAge >= DustTuning.returnMaxTicks)) {
            phase = Phase.HOME;
            phaseAge = 0;
        }
    }

    private boolean withinLeash() {
        if (formation == null) {
            return true;
        }
        double leash = DustTuning.leashDistance;
        return formation.bounds.move(offsetX, offsetY, offsetZ).inflate(leash)
            .contains(caster.getX(), caster.getY(), caster.getZ());
    }

    private void capture() {
        if (formation == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        AABB area = formation.bounds.move(offsetX, offsetY, offsetZ);
        for (Entity entity : level.getEntities((Entity) null, area,
            entity -> DustPhysics.capturable(this, entity))) {
            if (captured.size() >= DustTuning.maxCaptured) {
                break;
            }
            AABB box = entity.getBoundingBox();
            if (!captured.contains(entity) && formationDistance((box.minX + box.maxX) * 0.5,
                (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5) <= 0.0) {
                captured.add(entity);
            }
        }
    }


    private double formationDistance(double x, double y, double z) {
        DustFormation current = formation;
        return current == null ? Double.MAX_VALUE : current.distance(x - offsetX, y - offsetY, z - offsetZ);
    }

    private double formationMaterialDistance(double x, double y, double z) {
        double d = formationDistance(x, y, z);
        return d == Double.MAX_VALUE ? d : DustFormation.surfaceDistance(d, constrained);
    }

    private double orbitDistance(double x, double y, double z) {
        double dx = x - anchorX;
        double dy = y - anchorY;
        double dz = z - anchorZ;
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(r - DustTuning.orbitOuterRadius, DustTuning.orbitInnerRadius - r);
    }

    private double slugDistance(double x, double y, double z) {
        double ax = slugX - slugPrevX;
        double ay = slugY - slugPrevY;
        double az = slugZ - slugPrevZ;
        double px = x - slugPrevX;
        double py = y - slugPrevY;
        double pz = z - slugPrevZ;
        double lengthSq = ax * ax + ay * ay + az * az;
        double t = lengthSq < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, (px * ax + py * ay + pz * az) / lengthSq));
        double qx = px - ax * t;
        double qy = py - ay * t;
        double qz = pz - az * t;
        return Math.sqrt(qx * qx + qy * qy + qz * qz) - DustTuning.surgeRadius;
    }


    private void replenish() {
        if (++replenishTimer < DustTuning.replenishInterval) {
            return;
        }
        replenishTimer = 0;
        double deficit = targetMass - mass;
        if (deficit <= 1.0e-3 || !(caster instanceof ServerPlayer player)) {
            return;
        }
        double want = Math.min(deficit, DustTuning.replenishPerTick * DustTuning.replenishInterval);
        if (player.isCreative()) {
            mass += want;
            return;
        }
        long perMass = Math.max(DustTuning.mediaPerMass, 1L);
        long cost = (long) Math.ceil(want * perMass);
        long remaining = cost;
        for (ADMediaHolder source : MediaHelper.scanPlayerForMediaStuff(player)) {
            remaining -= MediaHelper.extractMedia(source, remaining, false, false);
            if (remaining <= 0) {
                break;
            }
        }
        mass = Math.min(targetMass, mass + (cost - Math.max(remaining, 0L)) / (double) perMass);
    }

    private void syncMass() {
        if (massSyncCooldown > 0) {
            massSyncCooldown--;
            return;
        }
        float now = (float) mass;
        float threshold = (float) Math.max(0.1, targetMass * 0.02);
        boolean reachedTarget = now >= targetMass - 1.0e-3 && lastSentMass < now;
        if (Math.abs(now - lastSentMass) >= threshold || reachedTarget) {
            lastSentMass = now;
            massSyncCooldown = 3;
            HexwrightNetworking.broadcastDust(caster, DustPacket.mass(caster.getId(), now, (float) targetMass));
        }
    }

    void syncState() {
        lastSentMass = (float) mass;
        HexwrightNetworking.broadcastDust(caster, statePacket());
    }

    DustPacket statePacket() {
        return DustPacket.state(caster.getId(), (float) mass, (float) targetMass,
            formation == null ? null : formation.region.save(), formationSerial, constrained,
            new Vec3(offsetX, offsetY, offsetZ), new Vec3(driftVx, driftVy, driftVz), driftTarget, destination);
    }
}

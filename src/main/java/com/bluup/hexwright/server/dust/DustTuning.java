package com.bluup.hexwright.server.dust;

import at.petrak.hexcasting.api.misc.MediaConstants;
import net.minecraft.world.phys.Vec3;

public final class DustTuning {

    public static final double MAX_TARGET_MASS = 500.0;
    public static long mediaPerMass = MediaConstants.DUST_UNIT;
    public static float replenishPerTick = 0.3f;
    public static int replenishInterval = 10;

    public static float referenceDensity = 0.42f;
    public static float minThickness = 0.5f;
    public static float crustThickness = 1.0f;
    public static float minVolume = 1.0f;
    public static float shellThickness = 0.45f;
    public static float surfaceProud = 0.14f;

    public static float dragRate = 1.2f;
    public static float damageSpeed = 0.35f;
    public static float damageFullSpeed = 1.2f;
    public static float damagePeak = 12.0f;
    public static float ramMass = 50.0f;
    public static float massPerImpulse = 0.6f;
    public static float massPerDamage = 0.3f;
    public static float freeImpulse = 0.015f;
    public static float projectileCatchStrength = 0.45f;
    public static float projectileInertia = 0.35f;
    public static int maxContacts = 48;

    public static float orbitDensityScale = 0.15f;
    public static int orbitContact = 0;
    public static int formationSparesCaster = 0;
    public static int constraintSparesCaster = 1;
    public static float orbitInnerRadius = 1.1f;
    public static float orbitOuterRadius = 2.6f;
    public static float anchorHeight = 1.0f;

    public static final int SURGE_DURATION_TICKS = 30;
    public static float surgeRadius = 1.2f;
    public static float surgeMinReach = 3.0f;
    public static float surgeMaxReach = 24.0f;
    public static float surgeBaseSpeed = 0.55f;
    public static float surgeSpeedPerBlock = 0.035f;
    public static float surgeMaxSpeed = 1.5f;
    public static float surgeSteer = 0.25f;
    public static float returnSpeed = 0.6f;
    public static float returnSteer = 0.12f;
    public static int returnMaxTicks = 80;
    public static float formationRate = 0.12f;

    public static float maxDriftSpeed = 1.0f;
    public static float driftAcceleration = 0.2f;
    public static float moveSpeed = 0.8f;
    public static float agileVolume = 4.0f;
    public static float heavyVolume = 64.0f;
    public static float agileBoost = 3.0f;
    public static float leashDistance = 48.0f;

    public static float constrainSoftZone = 0.35f;
    public static float constrainStiffness = 0.35f;
    public static float constrainMaxPush = 0.6f;
    public static float constrainCarry = 0.2f;
    public static float constrainEscape = 2.5f;
    public static int maxCaptured = 32;

    public static int damsFluid = 1;

    public static float supportLow = 0.3f;
    public static float supportFirm = 0.55f;
    public static float supportMaxPush = 0.5f;
    public static float supportNormalY = 0.4f;
    public static float supportCarry = 1.0f;
    public static float massPerBearing = 0.015f;
    public static int maxBorne = 16;

    private DustTuning() {
    }

    public static double agility(double fillVolume) {
        double low = Math.log(Math.max(agileVolume, 1.0e-3));
        double high = Math.max(Math.log(Math.max(heavyVolume, 1.0e-3)), low + 1.0e-3);
        double t = (Math.log(Math.max(fillVolume, 1.0e-3)) - low) / (high - low);
        t = Math.max(0.0, Math.min(1.0, t));
        double heavy = t * t * (3.0 - 2.0 * t);
        return agileBoost + (1.0 - agileBoost) * heavy;
    }

    public static Vec3 accelerate(double vx, double vy, double vz, Vec3 target, double agility) {
        double dx = target.x - vx;
        double dy = target.y - vy;
        double dz = target.z - vz;
        double change = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double step = driftAcceleration * agility;
        if (change <= step) {
            return target;
        }
        double scale = step / change;
        return new Vec3(vx + dx * scale, vy + dy * scale, vz + dz * scale);
    }

    public static Vec3 seek(double ox, double oy, double oz, Vec3 destination, double agility) {
        double dx = destination.x - ox;
        double dy = destination.y - oy;
        double dz = destination.z - oz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0e-4) {
            return Vec3.ZERO;
        }
        double step = driftAcceleration * agility;
        double braking = step * (Math.sqrt(0.25 + 2.0 * distance / step) - 0.5);
        double speed = Math.min(Math.min(moveSpeed, maxDriftSpeed), Math.min(braking, distance));
        return new Vec3(dx / distance * speed, dy / distance * speed, dz / distance * speed);
    }

    public static double impact(double relativeSpeed) {
        double t = (relativeSpeed - damageSpeed) / Math.max(damageFullSpeed - damageSpeed, 1.0e-3);
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double heft(double mass) {
        return mass <= 0.0 ? 0.0 : 1.0 - Math.exp(-mass / ramMass);
    }

    public static double strength(double density) {
        return density <= 0.0 ? 0.0 : 1.0 - Math.exp(-density / referenceDensity);
    }
}

package com.bluup.hexwright.server.dust;

import at.petrak.hexcasting.api.misc.MediaConstants;

public final class DustTuning {

    public static final double MAX_TARGET_MASS = 5000.0;
    public static long mediaPerMass = MediaConstants.DUST_UNIT / 500;
    public static float replenishPerTick = 3.0f;
    public static int replenishInterval = 10;

    public static float referenceDensity = 4.2f;
    public static float minThickness = 0.5f;
    public static float crustThickness = 1.0f;
    public static float minVolume = 1.0f;
    public static float shellThickness = 0.45f;
    public static float surfaceProud = 0.14f;

    public static float dragRate = 1.2f;
    public static float damageSpeed = 0.35f;
    public static float damagePerSpeed = 7.0f;
    public static float massPerImpulse = 6.0f;
    public static float massPerDamage = 3.0f;
    public static float freeImpulse = 0.015f;
    public static float projectileInertia = 0.35f;
    public static int maxContacts = 48;

    public static float orbitDensityScale = 0.15f;
    public static int orbitContact = 0;
    public static int formationSparesCaster = 0;
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

    public static float migrationSpeed = 1.5f;
    public static float leashDistance = 48.0f;

    public static float constrainSoftZone = 0.35f;
    public static float constrainStiffness = 0.35f;
    public static float constrainMaxPush = 0.6f;
    public static float constrainCarry = 0.2f;
    public static float constrainEscape = 2.5f;
    public static int maxCaptured = 32;

    public static float supportLow = 0.3f;
    public static float supportFirm = 0.55f;
    public static float supportMaxPush = 0.5f;
    public static float supportNormalY = 0.4f;
    public static float supportCarry = 1.0f;
    public static float massPerBearing = 0.15f;
    public static int maxBorne = 16;

    private DustTuning() {
    }

    public static double strength(double density) {
        return density <= 0.0 ? 0.0 : 1.0 - Math.exp(-density / referenceDensity);
    }
}

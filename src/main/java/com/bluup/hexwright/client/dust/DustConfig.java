package com.bluup.hexwright.client.dust;

public final class DustConfig {

    public enum Quality {
        LOW(300), MEDIUM(600), HIGH(900), ULTRA(1200);

        public final int population;

        Quality(int population) {
            this.population = population;
        }
    }

    public static Quality quality = Quality.HIGH;

    public static int hardCap = 12000;

    public static float lodNearDistance = 24.0f;
    public static float lodMidDistance = 48.0f;
    public static float lodFarDistance = 80.0f;
    public static float lodMidFactor = 0.6f;
    public static float lodFarFactor = 0.25f;
    public static float cullDistance = 112.0f;

    public static float formFillTicks = 70.0f;
    public static float replenishFactor = 1.2f;
    public static float fallbackLifetime = 150.0f;
    public static float anchorHeight = 1.0f;

    public static float steerForming = 0.09f;
    public static float steerOrbit = 0.2f;
    public static float steerAttack = 0.2f;
    public static float steerReturn = 0.07f;
    public static float steerDisperse = 0.35f;
    public static float maxSpeed = 2.2f;

    public static float orbitRadius = 1.8f;
    public static float orbitHeightSpread = 0.7f;
    public static float orbitRadiusWeave = 0.2f;
    public static float orbitSpeed = 0.28f;
    public static int orbitStreams = 1;
    public static float orbitStreamLength = 0.45f;
    public static float orbitTubeRadius = 0.28f;
    public static float orbitPull = 0.3f;
    public static float orbitTurbulence = 0.02f;
    public static float orbitFollow = 0.8f;
    public static float compressedRadiusScale = 0.35f;
    public static float compressedSpeedScale = 1.8f;
    public static float formingRadiusScale = 0.45f;

    public static int attackWindupTicks = 5;
    public static float attackMinReach = 3.0f;
    public static float attackMaxReach = 24.0f;
    public static float attackBaseSpeed = 0.55f;
    public static float attackSpeedPerBlock = 0.035f;
    public static float attackMaxAttackSpeed = 1.5f;
    public static float streamRadius = 0.22f;
    public static float streamFlare = 0.035f;
    public static float streamCompression = 0.3f;
    public static float streamSwirl = 0.08f;
    public static float streamTurbulence = 0.05f;
    public static float streamImpactBloom = 1.6f;
    public static float attackFollow = 0.5f;

    public static float returnSpeed = 0.55f;
    public static float returnCurl = 0.26f;
    public static float returnBlendFar = 4.5f;
    public static float returnBlendNear = 2.2f;
    public static float reformedFraction = 0.85f;
    public static int returnMaxTicks = 80;

    public static float massReference = 1000.0f;
    public static int minGrains = 24;

    public static float grainScale = 1.0f;
    public static float formationCoverage = 0.0f;
    public static float grainMinSize = 0.02f;
    public static float grainMaxSize = 0.35f;
    public static int depletionFadeTicks = 12;

    public static float formationPopulationScale = 1.0f;
    public static float surfacePull = 0.45f;
    public static float regionMaxSpeed = 2.0f;
    public static float shellDepth = 0.18f;
    public static float fillSurfaceBias = 2.5f;
    public static float swirlSpeed = 0.24f;
    public static float swirlMaxAngular = 0.1f;
    public static float swirlCentripetal = 0.7f;
    public static float swirlWeave = 0.55f;
    public static int swirlStreams = 3;
    public static float heightPull = 0.2f;
    public static float regionTurbulence = 0.015f;
    public static float regionFollow = 1.0f;
    public static float steerRegion = 0.22f;

    public static float formedSwirlScale = 0.06f;
    public static float surfaceDrift = 0.025f;
    public static float accentLayer = 0.2f;
    public static float breakAwayShare = 0.1f;
    public static float breakAwayHeight = 0.8f;
    public static float breakAwayRate = 0.008f;

    public static int constructEnabled = 1;
    public static float constructQuality = 1.0f;
    public static float constructBuildTicks = 40.0f;
    public static float constructDissolveTicks = 34.0f;
    public static float constructStrengthLow = 0.06f;
    public static float constructStrengthHigh = 0.5f;
    public static float constructAccentShare = 0.22f;
    public static float looseGrainScale = 2.0f;
    public static float constructFillDepth = 0.4f;
    public static float constructThinnest = 0.6f;
    public static float constructRoughness = 0.05f;
    public static float constructGrainSize = 0.045f;
    public static float constructStretch = 0.5f;
    public static float constructGlow = 0.08f;
    public static float constructSparkle = 1.0f;
    public static float constructLife = 1.0f;

    public static float impactMinSpeed = 0.12f;
    public static int impactCooldown = 6;
    public static int impactBurstGrains = 18;

    public static float disperseDrag = 0.9f;
    public static float disperseOutward = 0.004f;
    public static float disperseSink = 0.003f;
    public static float disperseTurbulence = 0.006f;

    private DustConfig() {
    }
}

package com.bluup.hexwright.client.dust;

public final class DustFrame {
    public float time;

    public float casterVx;
    public float casterVy;
    public float casterVz;

    public float orbitRadiusScale = 1.0f;
    public float orbitSpeedScale = 1.0f;
    public float orbitHeadAngle;

    public float attackDirX;
    public float attackDirY;
    public float attackDirZ = 1.0f;
    public float attackReach;
    public float attackSpeed;
    public float attackOriginX;
    public float attackOriginY;
    public float attackOriginZ;
    public float attackFollow;

    public float regionOffX;
    public float regionOffY;
    public float regionOffZ;
    public float regionVx;
    public float regionVy;
    public float regionVz;

    public float formCalm;
    public float constructSolidity;
    public float constructOuter;
    public int impactCount;
    public final float[] impactX = new float[DustConstruct.MAX_IMPACTS];
    public final float[] impactY = new float[DustConstruct.MAX_IMPACTS];
    public final float[] impactZ = new float[DustConstruct.MAX_IMPACTS];
    public final float[] impactRadius = new float[DustConstruct.MAX_IMPACTS];
    public final float[] impactIntensity = new float[DustConstruct.MAX_IMPACTS];
}

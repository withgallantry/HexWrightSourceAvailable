package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.misc.MediaConstants;

public final class VehicleConfig {

    private VehicleConfig() {
    }


    public static final double BROOM_MAX_HORIZONTAL_SPEED = 0.95;
    public static final double BROOM_MAX_VERTICAL_SPEED = 0.42;
    public static final double BROOM_MAX_ACCELERATION = 0.10;
    public static final double BROOM_HORIZONTAL_SPEED_GRADE_BONUS = 0.65;
    public static final double BROOM_VERTICAL_SPEED_GRADE_BONUS = 0.38;
    public static final double BROOM_ACCELERATION_GRADE_BONUS = 0.50;
    public static final int BROOM_PASSENGER_CAPACITY = 1;
    public static final double BROOM_MEDIA_MULTIPLIER = 1.0;
    public static final long BROOM_MEDIA_CAPACITY = 24L * MediaConstants.DUST_UNIT;
    public static final long BROOM_MEDIA_CAPACITY_GRADE_BONUS = 24L * MediaConstants.DUST_UNIT;

    public static final double BROOM_ARTIFACT_HORIZONTAL_SPEED_BONUS = 0.13;
    public static final double BROOM_ARTIFACT_VERTICAL_SPEED_BONUS = 0.08;
    public static final double BROOM_ARTIFACT_ACCELERATION_BONUS = 0.10;
    public static final long BROOM_ARTIFACT_MEDIA_CAPACITY_BONUS = 16L * MediaConstants.DUST_UNIT;
    public static final double BROOM_RIDER_HEIGHT_OFFSET = 0.075;
    public static final double BROOM_RIDER_FORWARD_OFFSET = 0.15;
    public static final double BROOM_VISUAL_TILT_DEGREES = 15.0;

    public static final double BROOM_LOAD_ONE_PASSENGER = 1.20;
    public static final double BROOM_LOAD_TWO_PASSENGERS = 1.42;

    public static final double BROOM_TRAIL_OFFSET_SIDEWAYS = 0.0;
    public static final double BROOM_TRAIL_OFFSET_UP = 0.0;
    public static final double BROOM_TRAIL_OFFSET_BACK = 0.5;
    public static final double BROOM_TRAIL_MIN_SPEED = 0.03;
    public static final double BROOM_TRAIL_MIN_SPEED_SQ = BROOM_TRAIL_MIN_SPEED * BROOM_TRAIL_MIN_SPEED;


    public static final double CARPET_MAX_HORIZONTAL_SPEED = 0.95;
    public static final double CARPET_MAX_VERTICAL_SPEED = 0.42;
    public static final double CARPET_MAX_ACCELERATION = 0.10;
    public static final double CARPET_HORIZONTAL_SPEED_GRADE_BONUS = 0.65;
    public static final double CARPET_VERTICAL_SPEED_GRADE_BONUS = 0.38;
    public static final double CARPET_ACCELERATION_GRADE_BONUS = 0.50;
    public static final int CARPET_PASSENGER_CAPACITY = 2;
    public static final double CARPET_MEDIA_MULTIPLIER = 1.0;
    public static final long CARPET_MEDIA_CAPACITY = 40L * MediaConstants.DUST_UNIT;
    public static final int CARPET_CHEST_SIZE = 27;
    public static final double CARPET_RIDER_HEIGHT_OFFSET = 0.0;

    public static final double CARPET_PILOT_FORWARD_OFFSET = -0.25;
    public static final double CARPET_PILOT_FORWARD_OFFSET_WITH_CHEST = 0.1;
    public static final double CARPET_PASSENGER_FORWARD_OFFSET = -0.7;

    public static final double CARPET_RENDER_SCALE = 2.0;

    public static final double CARPET_CULLING_INFLATION = 1.8;

    public static final float CARPET_CHEST_SCALE = 0.28f;
    public static final float CARPET_CHEST_OFFSET_Y = -0.469f + CARPET_CHEST_SCALE / 2.0f;
    public static final float CARPET_CHEST_OFFSET_Z = 0.26f;

    public static final double CARPET_LOAD_SINGLE_PASSENGER = 1.0;
    public static final double CARPET_LOAD_TWO_PASSENGERS = 1.25;
    public static final double CARPET_LOAD_EMPTY_CHEST = 1.15;
    public static final double CARPET_LOAD_FULL_CHEST = 1.35;


    public static final double VEHICLE_MODEL_HEIGHT_OFFSET = 0.625;

    public static final double VEHICLE_MODEL_CENTRE_OFFSET = -0.457;



    public static final double CARPET_RIPPLE_IDLE_PERIOD_TICKS = 40.0;
    public static final double CARPET_RIPPLE_FLIGHT_PERIOD_TICKS = 10.0;
    public static final float CARPET_RIPPLE_FLOW_SMOOTHING = 0.15f;


    public static final float BANK_DEGREES_PER_YAW_DEGREE = 2.5f;
    public static final float BANK_MAX_DEGREES = 30.0f;
    public static final float BANK_SMOOTHING = 0.2f;


    public static final int HEX_EXECUTION_INTERVAL_TICKS = 3;

    public static final int MAX_FLIGHT_OP_COUNT = 1_000;

    public static final double FLIGHT_DRAG = 0.99;


    public static final double DEPLOY_HOVER_HEIGHT = 0.35;
    public static final double DEPLOY_WALL_CLEARANCE = 0.1;


    public static final double PARK_MIN_HOVER_HEIGHT = 0.25;
    public static final double PARK_GROUND_SCAN_DISTANCE = 32.0;
    public static final double PARK_BOB_AMPLITUDE = 0.045;
    public static final double PARK_BOB_PERIOD_TICKS = 70.0;


    public static final double DISMOUNT_GROUND_SNAP_DISTANCE = 4.0;


    public static final int MEMORY_MAX_IOTAS = 64;
    public static final int MEMORY_MAX_DEPTH = 8;



    public static final long HOVER_COST = MediaConstants.DUST_UNIT / 50;
    public static final long CRUISE_COST_COEFFICIENT = MediaConstants.DUST_UNIT / 12;
    public static final long CLIMB_COST_COEFFICIENT = MediaConstants.DUST_UNIT / 8;
    public static final long MANOEUVRE_COST_COEFFICIENT = MediaConstants.DUST_UNIT / 6;


    public static final int OVERSPEED_GRACE_RUNS = 3;
    public static final double OVERSPEED_MEDIA_MULTIPLIER = 30.0;



    public static final int REMOTE_LERP_STEPS = 10;

    public static final double RECONCILE_DEADZONE = 0.03;
    public static final double RECONCILE_GENTLE_DISTANCE = 0.75;
    public static final double RECONCILE_GENTLE_FACTOR = 0.10;
    public static final double RECONCILE_SNAP_DISTANCE = 3.0;
    public static final double RECONCILE_STRONG_FACTOR = 0.35;

    public static final double RECONCILE_DEADZONE_SQ = RECONCILE_DEADZONE * RECONCILE_DEADZONE;
    public static final double RECONCILE_GENTLE_DISTANCE_SQ = RECONCILE_GENTLE_DISTANCE * RECONCILE_GENTLE_DISTANCE;
    public static final double RECONCILE_SNAP_DISTANCE_SQ = RECONCILE_SNAP_DISTANCE * RECONCILE_SNAP_DISTANCE;
}

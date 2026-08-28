package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.hexpatterns.HexwrightConstMediaAction;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class FlightActions {

    private FlightActions() {
    }

    public static final HexPattern DEBUG_FLIGHT_HEX_PATTERN = HexPattern.fromAngles("qqqaeqeeedq", HexDir.NORTH_WEST);

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("conveyances_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqaewde", HexDir.SOUTH_WEST), CONVEYANCES_REFLECTION));
        Registry.register(registry, Hexwright.id("conveyance_entitys_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqaewdeqaa", HexDir.SOUTH_WEST), CONVEYANCE_ENTITYS_REFLECTION));
        Registry.register(registry, Hexwright.id("debug_flight_reflection"),
            new ActionRegistryEntry(DEBUG_FLIGHT_HEX_PATTERN, DEBUG_FLIGHT_REFLECTION));
    }

    private static FlightCastingEnvironment requireFlightEnv(CastingEnvironment env) {
        if (env instanceof FlightCastingEnvironment flightEnv) {
            return flightEnv;
        }
        throw new MishapBadCaster();
    }

    private static final ConstMediaAction CONVEYANCES_REFLECTION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public long getMediaCost() {
            return MediaConstants.DUST_UNIT / 10;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            FlightCastingEnvironment flightEnv = requireFlightEnv(env);
            return List.of(flightEnv.getContext().toContextList());
        }
    };

    private static final ConstMediaAction CONVEYANCE_ENTITYS_REFLECTION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public long getMediaCost() {
            return MediaConstants.DUST_UNIT / 10;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            FlightCastingEnvironment flightEnv = requireFlightEnv(env);
            return List.of(new EntityIota(flightEnv.getVehicle()));
        }
    };

    private static final ConstMediaAction DEBUG_FLIGHT_REFLECTION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public long getMediaCost() {
            return 0L;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            FlightCastingEnvironment flightEnv = requireFlightEnv(env);
            FlightExecutionContext context = flightEnv.getContext();
            Vec3 requestedThrottle = VehicleMovementMath.solveThrottle(
                context.getVehicleVelocity(),
                context.getRiderInput(),
                context.getRiderForward(),
                context.getRiderRight(),
                context.getMaxHorizontalSpeed(),
                context.getMaxVerticalSpeed(),
                context.getMaxAcceleration()
            );

            Vec3Iota command = new Vec3Iota(requestedThrottle);
            ListIota memory = new ListIota(List.of());
            return List.of(new ListIota(List.of(command, memory)));
        }
    };
}

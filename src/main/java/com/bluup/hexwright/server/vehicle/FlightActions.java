package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.hexpatterns.HexwrightConstMediaAction;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;

import java.util.List;

public final class FlightActions {

    private FlightActions() {
    }

    private static final long DISMISSAL_COST = MediaConstants.DUST_UNIT;

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("conveyances_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqaeede", HexDir.SOUTH_WEST), CONVEYANCES_REFLECTION));
        Registry.register(registry, Hexwright.id("conveyance_entitys_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqaewdeqaa", HexDir.SOUTH_WEST), CONVEYANCE_ENTITYS_REFLECTION));
        Registry.register(registry, Hexwright.id("speedys_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqadd", HexDir.SOUTH_WEST), SPEEDYS_REFLECTION));
        Registry.register(registry, Hexwright.id("locomotions_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqawa", HexDir.SOUTH_WEST), LOCOMOTIONS_REFLECTION));
        Registry.register(registry, Hexwright.id("vessels_dismissal"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqawqded", HexDir.SOUTH_WEST), VESSELS_DISMISSAL));
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
            return 0L;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            FlightCastingEnvironment flightEnv = requireFlightEnv(env);
            return List.of(flightEnv.getContext().toContextList());
        }
    };

    private static final ConstMediaAction SPEEDYS_REFLECTION = new HexwrightConstMediaAction() {
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
            return List.of(flightEnv.getContext().toLimitsList());
        }
    };

    private static final ConstMediaAction LOCOMOTIONS_REFLECTION = new HexwrightConstMediaAction() {
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
            FlightExecutionContext context = requireFlightEnv(env).getContext();
            return List.of(
                new DoubleIota(context.getClimbSpeed()),
                new DoubleIota(context.getGroundSpeed())
            );
        }
    };

    private static final ConstMediaAction VESSELS_DISMISSAL = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0L;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            FlightCastingEnvironment flightEnv = requireFlightEnv(env);
            Iota raw = args.get(0);
            if (!(raw instanceof BooleanIota keepDeployed)) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.vessels_dismissal");
            }

            if (env.extractMedia(DISMISSAL_COST, true) > 0) {
                throw new MishapNotEnoughMedia(DISMISSAL_COST);
            }
            env.extractMedia(DISMISSAL_COST, false);

            flightEnv.getVehicle().dismissRiders(keepDeployed.getBool());
            return List.of();
        }
    };

    private static final ConstMediaAction CONVEYANCE_ENTITYS_REFLECTION = new HexwrightConstMediaAction() {
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
            return List.of(new EntityIota(flightEnv.getVehicle()));
        }
    };
}

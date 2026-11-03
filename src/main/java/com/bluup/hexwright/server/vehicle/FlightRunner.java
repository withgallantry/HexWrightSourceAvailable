package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FlightRunner {

    private static final double EPSILON = 1.0e-9;

    private FlightRunner() {
    }

    public sealed interface FlightRunResult {
        record Success(Vec3 acceleration, ListIota memory, boolean overspeed) implements FlightRunResult {
        }

        record Failed(Component riderMessage, boolean punishing) implements FlightRunResult {
        }
    }

    public static FlightRunResult run(@Nullable Iota storedHex, FlightCastingEnvironment env, ServerLevel world) {
        List<Iota> program = StoredHex.decode(storedHex);
        if (program == null || program.isEmpty()) {
            return new FlightRunResult.Failed(
                Component.translatable("message.hexwright.vehicle.mishap.no_hex"), false);
        }

        CastingVM vm;
        try {
            vm = CastingVM.empty(env);
            vm.queueExecuteAndWrapIotas(program, world);
        } catch (RuntimeException e) {
            return failed("evaluator_error");
        }

        if (env.hasMishap()) {
            return failed("mishap");
        }

        List<Iota> stack = vm.getImage().getStack();
        if (stack.isEmpty()) {
            return failed("empty_stack");
        }

        Iota top = stack.get(stack.size() - 1);
        Vec3Iota throttleIota;
        ListIota memoryIota;
        if (top instanceof Vec3Iota bareThrottle) {
            throttleIota = bareThrottle;
            memoryIota = new ListIota(List.of());
        } else if (top instanceof ListIota outputList) {
            List<Iota> outputElems = new ArrayList<>();
            for (Iota entry : outputList.getList()) {
                outputElems.add(entry);
            }
            if (outputElems.size() != 2) {
                return failed("wrong_shape");
            }
            if (!(outputElems.get(0) instanceof Vec3Iota listedThrottle)) {
                return failed("command_not_vector");
            }
            if (!(outputElems.get(1) instanceof ListIota listedMemory)) {
                return failed("memory_not_list");
            }
            throttleIota = listedThrottle;
            memoryIota = listedMemory;
        } else {
            return failed("bad_output");
        }

        if (memoryIota.size() > VehicleConfig.MEMORY_MAX_IOTAS
            || memoryIota.depth() > VehicleConfig.MEMORY_MAX_DEPTH
            || IotaType.isTooLargeToSerialize(List.of((Iota) memoryIota))) {
            return failed("memory_too_large");
        }

        FlightExecutionContext context = env.getContext();

        Vec3 requestedAcceleration = clampToUnitLength(throttleIota.getVec3())
            .scale(context.getMaxAcceleration());

        Vec3 currentVelocity = context.getVehicleVelocity();
        Vec3 resultingVelocity = currentVelocity.add(requestedAcceleration);
        boolean overspeed = !isVelocityAcceptable(currentVelocity, resultingVelocity,
            context.getMaxHorizontalSpeed(), context.getMaxVerticalSpeed());

        return new FlightRunResult.Success(requestedAcceleration, memoryIota, overspeed);
    }

    private static boolean isVelocityAcceptable(Vec3 current, Vec3 result, double maxHorizontalSpeed, double maxVerticalSpeed) {
        double resultHorizontalSq = result.x * result.x + result.z * result.z;
        double currentHorizontalSq = current.x * current.x + current.z * current.z;
        boolean horizontalOk = resultHorizontalSq <= sq(maxHorizontalSpeed) + EPSILON
            || resultHorizontalSq <= currentHorizontalSq + EPSILON;

        boolean verticalOk = Math.abs(result.y) <= maxVerticalSpeed + EPSILON
            || Math.abs(result.y) <= Math.abs(current.y) + EPSILON;

        return horizontalOk && verticalOk;
    }

    private static Vec3 clampToUnitLength(Vec3 v) {
        double lenSq = v.lengthSqr();
        if (lenSq <= 1.0 || lenSq == 0.0) {
            return v;
        }
        return v.scale(1.0 / Math.sqrt(lenSq));
    }

    private static double sq(double value) {
        return value * value;
    }


    private static FlightRunResult.Failed failed(String reasonKey) {
        return new FlightRunResult.Failed(Component.translatable("message.hexwright.vehicle.mishap." + reasonKey), true);
    }
}

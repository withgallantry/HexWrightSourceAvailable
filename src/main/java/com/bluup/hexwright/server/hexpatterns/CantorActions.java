package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.armour.ArmourPowerToggle;
import com.bluup.hexwright.server.armour.ArmourSet;
import com.bluup.hexwright.server.armour.ArmourTier;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public final class CantorActions {

    private static final String CANTOR_USERDATA = Hexwright.id("cantor_minds").toString();

    private CantorActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("cantors_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("qeewdweddwdw", HexDir.NORTH_EAST), CANTORS_REFLECTION));
        Registry.register(registry, Hexwright.id("cantors_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("eqqwawqaawaw", HexDir.NORTH_WEST), CANTORS_GAMBIT));
    }

    public static int mindCount(ArmourTier tier) {
        return switch (tier) {
            case IRON -> 1;
            case GOLDEN -> 2;
            case DIAMOND -> 3;
            case NETHERITE -> 4;
        };
    }

    private static final Action CANTORS_REFLECTION = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        List<Iota> args = takeArgs(stack, 1);
        int mind = requireMind(env, args, 0, 1);

        CompoundTag minds = image.getUserData().getCompound(CANTOR_USERDATA);
        String key = Integer.toString(mind);
        stack.add(minds.contains(key)
            ? IotaType.deserialize(minds.getCompound(key), env.getWorld())
            : new NullIota());

        return finish(image, stack, continuation);
    };

    private static final Action CANTORS_GAMBIT = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        List<Iota> args = takeArgs(stack, 2);
        int mind = requireMind(env, args, 0, 2);
        Iota value = args.get(1);

        CompoundTag userData = image.getUserData();
        CompoundTag minds = userData.getCompound(CANTOR_USERDATA);
        String key = Integer.toString(mind);
        if (value instanceof NullIota) {
            minds.remove(key);
        } else {
            minds.put(key, IotaType.serialize(value));
        }
        if (minds.isEmpty()) {
            userData.remove(CANTOR_USERDATA);
        } else {
            userData.put(CANTOR_USERDATA, minds);
        }

        return finish(image, stack, continuation);
    };


    private static List<Iota> takeArgs(List<Iota> stack, int argc) {
        if (stack.size() < argc) {
            throw new MishapNotEnoughArgs(argc, stack.size());
        }
        List<Iota> window = stack.subList(stack.size() - argc, stack.size());
        List<Iota> args = new ArrayList<>(window);
        window.clear();
        return args;
    }

    private static int requireMind(CastingEnvironment env, List<Iota> args, int idx, int argc) {
        int capacity = capacity(env);
        int mind = OperatorUtils.getInt(args, idx, argc);
        if (mind < 0 || mind >= capacity) {
            throw MishapInvalidIota.of(args.get(idx), argc - idx - 1, "hexwright.cantor_mind", capacity - 1);
        }
        return mind;
    }

    private static int capacity(CastingEnvironment env) {
        LivingEntity wearer = env.getCastingEntity();
        if (wearer == null) {
            throw new MishapBadCaster();
        }
        ArmourTier tier = ArmourPowerToggle.activeTier(wearer, ArmourSet.CANTOR);
        if (tier == null) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_cantor_chest");
        }
        return mindCount(tier);
    }

    private static OperationResult finish(CastingImage image, List<Iota> stack, SpellContinuation continuation) {
        CastingImage used = image.withUsedOp();
        CastingImage next = used.copy(stack, used.getParenCount(), used.getParenthesized(),
            used.getEscapeNext(), used.getOpsConsumed(), used.getUserData());
        return new OperationResult(next, List.of(), continuation, HexEvalSounds.NORMAL_EXECUTE);
    }
}

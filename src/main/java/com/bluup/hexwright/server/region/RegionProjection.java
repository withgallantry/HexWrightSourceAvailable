package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class RegionProjection {

    private RegionProjection() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("region_projection"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqwqwqwqwqweawwqwwa", HexDir.SOUTH_WEST), PROJECTION));
    }

    private static final Action PROJECTION = (env, image, continuation) -> {
        List<Iota> stack = image.getStack();
        if (stack.isEmpty()) {
            throw new MishapNotEnoughArgs(1, 0);
        }
        Iota top = stack.get(stack.size() - 1);
        Region region = HexpanseBridge.regionOf(top);
        if (region == null) {
            throw MishapInvalidIota.ofType(top, 0, "hexwright.region");
        }
        RegionAmbit.assertScannable(region, top, 0);

        List<OperatorSideEffect> effects = region.isEmpty()
            ? List.of()
            : List.of(new OperatorSideEffect.AttemptSpell(new Projection(region), false, false));
        return new OperationResult(image.withUsedOp(), effects, continuation, HexEvalSounds.SPELL);
    };

    private record Projection(Region region) implements RenderedSpell {

        @Override
        public void cast(CastingEnvironment env) {
            if (env.getCastingEntity() instanceof ServerPlayer viewer) {
                HexwrightNetworking.sendRegionProjection(viewer, region, env.getPigment());
            }
        }

        @Override
        public CastingImage cast(CastingEnvironment env, CastingImage image) {
            cast(env);
            return image;
        }
    }
}

package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;

public final class CastSounds {

    private CastSounds() {
    }

    public static CastResult muted(CastResult result) {
        return result.copy(
            result.getCast(),
            result.getContinuation(),
            result.getNewData(),
            result.getSideEffects(),
            result.getResolutionType(),
            HexEvalSounds.MUTE
        );
    }
}

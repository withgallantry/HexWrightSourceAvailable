package com.bluup.hexwright.client.hexahedron;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class HexahedronSigils {

    private static final List<ActionRegistryEntry> FACES = List.of(
        HexActions.BLINK, HexActions.FLIGHT$RANGE, HexActions.HALT,
        HexActions.EVAL, HexActions.FOR_EACH, HexActions.SENTINEL$CREATE);

    private static final float FIT = 0.6f;
    private static final float DROP = -0.1f;

    @Nullable
    private static DynamicTexture atlas;

    private HexahedronSigils() {
    }

    static DynamicTexture atlas() {
        if (atlas == null) {
            atlas = SigilAtlas.bake(FACES.stream().map(ActionRegistryEntry::prototype).toList(), 3, 2, FIT, DROP);
        }
        return atlas;
    }
}

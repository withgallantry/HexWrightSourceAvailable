package com.bluup.hexwright.client.block;

import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class CoalescerFormVisualClient {

    private static final ResourceLocation FX_ID = new ResourceLocation("hexwright", "coalescer");

    private static final Vector3f OFFSET = new Vector3f(0.0f, 0.66f, 0.0f);

    private CoalescerFormVisualClient() {
    }

    public static void play(Level level, BlockPos pos) {
        FX fx = FXHelper.getFX(FX_ID);
        if (fx == null) {
            return;
        }
        try {
            BlockEffect effect = new BlockEffect(fx, level, pos);
            effect.setOffset(OFFSET);
            effect.setAllowMulti(true);
            effect.start();
        } catch (RuntimeException ignored) {
        }
    }
}

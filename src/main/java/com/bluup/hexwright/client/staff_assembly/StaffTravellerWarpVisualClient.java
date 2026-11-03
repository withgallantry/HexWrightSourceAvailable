package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.Hexwright;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class StaffTravellerWarpVisualClient {
    private static final ResourceLocation WARP_OUT_FX = Hexwright.id("traveller_warp_out");
    private static final ResourceLocation WARP_IN_FX = Hexwright.id("traveller_warp_in");
    private static final int POP_DURATION_TICKS = 5;

    private static final Map<Integer, PopState> ACTIVE_POPS = new HashMap<>();

    private StaffTravellerWarpVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StaffTravellerWarpVisualClient::onClientTick);
    }

    public static void handleWarp(int entityId, Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        spawnBurst(level, WARP_OUT_FX, from);
        spawnBurst(level, WARP_IN_FX, to);
        ACTIVE_POPS.put(entityId, new PopState());
    }

    public static float getArrivalPopProgress(int entityId, float partialTick) {
        PopState state = ACTIVE_POPS.get(entityId);
        if (state == null) {
            return 1.0f;
        }
        return Mth.clamp((state.age + partialTick) / POP_DURATION_TICKS, 0.0f, 1.0f);
    }

    private static void spawnBurst(Level level, ResourceLocation fxId, Vec3 point) {
        FX fx = FXHelper.getFX(fxId);
        if (fx == null) {
            return;
        }

        BlockPos anchor = new BlockPos(Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z));
        try {
            BlockEffect effect = new BlockEffect(fx, level, anchor);
            effect.setOffset(new Vector3f(
                (float) (point.x - anchor.getX()),
                (float) (point.y - anchor.getY()),
                (float) (point.z - anchor.getZ())
            ));
            effect.start();
        } catch (RuntimeException ignored) {
        }
    }

    private static void onClientTick(Minecraft mc) {
        if (ACTIVE_POPS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Integer, PopState>> it = ACTIVE_POPS.entrySet().iterator();
        while (it.hasNext()) {
            PopState state = it.next().getValue();
            state.age++;
            if (state.age > POP_DURATION_TICKS) {
                it.remove();
            }
        }
    }

    private static final class PopState {
        private int age;
    }
}

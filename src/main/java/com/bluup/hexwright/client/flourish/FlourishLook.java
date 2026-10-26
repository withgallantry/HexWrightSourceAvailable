package com.bluup.hexwright.client.flourish;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.function.IntUnaryOperator;

interface FlourishLook {

    int lifetimeTicks();

    default void begin(ClientLevel level, IntUnaryOperator colour) {
    }

    void draw(FlourishPen pen, float progress);
}

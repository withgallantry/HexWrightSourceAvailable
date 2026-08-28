package com.bluup.hexwright.client.talisman;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class TalismanClient {

    private TalismanClient() {
    }

    public static void openDrawScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TalismanDrawScreen(hand));
    }
}

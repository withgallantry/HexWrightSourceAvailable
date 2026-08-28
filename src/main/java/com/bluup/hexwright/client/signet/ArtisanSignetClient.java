package com.bluup.hexwright.client.signet;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class ArtisanSignetClient {

    private ArtisanSignetClient() {
    }

    public static void openDrawScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new SignetDrawScreen(hand));
    }
}

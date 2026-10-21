package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.server.hexweave.WeaveTrigger;
import com.bluup.hexwright.server.hexweave.WovenOrbItem;
import com.bluup.hexwright.server.hexweave.WovenOrbTriggers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class WovenOrbClient {

    private static final Set<String> DOWN = new HashSet<>();

    private WovenOrbClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WovenOrbClient::releaseUnwatched);
    }

    public static void onInput(long window, InputConstants.Key key, int action) {
        Minecraft client = Minecraft.getInstance();
        if (window != client.getWindow().getWindow() || client.player == null
            || !ClientPlayNetworking.canSend(WovenOrbTriggers.KEY_C2S)) {
            return;
        }
        String name = key.getName();
        if (action == GLFW.GLFW_RELEASE) {
            if (DOWN.remove(name)) {
                send(name, false, client.options.keyUse.isDown());
            }
        } else if (action == GLFW.GLFW_PRESS && client.screen == null && watched(client).contains(name)
            && DOWN.add(name)) {
            send(name, true, client.options.keyUse.isDown());
        }
    }

    private static void releaseUnwatched(Minecraft client) {
        if (DOWN.isEmpty()) {
            return;
        }
        if (client.player == null || !ClientPlayNetworking.canSend(WovenOrbTriggers.KEY_C2S)) {
            DOWN.clear();
            return;
        }
        Set<String> watched = watched(client);
        for (String key : Set.copyOf(DOWN)) {
            if (!watched.contains(key)) {
                DOWN.remove(key);
                send(key, false, client.options.keyUse.isDown());
            }
        }
    }

    private static Set<String> watched(Minecraft client) {
        Set<String> watched = new HashSet<>();
        for (InteractionHand hand : InteractionHand.values()) {
            for (WeaveTrigger trigger : WovenOrbItem.listensFor(client.player.getItemInHand(hand))) {
                if (trigger.kind().keyed()) {
                    watched.add(trigger.key());
                }
            }
        }
        return watched;
    }

    private static void send(String key, boolean down, boolean using) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(key, WeaveTrigger.MAX_KEY_LENGTH);
        buf.writeBoolean(down);
        buf.writeBoolean(using);
        ClientPlayNetworking.send(WovenOrbTriggers.KEY_C2S, buf);
    }
}

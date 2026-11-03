package com.bluup.hexwright.client.armour;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public final class ClientArmourWearer {

    private ClientArmourWearer() {
    }

    public static LivingEntity localPlayer() {
        return net.minecraft.client.Minecraft.getInstance().player;
    }
}

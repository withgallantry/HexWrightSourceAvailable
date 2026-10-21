package com.bluup.hexwright.client.render;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.lib.HexItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class SoulglimmerColor {

    private static final Map<UUID, ColorProvider> PROVIDERS = new ConcurrentHashMap<>();

    private SoulglimmerColor() {
    }

    public static int now() {
        Minecraft client = Minecraft.getInstance();
        UUID owner = client.player != null ? client.player.getUUID() : Util.NIL_UUID;
        ColorProvider provider = PROVIDERS.computeIfAbsent(owner, uuid ->
            new FrozenPigment(new ItemStack(HexItems.UUID_PIGMENT), uuid).getColorProvider());

        float time = client.level != null
            ? client.level.getGameTime() + client.getFrameTime()
            : (System.currentTimeMillis() / 50f);
        return provider.getColor(time, Vec3.ZERO);
    }

    public static int atFullBrightness(int color) {
        int red = FastColor.ARGB32.red(color);
        int green = FastColor.ARGB32.green(color);
        int blue = FastColor.ARGB32.blue(color);
        int strongest = Math.max(red, Math.max(green, blue));
        if (strongest == 0) {
            return 0xFFFFFFFF;
        }
        return FastColor.ARGB32.color(255, red * 255 / strongest, green * 255 / strongest, blue * 255 / strongest);
    }
}

package com.bluup.hexwright.client.dust;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class DustTint {

    static final float[] AUTHORED = {
        0.42f, 0.31f, 0.49f,
        0.87f, 0.75f, 0.91f,
        0.98f, 0.82f, 1.0f,
    };

    private DustTint() {
    }

    static @Nullable ColorProvider of(@Nullable Entity caster) {
        if (DustConfig.pigmentTint == 0 || !(caster instanceof Player player)) {
            return null;
        }
        FrozenPigment pigment = IXplatAbstractions.INSTANCE.getPigment(player);
        if (pigment == null || pigment.item().is(HexItems.DEFAULT_PIGMENT)) {
            return null;
        }
        return IXplatAbstractions.INSTANCE.getColorProvider(pigment);
    }

    static int sample(@Nullable ColorProvider provider, float time, double x, double y, double z) {
        if (provider == null) {
            return -1;
        }
        try {
            return provider.getColor(time, new Vec3(x, y, z)) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static void recolour(float r, float g, float b, int rgb, float[] out, int offset) {
        float luminance = luma(r, g, b);
        float pr = ((rgb >> 16) & 0xFF) / 255.0f;
        float pg = ((rgb >> 8) & 0xFF) / 255.0f;
        float pb = (rgb & 0xFF) / 255.0f;
        float pl = luma(pr, pg, pb);
        float cr = pr - pl;
        float cg = pg - pl;
        float cb = pb - pl;
        float scale = Math.max(DustConfig.pigmentStrength, 0.0f);
        scale = fit(scale, luminance, cr);
        scale = fit(scale, luminance, cg);
        scale = fit(scale, luminance, cb);
        out[offset] = luminance + cr * scale;
        out[offset + 1] = luminance + cg * scale;
        out[offset + 2] = luminance + cb * scale;
    }

    static void palette(int rgb, float[] out) {
        for (int i = 0; i < AUTHORED.length; i += 3) {
            recolour(AUTHORED[i], AUTHORED[i + 1], AUTHORED[i + 2], rgb, out, i);
        }
    }

    private static float fit(float scale, float luminance, float chroma) {
        if (chroma > 1.0e-4f) {
            return Math.min(scale, (1.0f - luminance) / chroma);
        }
        if (chroma < -1.0e-4f) {
            return Math.min(scale, luminance / -chroma);
        }
        return scale;
    }

    private static float luma(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }
}

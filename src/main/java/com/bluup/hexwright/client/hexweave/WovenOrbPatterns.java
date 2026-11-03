package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.ClientTickCounter;
import at.petrak.hexcasting.client.render.HexPatternLike;
import at.petrak.hexcasting.client.render.PatternTextureManager;
import at.petrak.hexcasting.client.render.WorldlyPatternRenderHelpers;
import com.bluup.hexwright.client.render.SoulglimmerColor;
import com.bluup.hexwright.server.hexweave.HexweaveNbt;
import com.bluup.hexwright.server.hexweave.WovenOrbItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class WovenOrbPatterns {

    private static final int SLOTS = 3;
    private static final float CYCLE_TICKS = 80.0F;
    private static final float HALF_SIZE = 0.3F;
    private static final float DRIFT = 0.05F;
    private static final float PEAK_ALPHA = 0.95F;
    private static final float CORE_WHITENING = 0.6F;
    private static final int RESOLUTION = 128;

    private WovenOrbPatterns() {
    }

    public static void onOrbRendered(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers) {
        if (!(stack.getItem() instanceof WovenOrbItem) || !WovenOrbItem.hasWeave(stack)) {
            return;
        }
        CompoundTag weave = stack.getTag().getCompound(WovenOrbItem.TAG_WEAVE);
        int count = HexweaveNbt.peekPatternCount(weave);
        if (count == 0) {
            return;
        }

        Matrix4f pose = poseStack.last().pose();
        Vector3f centre = pose.transformPosition(0.5F, 0.5F, 0.5F, new Vector3f());
        float scale = pose.getScale(new Vector3f()).x();
        float up = pose.determinant3x3() < 0 ? -1.0F : 1.0F;
        int glow = SoulglimmerColor.atFullBrightness(SoulglimmerColor.now());
        int core = towardsWhite(glow, CORE_WHITENING);

        float time = ClientTickCounter.getTotal() / CYCLE_TICKS;
        for (int slot = 0; slot < SLOTS; slot++) {
            float phase = time + (float) slot / SLOTS;
            long cycle = (long) Math.floor(phase);
            float alpha = Mth.sin((phase - cycle) * Mth.PI) * PEAK_ALPHA;
            RandomSource random = new XoroshiroRandomSource(cycle * SLOTS + slot);
            HexPattern pattern = HexweaveNbt.peekPattern(weave, random.nextInt(count));
            if (pattern == null) {
                continue;
            }
            Optional<Map<String, ResourceLocation>> textures = PatternTextureManager.getTextures(
                HexPatternLike.of(pattern), WorldlyPatternRenderHelpers.WORLDLY_SETTINGS, 0, RESOLUTION);
            if (textures.isEmpty()) {
                continue;
            }
            float angle = random.nextFloat() * Mth.TWO_PI;
            Vector3f at = new Vector3f(centre).add(
                (random.nextFloat() * 2.0F - 1.0F) * DRIFT * scale, (random.nextFloat() * 2.0F - 1.0F) * DRIFT * scale, 0.0F);
            float half = HALF_SIZE * scale;
            card(buffers, textures.get().get("outer"), at, half, angle, up, withAlpha(glow, alpha));
            card(buffers, textures.get().get("inner"), at, half, angle, up, withAlpha(core, alpha));
        }
    }

    private static void card(MultiBufferSource buffers, ResourceLocation texture, Vector3f centre, float half,
                             float angle, float up, int argb) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        for (int corner = 0; corner < 4; corner++) {
            float u = corner >= 2 ? 1.0F : 0.0F;
            float v = corner == 1 || corner == 2 ? 1.0F : 0.0F;
            float x = (u * 2.0F - 1.0F) * half;
            float y = (1.0F - v * 2.0F) * half * up;
            consumer.vertex(centre.x() + x * cos - y * sin, centre.y() + x * sin + y * cos, centre.z())
                .color(argb)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
        }
    }

    private static int towardsWhite(int color, float amount) {
        return FastColor.ARGB32.color(255,
            Mth.lerpInt(amount, FastColor.ARGB32.red(color), 255),
            Mth.lerpInt(amount, FastColor.ARGB32.green(color), 255),
            Mth.lerpInt(amount, FastColor.ARGB32.blue(color), 255));
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24);
    }
}

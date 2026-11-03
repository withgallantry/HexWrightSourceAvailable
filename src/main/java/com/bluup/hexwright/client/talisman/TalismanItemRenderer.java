package com.bluup.hexwright.client.talisman;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.talisman.TalismanDesign;
import com.bluup.hexwright.server.talisman.TalismanItem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TalismanItemRenderer {

    private static final float MODEL_FRONT_Z = 8.5f / 16f;
    private static final float MODEL_BACK_Z = 7.5f / 16f;
    private static final float Z_OFFSET = 0.002f;

    private static final int MAX_CACHED_DESIGNS = 256;

    private static final Map<DesignKey, ResourceLocation> TEXTURE_CACHE =
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<DesignKey, ResourceLocation> eldest) {
                if (size() <= MAX_CACHED_DESIGNS) {
                    return false;
                }
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
        };

    private static int textureSerial;

    private TalismanItemRenderer() {
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearCache());
    }

    private static void clearCache() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        TEXTURE_CACHE.values().forEach(textureManager::release);
        TEXTURE_CACHE.clear();
    }

    public static void renderDevice(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
                                    int light, int overlay) {
        if (!(stack.getItem() instanceof TalismanItem)) {
            return;
        }
        byte[] pixels = TalismanDesign.getPixels(stack);
        long mask = TalismanDesign.getMask(stack);
        if (pixels == null || mask == 0L) {
            return;
        }

        ResourceLocation texture = textureFor(pixels, mask);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutout(texture));
        PoseStack.Pose pose = poseStack.last();

        float left = TalismanDesign.ORIGIN_X / 16f;
        float right = (TalismanDesign.ORIGIN_X + TalismanDesign.WIDTH) / 16f;
        float top = 1f - TalismanDesign.ORIGIN_Y / 16f;
        float bottom = 1f - (TalismanDesign.ORIGIN_Y + TalismanDesign.HEIGHT) / 16f;

        float frontZ = MODEL_FRONT_Z + Z_OFFSET;
        vertex(buffer, pose, left, bottom, frontZ, 0f, 1f, light, overlay, 1f);
        vertex(buffer, pose, right, bottom, frontZ, 1f, 1f, light, overlay, 1f);
        vertex(buffer, pose, right, top, frontZ, 1f, 0f, light, overlay, 1f);
        vertex(buffer, pose, left, top, frontZ, 0f, 0f, light, overlay, 1f);

        float backZ = MODEL_BACK_Z - Z_OFFSET;
        vertex(buffer, pose, right, bottom, backZ, 0f, 1f, light, overlay, -1f);
        vertex(buffer, pose, left, bottom, backZ, 1f, 1f, light, overlay, -1f);
        vertex(buffer, pose, left, top, backZ, 1f, 0f, light, overlay, -1f);
        vertex(buffer, pose, right, top, backZ, 0f, 0f, light, overlay, -1f);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, int overlay, float normalZ) {
        buffer.vertex(pose.pose(), x, y, z)
            .color(255, 255, 255, 255)
            .uv(u, v)
            .overlayCoords(overlay)
            .uv2(light)
            .normal(pose.normal(), 0f, 0f, normalZ)
            .endVertex();
    }


    private static ResourceLocation textureFor(byte[] pixels, long mask) {
        return TEXTURE_CACHE.computeIfAbsent(new DesignKey(pixels, mask), key -> {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, TalismanDesign.WIDTH, TalismanDesign.HEIGHT, false);
            for (int y = 0; y < TalismanDesign.HEIGHT; y++) {
                for (int x = 0; x < TalismanDesign.WIDTH; x++) {
                    int index = TalismanDesign.index(x, y);
                    int abgr = TalismanDesign.isPainted(key.mask(), index)
                        ? toAbgr(TalismanDesign.argb(TalismanDesign.paletteIndexAt(key.pixels(), index)))
                        : 0;
                    image.setPixelRGBA(x, y, abgr);
                }
            }
            ResourceLocation location = Hexwright.id("talisman_design/" + textureSerial++);
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
            return location;
        });
    }

    private static int toAbgr(int argb) {
        return argb & 0xFF00FF00
            | (argb & 0x00FF0000) >> 16
            | (argb & 0x000000FF) << 16;
    }

    private record DesignKey(byte[] pixels, long mask) {
        @Override
        public boolean equals(Object other) {
            return other instanceof DesignKey key
                && this.mask == key.mask
                && Arrays.equals(this.pixels, key.pixels);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(this.pixels) + Long.hashCode(this.mask);
        }
    }
}

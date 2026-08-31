package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.fluid.HexidTank;
import com.bluup.hexwright.server.fluid.HexidTankBlock;
import com.bluup.hexwright.server.fluid.HexidTankBlockEntity;
import com.bluup.hexwright.server.fluid.HexidTankColumn;
import com.bluup.hexwright.server.fluid.TankPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class HexidTankRenderer implements BlockEntityRenderer<HexidTankBlockEntity> {

    private static final float WALL_MIN = 2.6f / 16f;
    private static final float WALL_MAX = 13.6f / 16f;
    private static final float FLOOR = 4f / 16f;
    private static final float CEILING = 14f / 16f;

    private static final int WATER_TINT = 0x3F76E4;
    private static final int HEXID_TINT = 0xB05CFF;

    private static final int GLOW = 11;

    private static final ResourceLocation WATER_STILL =
        new ResourceLocation("minecraft", "block/water_still");

    public HexidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.HEXID_TANK_BLOCK_ENTITY, HexidTankRenderer::new);
    }

    @Override
    public void render(HexidTankBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        HexidTankBlockEntity column = HexidTankColumn.controller(blockEntity.getLevel(), blockEntity.getBlockPos());
        if (column == null || column.amountMb() <= 0) {
            return;
        }

        int height = column.columnHeight();
        long capacity = HexidTank.capacityMb(height);
        if (capacity <= 0) {
            return;
        }

        int index = blockEntity.getBlockPos().getY() - column.getBlockPos().getY();
        float innerHeight = (height - 1) + (CEILING - FLOOR);
        float surface = FLOOR + innerHeight * Mth.clamp((float) column.amountMb() / capacity, 0f, 1f);

        BlockState state = blockEntity.getBlockState();
        TankPart part = state.hasProperty(HexidTankBlock.PART) ? state.getValue(HexidTankBlock.PART) : TankPart.SOLO;
        float sliceBottom = index + (part == TankPart.MIDDLE || part == TankPart.TOP ? 0f : FLOOR);
        float sliceTop = index + (part == TankPart.MIDDLE || part == TankPart.BOTTOM ? 1f : CEILING);

        float from = Math.max(sliceBottom, FLOOR);
        float to = Math.min(sliceTop, surface);
        if (to <= from) {
            return;
        }

        float density = (float) column.saturation();
        int tint = lerpColour(WATER_TINT, HEXID_TINT, density);
        int light = LightTexture.pack(
            Math.max(LightTexture.block(packedLight), Math.round(density * GLOW)),
            LightTexture.sky(packedLight));

        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(WATER_STILL);
        VertexConsumer buffer = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());

        draw(poseStack, buffer, sprite, from - index, to - index, tint, light, to >= surface);
    }

    private static void draw(PoseStack poseStack, VertexConsumer buffer, TextureAtlasSprite sprite,
                             float y0, float y1, int tint, int light, boolean capped) {
        float r = ((tint >> 16) & 0xFF) / 255f;
        float g = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        float a = 0.86f;

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float x0 = WALL_MIN;
        float x1 = WALL_MAX;
        float z0 = WALL_MIN;
        float z1 = WALL_MAX;

        float span = WALL_MAX - WALL_MIN;
        float u0 = sprite.getU(0);
        float u1 = sprite.getU(16 * span);
        float vSide0 = sprite.getV(0);
        float vSide1 = sprite.getV(16 * (y1 - y0));
        float vTop1 = sprite.getV(16 * span);

        quad(pose, normal, buffer, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0,
            u0, vSide0, u1, vSide1, r, g, b, a, light, 0f, 0f, -1f);
        quad(pose, normal, buffer, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1,
            u0, vSide0, u1, vSide1, r, g, b, a, light, 0f, 0f, 1f);
        quad(pose, normal, buffer, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1,
            u0, vSide0, u1, vSide1, r, g, b, a, light, -1f, 0f, 0f);
        quad(pose, normal, buffer, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0,
            u0, vSide0, u1, vSide1, r, g, b, a, light, 1f, 0f, 0f);

        if (capped) {
            quad(pose, normal, buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                u0, vSide0, u1, vTop1, r, g, b, a, light, 0f, 1f, 0f);
        }
    }

    private static void quad(Matrix4f pose, Matrix3f normal, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1,
                             float r, float g, float b, float a, int light,
                             float nx, float ny, float nz) {
        vertex(pose, normal, buffer, ax, ay, az, u0, v0, r, g, b, a, light, nx, ny, nz);
        vertex(pose, normal, buffer, bx, by, bz, u1, v0, r, g, b, a, light, nx, ny, nz);
        vertex(pose, normal, buffer, cx, cy, cz, u1, v1, r, g, b, a, light, nx, ny, nz);
        vertex(pose, normal, buffer, dx, dy, dz, u0, v1, r, g, b, a, light, nx, ny, nz);
    }

    private static void vertex(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, float x, float y, float z,
                               float u, float v, float r, float g, float b, float a, int light,
                               float nx, float ny, float nz) {
        buffer.vertex(pose, x, y, z)
            .color(r, g, b, a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(normal, nx, ny, nz)
            .endVertex();
    }

    private static int lerpColour(int from, int to, float t) {
        float clamped = Mth.clamp(t, 0f, 1f);
        int r = channel(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = channel(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = channel(clamped, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(float t, int from, int to) {
        return Mth.clamp(Math.round(Mth.lerp(t, from, to)), 0, 255);
    }
}

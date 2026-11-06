package com.bluup.hexwright.client.maintenance;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.hexahedron.WardCrystal;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.maintenance.KeystoneStudy;
import com.bluup.hexwright.server.maintenance.MaintenanceCrystalBlockEntity;
import com.bluup.hexwright.server.maintenance.MaintenanceRunes;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class MaintenanceCrystalRenderer implements BlockEntityRenderer<MaintenanceCrystalBlockEntity> {

    private static final ResourceLocation RUNES = Hexwright.id("textures/gui/research/runes.png");
    private static final WardCrystal.Shape SHAPE = new WardCrystal.Shape(MaintenanceRunes.HALF_LENGTH,
        MaintenanceRunes.RADIUS, MaintenanceRunes.BODY, 1.0F / 16.0F);
    private static final float PROUD = 0.006F;
    private static final int LIT = 0x4CD6FF;
    private static final int PALE = 0xB8CBE0;
    private static final int WRONG = 0xD86A64;
    private static final float FLASH_TICKS = 12.0F;
    private static final float TIME_WRAP_TICKS = 24000.0F;

    private static final Set<MaintenanceCrystalBlockEntity> SEEN = new LinkedHashSet<>();

    public MaintenanceCrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(KeystoneStudy.MAINTENANCE_CRYSTAL_BLOCK_ENTITY, MaintenanceCrystalRenderer::new);
        WorldRenderEvents.AFTER_ENTITIES.register(MaintenanceCrystalRenderer::draw);
    }

    @Override
    public void render(MaintenanceCrystalBlockEntity crystal, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        SEEN.add(crystal);
    }

    @Override
    public boolean shouldRenderOffScreen(MaintenanceCrystalBlockEntity crystal) {
        return true;
    }

    private static void draw(WorldRenderContext context) {
        try {
            if (SEEN.isEmpty() || IrisCompat.isRenderingShadowPass() || PortalViewRenderer.isRenderingView()
                || RemoteLevelManager.isRemotePassActive()) {
                return;
            }
            ClientLevel level = context.world();
            float partialTick = context.tickDelta();
            float time = (ClientTickCounter.getTotal() % TIME_WRAP_TICKS) / 20.0F;
            ShaderInstance shader = WardCrystal.shader(time, level.getSkyDarken(partialTick));
            if (shader == null) {
                return;
            }
            double gameTime = level.getGameTime() + partialTick;
            Vec3 eye = context.camera().getPosition();

            BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
            BlendModeAccessor.hexwright$setLastApplied(null);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            PoseStack modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            modelView.setIdentity();
            modelView.mulPoseMatrix(context.matrixStack().last().pose());
            RenderSystem.applyModelViewMatrix();

            for (MaintenanceCrystalBlockEntity crystal : SEEN) {
                drawCrystal(shader, crystal, eye, gameTime);
            }
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, RUNES);
            RenderSystem.defaultBlendFunc();
            drawRunes(false, eye, gameTime);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            drawRunes(true, eye, gameTime);

            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            BlendModeAccessor.hexwright$setLastApplied(previousBlend);
        } finally {
            SEEN.clear();
        }
    }

    private static void drawCrystal(ShaderInstance shader, MaintenanceCrystalBlockEntity crystal, Vec3 eye,
                                    double gameTime) {
        float entered = crystal.engaged() ? 1.0F : crystal.progress() / (float) MaintenanceRunes.ORDER.length;
        float pulse = crystal.engaged() ? 0.15F + 0.1F * Mth.sin((float) gameTime * 0.08F) : 0.0F;
        WardCrystal.draw(shader, MaintenanceRunes.centre(crystal.getBlockPos()), new Matrix3f(), SHAPE,
            new WardCrystal.Look(entered, 0.3F + 0.6F * entered, Math.max(pulse, flash(crystal, gameTime) * 0.4F),
                0.0F, 1.0F, crystal.getBlockPos().hashCode() % 97), eye);
    }

    private static void drawRunes(boolean lit, Vec3 eye, double gameTime) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (MaintenanceCrystalBlockEntity crystal : SEEN) {
            Vec3 centre = MaintenanceRunes.centre(crystal.getBlockPos()).subtract(eye);
            float flash = flash(crystal, gameTime);
            for (int slot = 0; slot < MaintenanceRunes.SLOTS; slot++) {
                boolean on = crystal.engaged() || pressed(crystal, slot);
                boolean flashing = slot == crystal.flashSlot() && flash > 0.0F;
                if (lit) {
                    if (flashing && crystal.flashWrong()) {
                        rune(builder, centre, slot, 1.0F, WRONG, flash);
                    } else if (on) {
                        float strength = flashing ? 1.0F : 0.8F;
                        rune(builder, centre, slot, 1.3F, LIT, 0.3F * strength);
                        rune(builder, centre, slot, 1.0F, LIT, strength);
                    }
                } else if (!on) {
                    rune(builder, centre, slot, 1.0F, PALE, 0.55F);
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static boolean pressed(MaintenanceCrystalBlockEntity crystal, int slot) {
        for (int step = 0; step < crystal.progress(); step++) {
            if (MaintenanceRunes.expected(step) == slot) {
                return true;
            }
        }
        return false;
    }

    private static float flash(MaintenanceCrystalBlockEntity crystal, double gameTime) {
        return Mth.clamp(1.0F - (float) (gameTime - crystal.flashedAt()) / FLASH_TICKS, 0.0F, 1.0F);
    }

    private static void rune(BufferBuilder builder, Vec3 centre, int slot, float scale, int colour, float alpha) {
        double angle = MaintenanceRunes.facetAngle(slot / MaintenanceRunes.ROWS);
        Vector3f out = new Vector3f((float) Math.cos(angle), 0.0F, (float) Math.sin(angle));
        Vector3f right = new Vector3f((float) Math.sin(angle), 0.0F, (float) -Math.cos(angle));
        Vector3f middle = new Vector3f((float) centre.x, (float) centre.y, (float) centre.z)
            .add(out.mul((float) MaintenanceRunes.apothem() + PROUD, new Vector3f()))
            .add(0.0F, MaintenanceRunes.rowHeight(slot % MaintenanceRunes.ROWS), 0.0F);
        float half = MaintenanceRunes.RUNE_SIZE * scale / 2.0F;
        int glyph = MaintenanceRunes.glyph(slot);
        float u0 = (glyph % 4) * 0.25F;
        float v0 = (glyph / 4) * 0.25F;
        float r = ((colour >> 16) & 0xFF) / 255.0F;
        float g = ((colour >> 8) & 0xFF) / 255.0F;
        float b = (colour & 0xFF) / 255.0F;
        corner(builder, middle, right, -half, -half, u0, v0 + 0.25F, r, g, b, alpha);
        corner(builder, middle, right, half, -half, u0 + 0.25F, v0 + 0.25F, r, g, b, alpha);
        corner(builder, middle, right, half, half, u0 + 0.25F, v0, r, g, b, alpha);
        corner(builder, middle, right, -half, half, u0, v0, r, g, b, alpha);
    }

    private static void corner(BufferBuilder builder, Vector3f middle, Vector3f right, float across, float up,
                               float u, float v, float r, float g, float b, float alpha) {
        builder.vertex(middle.x + right.x * across, middle.y + up, middle.z + right.z * across)
            .uv(u, v).color(r, g, b, alpha).endVertex();
    }
}

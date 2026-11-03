package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.BloodTreeEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class BloodTreeAura extends GeoRenderLayer<BloodTreeEntity> {

    private static final ResourceLocation TEXTURE = Hexwright.id("textures/entity/blood_tree_aura.png");

    private static final float INFLATE = 0.08F;
    private static final float TILES_ACROSS = 256.0F / 32.0F;
    private static final float TILES_DOWN = 128.0F / 32.0F;
    private static final float FLOW = 0.012F;
    private static final float RESTING = 0.12F;
    private static final float BEATING = 0.5F;

    public BloodTreeAura(GeoRenderer<BloodTreeEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, BloodTreeEntity tree, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        float presence = tree.presence();
        float strength = presence * presence * Mth.lerp(tree.heartbeat(partialTick), RESTING, BEATING);
        if (strength <= 0.0F) {
            return;
        }
        VertexConsumer shell = new Shell(bufferSource.getBuffer(Type.AURA), (tree.age() + partialTick) * FLOW);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, tree, Type.AURA, shell, partialTick,
            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, strength, strength, strength, 1.0F);
    }

    private static final class Shell implements VertexConsumer {

        private static final float SMALLEST = 0.03F;

        private final VertexConsumer inner;
        private final float flow;
        private final Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        private final float[] u = new float[4];
        private final float[] v = new float[4];
        private final Vector3f normal = new Vector3f();
        private int count;
        private float red;
        private float green;
        private float blue;
        private float alpha;
        private int overlay;
        private int light;

        private Shell(VertexConsumer inner, float flow) {
            this.inner = inner;
            this.flow = flow;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u,
                           float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            this.corners[this.count].set(x, y, z);
            this.u[this.count] = u;
            this.v[this.count] = v;
            if (++this.count < 4) {
                return;
            }
            this.count = 0;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.overlay = overlay;
            this.light = light;
            this.normal.set(normalX, normalY, normalZ).normalize();
            inflate();
        }

        private void inflate() {
            if (this.normal.dot(this.corners[0]) <= 0.0F) {
                return;
            }
            Vector3f across = this.corners[1].sub(this.corners[0], new Vector3f());
            Vector3f along = this.corners[3].sub(this.corners[0], new Vector3f());
            if (across.length() < SMALLEST || along.length() < SMALLEST) {
                return;
            }
            across.normalize();
            along.normalize();
            Vector3f middle = new Vector3f(this.corners[0]).add(this.corners[2]).mul(0.5F);
            Vector3f out = new Vector3f();
            for (int n = 0; n < 4; n++) {
                Vector3f corner = this.corners[n];
                out.set(corner).sub(middle);
                float sideways = Math.signum(out.dot(across));
                float lengthways = Math.signum(out.dot(along));
                out.set(this.normal).fma(sideways, across).fma(lengthways, along).mul(INFLATE).add(corner);
                this.inner.vertex(out.x, out.y, out.z, this.red, this.green, this.blue, this.alpha,
                    this.u[n] * TILES_ACROSS, this.v[n] * TILES_DOWN + this.flow, this.overlay, this.light,
                    this.normal.x, this.normal.y, this.normal.z);
            }
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.inner.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            this.inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            this.inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            this.inner.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            this.inner.unsetDefaultColor();
        }
    }

    private static final class Type extends RenderType {

        private static final RenderType AURA = create("hexwright_blood_tree_aura", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)
                .setTextureState(new TextureStateShard(TEXTURE, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));

        private Type(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                     boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }
    }
}

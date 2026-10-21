package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.render.SoulglimmerColor;
import com.bluup.hexwright.server.hexweave.HexLoom;
import com.bluup.hexwright.server.hexweave.WovenOrbItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public final class WovenOrbModel extends ForwardingBakedModel {

    private static final ModelResourceLocation ITEM_MODEL =
        new ModelResourceLocation(Hexwright.MOD_ID, "woven_orb", "inventory");
    private static final ResourceLocation LIQUID = Hexwright.id("item/woven_orb/liquid");
    private static final int LIQUID_TINT = 0;
    private static final int GLASS_TINT = 1;
    private static final int UNDYED_GLASS = 0xFFCEB8FF;
    private static final ResourceLocation WOVEN = Hexwright.id("woven");

    private final Mesh mesh;

    private WovenOrbModel(BakedModel model, Renderer renderer) {
        this.wrapped = model;
        RenderMaterial liquid = renderer.materialFinder().blendMode(BlendMode.CUTOUT).emissive(true).find();
        RenderMaterial glass = renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT).find();
        MeshBuilder builder = renderer.meshBuilder();
        QuadEmitter emitter = builder.getEmitter();
        RandomSource random = RandomSource.create();
        for (int face = 0; face <= ModelHelper.NULL_FACE_ID; face++) {
            Direction cullFace = ModelHelper.faceFromIndex(face);
            for (BakedQuad quad : model.getQuads(null, cullFace, random)) {
                boolean isLiquid = quad.getSprite().contents().name().equals(LIQUID);
                emitter.fromVanilla(quad, isLiquid ? liquid : glass, cullFace).emit();
            }
        }
        this.mesh = builder.build();
    }

    public static void register() {
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> switch (tintIndex) {
            case LIQUID_TINT -> SoulglimmerColor.atFullBrightness(SoulglimmerColor.now());
            case GLASS_TINT -> stack.getItem() instanceof WovenOrbItem orb && orb.hasCustomColor(stack)
                ? 0xFF000000 | orb.getColor(stack) : UNDYED_GLASS;
            default -> 0xFFFFFFFF;
        }, HexLoom.WOVEN_ORB);
        ItemProperties.register(HexLoom.WOVEN_ORB, WOVEN,
            (stack, level, entity, seed) -> WovenOrbItem.hasWeave(stack) ? 1.0F : 0.0F);
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, baked) -> {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            return model != null && renderer != null && ITEM_MODEL.equals(baked.id())
                ? new WovenOrbModel(model, renderer)
                : model;
        }));
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        mesh.outputTo(context.getEmitter());
    }
}

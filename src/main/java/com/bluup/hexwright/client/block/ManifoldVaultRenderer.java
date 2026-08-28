package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.ReliquaryBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class ManifoldVaultRenderer implements BlockEntityRenderer<ReliquaryBlockEntity> {
    private static final float CYCLE_TICKS = 60.0f;
    private static final float FADE_IN_PORTION = 0.30f;
    private static final float HOLD_PORTION = 0.40f;
    private static final float MAX_WORLD_SCALE = 0.40f;

    public ManifoldVaultRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(
            com.bluup.hexwright.server.block.HexwrightBlocks.RELIQUARY_BLOCK_ENTITY,
            ManifoldVaultRenderer::new
        );
    }

    @Override
    public void render(
        ReliquaryBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        if (blockEntity.getLevel() == null) {
            return;
        }

        List<ItemStack> populated = new ArrayList<>();
        for (int slot = 0; slot < blockEntity.getContainerSize(); slot++) {
            ItemStack stack = blockEntity.getItem(slot);
            if (!stack.isEmpty()) {
                populated.add(stack);
            }
        }
        if (populated.isEmpty()) {
            return;
        }

        float time = blockEntity.getLevel().getGameTime() + partialTick;
        long cycle = Mth.floor(time / CYCLE_TICKS);
        float cycleProgress = (time - (cycle * CYCLE_TICKS)) / CYCLE_TICKS;

        float visibility = cycleVisibility(cycleProgress);
        if (visibility <= 0.01f) {
            return;
        }

        ItemStack shown = chooseStack(populated, blockEntity.getBlockPos().asLong(), cycle);
        float scale = MAX_WORLD_SCALE * visibility;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.scale(scale, scale, scale);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, visibility);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            shown,
            ItemDisplayContext.GUI,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            bufferSource,
            blockEntity.getLevel(),
            (int) (blockEntity.getBlockPos().asLong() ^ cycle)
        );
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();
    }

    private static ItemStack chooseStack(List<ItemStack> stacks, long posSeed, long cycle) {
        if (stacks.size() == 1) {
            return stacks.get(0);
        }
        long cycleSeed = posSeed ^ (cycle * 341873128712L) ^ (cycle * 132897987541L);
        RandomSource random = RandomSource.create(cycleSeed);
        return stacks.get(random.nextInt(stacks.size()));
    }

    private static float cycleVisibility(float progress) {
        if (progress < FADE_IN_PORTION) {
            return smooth01(progress / FADE_IN_PORTION);
        }

        float holdEnd = FADE_IN_PORTION + HOLD_PORTION;
        if (progress < holdEnd) {
            return 1.0f;
        }

        float fadeOutPortion = 1.0f - holdEnd;
        if (fadeOutPortion <= 0.0f) {
            return 1.0f;
        }
        float fadeOutT = Mth.clamp((progress - holdEnd) / fadeOutPortion, 0.0f, 1.0f);
        return 1.0f - smooth01(fadeOutT);
    }

    private static float smooth01(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }
}

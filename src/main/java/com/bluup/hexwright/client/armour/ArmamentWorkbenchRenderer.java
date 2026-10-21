package com.bluup.hexwright.client.armour;

import com.bluup.hexwright.server.armour.ArmamentWorkbenchBlock;
import com.bluup.hexwright.server.armour.ArmamentWorkbenchBlockEntity;
import com.bluup.hexwright.server.armour.HexwrightArmourItem;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.client.RenderProvider;

public final class ArmamentWorkbenchRenderer implements BlockEntityRenderer<ArmamentWorkbenchBlockEntity> {

    private static final float SCALE = 0.5f;

    private static final float PLINTH_TOP = 11f;

    private static final float BUST_BACK = 1f;

    private static final float WEAPON_REST_TOP = 12f;
    private static final float WEAPON_FRONT = 4.5f;

    private static final float WEAPON_SCALE = 0.33f;

    private static final float WEAPON_LIFT = 0.5f;

    private final HumanoidModel<LivingEntity> stance;

    private @Nullable ArmorStand wearer;

    public ArmamentWorkbenchRenderer(BlockEntityRendererProvider.Context context) {
        stance = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        stance.young = false;
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.ARMAMENT_WORKBENCH_BLOCK_ENTITY, ArmamentWorkbenchRenderer::new);
    }

    @Override
    public void render(ArmamentWorkbenchBlockEntity bench, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack stack = bench.piece();
        Level level = bench.getLevel();
        if (stack.isEmpty() || level == null) {
            return;
        }
        Direction facing = bench.getBlockState().getValue(ArmamentWorkbenchBlock.FACING);
        if (stack.getItem() instanceof HexwrightArmourItem piece) {
            renderArmour(piece, stack, facing, level, poseStack, packedLight);
        } else {
            renderWeapon(stack, facing, level, poseStack, bufferSource, packedLight,
                (int) bench.getBlockPos().asLong());
        }
    }

    private void renderArmour(HexwrightArmourItem piece, ItemStack stack, Direction facing, Level level,
                              PoseStack poseStack, int packedLight) {
        EquipmentSlot slot = piece.getEquipmentSlot();
        poseStack.pushPose();
        poseStack.translate(0.5, (PLINTH_TOP + reachBelowNeck(slot) * SCALE) / 16f, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        poseStack.translate(0, 0, BUST_BACK / 16f);
        poseStack.scale(-SCALE, -SCALE, SCALE);

        HumanoidModel<LivingEntity> model = RenderProvider.of(stack)
            .getHumanoidArmorModel(wearer(level), stack, slot, stance);
        model.renderToBuffer(poseStack, null, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        poseStack.popPose();
    }

    private static void renderWeapon(ItemStack stack, Direction facing, Level level, PoseStack poseStack,
                                     MultiBufferSource bufferSource, int packedLight, int seed) {
        poseStack.pushPose();
        poseStack.translate(0.5, (WEAPON_REST_TOP + WEAPON_LIFT) / 16f, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        poseStack.translate(0, 0, -WEAPON_FRONT / 16f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(90f));
        poseStack.scale(WEAPON_SCALE, WEAPON_SCALE, WEAPON_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, bufferSource, level, seed);
        poseStack.popPose();
    }

    private static float reachBelowNeck(EquipmentSlot slot) {
        return switch (slot) {
            case CHEST -> 12f;
            case LEGS, FEET -> 24f;
            default -> 0f;
        };
    }

    private ArmorStand wearer(Level level) {
        if (wearer == null || wearer.level() != level) {
            wearer = new ArmorStand(EntityType.ARMOR_STAND, level);
        }
        return wearer;
    }
}

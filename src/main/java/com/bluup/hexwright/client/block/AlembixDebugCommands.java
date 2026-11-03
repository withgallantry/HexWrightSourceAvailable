package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.AlembixBlockEntity;
import com.bluup.hexwright.client.command.ClientCommandGate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.FloatArgumentType;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class AlembixDebugCommands {

    private AlembixDebugCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrightalembix")
                .requires(ClientCommandGate::creative)
                .then(ClientCommandManager.literal("lift")
                    .then(ClientCommandManager.argument("blocks", FloatArgumentType.floatArg(-16.0f, 16.0f))
                        .executes(ctx -> lift(ctx.getSource(), FloatArgumentType.getFloat(ctx, "blocks")))))
                .then(ClientCommandManager.literal("depthtest")
                    .then(ClientCommandManager.literal("on").executes(ctx -> depthTest(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off").executes(ctx -> depthTest(ctx.getSource(), false))))
                .then(ClientCommandManager.literal("depthmask")
                    .then(ClientCommandManager.literal("on").executes(ctx -> depthMask(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off").executes(ctx -> depthMask(ctx.getSource(), false))))
                .then(ClientCommandManager.literal("reset").executes(ctx -> reset(ctx.getSource())))
                .executes(ctx -> status(ctx.getSource()))));
    }

    private static int lift(FabricClientCommandSource source, float blocks) {
        AlembixVesselVisualClient.setDebugLift(blocks);
        feedback(source, "Alembix effect anchored " + blocks + " blocks above the block."
            + (blocks == 0.0f ? "" : " Particles appearing now were running all along, and hidden."));
        return SINGLE_SUCCESS;
    }

    private static int depthTest(FabricClientCommandSource source, boolean enabled) {
        AlembixVesselVisualClient.setDebugDepthTest(enabled);
        feedback(source, enabled
            ? "Alembix emitters keep the depth test they were authored with."
            : "Alembix emitters draw with no depth test - through the glass, and through everything else.");
        return SINGLE_SUCCESS;
    }

    private static int depthMask(FabricClientCommandSource source, boolean enabled) {
        AlembixVesselVisualClient.setDebugDepthMask(enabled);
        feedback(source, enabled
            ? "Alembix particles write depth - the vessel's far inner wall stays behind them."
            : "Alembix particles write no depth - the far inner wall paints back over them.");
        return SINGLE_SUCCESS;
    }

    private static int reset(FabricClientCommandSource source) {
        AlembixVesselVisualClient.setDebugLift(0.0f);
        AlembixVesselVisualClient.setDebugDepthTest(true);
        AlembixVesselVisualClient.setDebugDepthMask(true);
        feedback(source, "Alembix effect back to how it ships.");
        return SINGLE_SUCCESS;
    }

    private static int status(FabricClientCommandSource source) {
        feedback(source, "Effect asset hexwright:alembix "
            + (AlembixVesselVisualClient.isFxLoadable() ? "loaded." : "FAILED TO LOAD."));
        feedback(source, "lift=" + AlembixVesselVisualClient.debugLift()
            + " depthTest=" + AlembixVesselVisualClient.debugDepthTest()
            + " depthMask=" + AlembixVesselVisualClient.debugDepthMask());

        BlockPos pos = lookingAt();
        if (pos == null) {
            feedback(source, "Look at an Alembix for the rest.");
            return SINGLE_SUCCESS;
        }

        Level level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(pos) instanceof AlembixBlockEntity alembix)) {
            feedback(source, "No Alembix at " + pos + " - looking at " + level_name(level, pos) + ".");
            return SINGLE_SUCCESS;
        }

        StringBuilder tints = new StringBuilder();
        for (int index = 0; index < AlembixBlockEntity.INPUTS; index++) {
            int tint = alembix.inputTint(index);
            tints.append(index == 0 ? "" : "  ")
                .append(AlembixBlockEntity.Companion.getINPUT_SIDES()[index].getName())
                .append('=')
                .append(tint == AlembixBlockEntity.NO_TINT ? "none" : String.format("#%06X", tint));
        }
        feedback(source, "Faces as the CLIENT reads them: " + tints);

        int emitters = AlembixVesselVisualClient.liveEmitterCount(pos);
        feedback(source, emitters < 0
            ? "No effect running on this block."
            : "Effect running with " + emitters + " emitter(s); "
                + AlembixVesselVisualClient.liveParticleCount()
                + " particle(s) in the vessel pass across every Alembix.");
        return SINGLE_SUCCESS;
    }

    private static @Nullable BlockPos lookingAt() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
            ? blockHit.getBlockPos()
            : null;
    }

    private static String level_name(@Nullable Level level, BlockPos pos) {
        return level == null ? "nothing" : level.getBlockState(pos).getBlock().getName().getString();
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.AQUA));
    }
}

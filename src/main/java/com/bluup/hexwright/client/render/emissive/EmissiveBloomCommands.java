package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

final class EmissiveBloomCommands {
    private static final String[] DEBUG_MODE_NAMES = {
        "normal",
        "composite skipped",
        "showing the mask",
        "capture only",
    };

    private EmissiveBloomCommands() {
    }

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrightglow")
                .then(ClientCommandManager.literal("reload").executes(EmissiveBloomCommands::reload))
                .then(ClientCommandManager.literal("status").executes(EmissiveBloomCommands::status))
                .then(ClientCommandManager.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("undershaders")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setUnderShaders(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setUnderShaders(ctx.getSource(), false))))
                .then(setting("intensity", EmissiveBloomConfigManager::clampIntensity,
                    (config, value) -> config.intensity = value))
                .then(setting("threshold", EmissiveBloomConfigManager::clampGlowThreshold,
                    (config, value) -> config.glowThreshold = value))
                .then(setting("saturation", EmissiveBloomConfigManager::clampGlowSaturation,
                    (config, value) -> config.glowSaturation = value))
                .then(setting("knee", EmissiveBloomConfigManager::clampGlowKnee,
                    (config, value) -> config.glowKnee = value))
                .then(setting("core", EmissiveBloomConfigManager::clampCoreStrength,
                    (config, value) -> config.coreStrength = value))
                .then(setting("radius", EmissiveBloomConfigManager::clampBlurRadius,
                    (config, value) -> config.blurRadius = value))
                .then(setting("scale", EmissiveBloomConfigManager::clampFramebufferScale,
                    (config, value) -> config.framebufferScale = value))
                .then(ClientCommandManager.literal("blocks")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setBlockGlow(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setBlockGlow(ctx.getSource(), false)))
                    .then(ClientCommandManager.literal("strength")
                        .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                            .executes(EmissiveBloomCommands::setBlockGlowStrength)))
                    .then(ClientCommandManager.literal("distance")
                        .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(0, 128))
                            .executes(EmissiveBloomCommands::setBlockGlowDistance)))
                    .then(ClientCommandManager.literal("lift")
                        .then(ClientCommandManager.argument("blocks", FloatArgumentType.floatArg())
                            .executes(EmissiveBloomCommands::setBlockGlowLift)))
                    .then(ClientCommandManager.literal("undershaders")
                        .then(ClientCommandManager.literal("on")
                            .executes(ctx -> setBlockGlowUnderShaders(ctx.getSource(), true)))
                        .then(ClientCommandManager.literal("off")
                            .executes(ctx -> setBlockGlowUnderShaders(ctx.getSource(), false))))
                    .then(ClientCommandManager.literal("bias")
                        .then(ClientCommandManager.argument("units", FloatArgumentType.floatArg())
                            .executes(EmissiveBloomCommands::setBlockGlowDepthBias)))
                    .then(ClientCommandManager.literal("slope")
                        .then(ClientCommandManager.argument("factor", FloatArgumentType.floatArg())
                            .executes(EmissiveBloomCommands::setBlockGlowDepthSlope)))
                    .then(ClientCommandManager.literal("debug")
                        .then(ClientCommandManager.literal("on")
                            .executes(ctx -> setBlockGlowDebug(ctx.getSource(), true)))
                        .then(ClientCommandManager.literal("off")
                            .executes(ctx -> setBlockGlowDebug(ctx.getSource(), false))))
                    .then(ClientCommandManager.literal("trace")
                        .executes(ctx -> traceBlockGlow(ctx.getSource())))
                    .then(ClientCommandManager.literal("mipmap")
                        .then(ClientCommandManager.literal("on")
                            .executes(ctx -> setBlockGlowMipmap(ctx.getSource(), true)))
                        .then(ClientCommandManager.literal("off")
                            .executes(ctx -> setBlockGlowMipmap(ctx.getSource(), false)))))
                .then(ClientCommandManager.literal("items")
                    .then(ClientCommandManager.literal("trace")
                        .executes(ctx -> traceItemGlow(ctx.getSource()))))
                .then(ClientCommandManager.literal("debug")
                    .then(ClientCommandManager.argument("mode", IntegerArgumentType.integer(0, 3))
                        .executes(EmissiveBloomCommands::setDebugMode)))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> setting(String name, Clamp clamp, Setter setter) {
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                .executes(ctx -> {
                    float value = clamp.clamp(FloatArgumentType.getFloat(ctx, "value"));
                    setter.set(EmissiveBloomConfigManager.get(), value);
                    EmissiveBloomConfigManager.save();
                    feedback(ctx.getSource(), "Bloom " + name + " = " + value);
                    return SINGLE_SUCCESS;
                }));
    }

    private static int setBlockGlow(FabricClientCommandSource source, boolean enabled) {
        EmissiveBloomConfigManager.get().blockGlow = enabled;
        EmissiveBloomConfigManager.save();
        feedback(source, "Block glow " + (enabled ? "enabled." : "disabled."));
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowStrength(CommandContext<FabricClientCommandSource> ctx) {
        float value = EmissiveBloomConfigManager.clampBlockGlowStrength(
            FloatArgumentType.getFloat(ctx, "value"));
        EmissiveBloomConfigManager.get().blockGlowStrength = value;
        EmissiveBloomConfigManager.save();
        feedback(ctx.getSource(), "Block glow strength = " + value);
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowUnderShaders(FabricClientCommandSource source, boolean under) {
        EmissiveBloomConfigManager.get().blockGlowDisableWhenShaderPackActive = !under;
        EmissiveBloomConfigManager.save();
        feedback(source, "Block masks under shader packs " + (under ? "on." : "off."));
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowDepthBias(CommandContext<FabricClientCommandSource> ctx) {
        float value = EmissiveBloomConfigManager.clampBlockGlowDepthBias(
            FloatArgumentType.getFloat(ctx, "units"));
        EmissiveBloomConfigManager.get().blockGlowDepthBias = value;
        EmissiveBloomConfigManager.save();
        feedback(ctx.getSource(), "Block glow depth bias = " + value
            + " depth units (vanilla's decal layering uses -10)");
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowDepthSlope(CommandContext<FabricClientCommandSource> ctx) {
        float value = EmissiveBloomConfigManager.clampBlockGlowDepthSlope(
            FloatArgumentType.getFloat(ctx, "factor"));
        EmissiveBloomConfigManager.get().blockGlowDepthSlope = value;
        EmissiveBloomConfigManager.save();
        feedback(ctx.getSource(), "Block glow depth slope = " + value
            + " (0 is off; vanilla's decal layering uses -1)");
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowLift(CommandContext<FabricClientCommandSource> ctx) {
        float value = EmissiveBloomConfigManager.clampBlockGlowLift(
            FloatArgumentType.getFloat(ctx, "blocks"));
        EmissiveBloomConfigManager.get().blockGlowLift = value;
        EmissiveBloomConfigManager.save();
        feedback(ctx.getSource(), "Block glow lift = " + value + " blocks (1/"
            + (value > 0.0f ? Math.round(1.0f / value) : 0) + ")");
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowDebug(FabricClientCommandSource source, boolean debug) {
        EmissiveBloomConfigManager.get().blockGlowDebug = debug;
        EmissiveBloomConfigManager.save();
        feedback(source, "Block mask tint " + (debug ? "on (magenta)." : "off."));
        return SINGLE_SUCCESS;
    }



    private static int traceItemGlow(FabricClientCommandSource source) {
        EmissiveItemTrace.request();
        feedback(source, "Item glow trace armed - the next frame is logged to the client log.");
        return SINGLE_SUCCESS;
    }

    private static int traceBlockGlow(FabricClientCommandSource source) {
        BlockGlowTrace.request();
        feedback(source, "Block mask trace armed - the next frame with a lamp in view is logged.");
        return SINGLE_SUCCESS;
    }



    private static int setBlockGlowMipmap(FabricClientCommandSource source, boolean mipmap) {
        EmissiveBloomConfigManager.get().blockGlowMipmap = mipmap;
        EmissiveBloomConfigManager.save();
        feedback(source, "Block glow mipmapping " + (mipmap ? "on." : "off."));
        return SINGLE_SUCCESS;
    }

    private static int setBlockGlowDistance(CommandContext<FabricClientCommandSource> ctx) {
        int value = EmissiveBloomConfigManager.clampBlockGlowDistance(
            IntegerArgumentType.getInteger(ctx, "blocks"));
        EmissiveBloomConfigManager.get().blockGlowDistance = value;
        EmissiveBloomConfigManager.save();
        feedback(ctx.getSource(), "Block glow distance = " + value + " blocks");
        return SINGLE_SUCCESS;
    }

    private static int setDebugMode(CommandContext<FabricClientCommandSource> ctx) {
        int mode = EmissiveBloomConfigManager.clampDebugMode(IntegerArgumentType.getInteger(ctx, "mode"));
        EmissiveBloomConfigManager.get().debugMode = mode;
        EmissiveBloomConfigManager.save();
        feedback(ctx.getSource(), "Bloom debug " + mode + " (" + DEBUG_MODE_NAMES[mode] + ")");
        return SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx) {
        EmissiveBloomConfigManager.reload();
        EmissiveBloomShaders.retry();
        feedback(ctx.getSource(), "Bloom config reloaded.");
        return SINGLE_SUCCESS;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        boolean suppressed = config.disableWhenShaderPackActive && IrisCompat.isShaderPackActive();

        feedback(ctx.getSource(), "Bloom "
            + (config.enabled ? "on" : "off")
            + (suppressed ? " (suppressed: shader pack active)" : "")
            + " | intensity " + config.intensity
            + " | threshold " + config.glowThreshold
            + " | saturation " + config.glowSaturation
            + " | knee " + config.glowKnee
            + " | core " + config.coreStrength
            + " | radius " + config.blurRadius
            + " | scale " + config.framebufferScale
            + " | blocks " + (config.blockGlow
                ? (config.blockGlowDisableWhenShaderPackActive && IrisCompat.isShaderPackActive()
                    ? "suppressed: shader pack" : "on")
                : "off")
            + " at " + config.blockGlowStrength + "/" + config.blockGlowDistance + "m"
            + " | depth " + config.blockGlowDepthSlope + "/" + config.blockGlowDepthBias
            + " lift " + config.blockGlowLift
            + " | shaders " + (EmissiveBloomShaders.shadersReady() ? "loaded" : "NOT LOADED")
            + " | composite " + (IrisCompat.isShaderPackActive()
                ? "after the shader pack's final pass" : "vanilla, world then hand")
            + (config.debugMode == 0 ? "" : " | DEBUG " + config.debugMode + ": " + DEBUG_MODE_NAMES[config.debugMode]));
        return SINGLE_SUCCESS;
    }

    private static int setUnderShaders(FabricClientCommandSource source, boolean under) {
        EmissiveBloomConfigManager.get().disableWhenShaderPackActive = !under;
        EmissiveBloomConfigManager.save();
        feedback(source, "Halo under shader packs " + (under ? "on." : "off.")
            + (IrisCompat.isShaderPackActive() ? "" : " (No shader pack is active right now.)"));
        return SINGLE_SUCCESS;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        EmissiveBloomConfigManager.get().enabled = enabled;
        EmissiveBloomConfigManager.save();
        feedback(source, "Bloom " + (enabled ? "enabled." : "disabled."));
        return SINGLE_SUCCESS;
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.AQUA));
    }

    @FunctionalInterface
    private interface Clamp {
        float clamp(float value);
    }

    @FunctionalInterface
    private interface Setter {
        void set(EmissiveBloomConfig config, float value);
    }
}

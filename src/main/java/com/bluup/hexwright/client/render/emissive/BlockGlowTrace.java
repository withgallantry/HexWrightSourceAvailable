package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

final class BlockGlowTrace {
    private static long frame;

    private static boolean requested;
    private static boolean armed;
    private static boolean drawnInPass;
    private static boolean reported;

    private static long submitFrame;
    private static final Matrix4f SUBMIT_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f SUBMIT_PROJECTION = new Matrix4f();

    private BlockGlowTrace() {
    }

    static void nextFrame() {
        frame++;
    }

    static boolean pending() {
        return requested && !reported;
    }

    static void request() {
        requested = true;
        reported = false;
    }

    static void onSubmit(MultiBufferSource target, boolean immediate) {
        if (!pending()) {
            return;
        }
        armed = true;
        drawnInPass = false;
        submitFrame = frame;
        SUBMIT_MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
        SUBMIT_PROJECTION.set(RenderSystem.getProjectionMatrix());
        Hexwright.LOGGER.warn("Block glow trace: submitted on frame {} into {} ({} buffer)",
            submitFrame, target.getClass().getName(), immediate ? "private immediate" : "the world's");
    }

    static void onLayerDrawn() {
        if (!armed) {
            return;
        }
        drawnInPass = true;
        Matrix4f drawModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f drawProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        boolean sameModelView = drawModelView.equals(SUBMIT_MODEL_VIEW, 1.0e-6f);
        boolean sameProjection = drawProjection.equals(SUBMIT_PROJECTION, 1.0e-6f);
        Hexwright.LOGGER.warn("Block glow trace: drawn on frame {} (submitted on {}). "
                + "model-view identical: {}. projection identical: {}.",
            frame, submitFrame, sameModelView, sameProjection);
        if (!sameModelView) {
            Hexwright.LOGGER.warn("  model-view at submit: {}\n  model-view at draw:   {}",
                SUBMIT_MODEL_VIEW, drawModelView);
        }
        if (!sameProjection) {
            Hexwright.LOGGER.warn("  projection at submit: {}\n  projection at draw:   {}",
                SUBMIT_PROJECTION, drawProjection);
        }
    }

    static void endPass() {
        if (!armed) {
            return;
        }
        armed = false;
        reported = true;
        if (!drawnInPass) {
            Hexwright.LOGGER.warn("Block glow trace: the batch submitted on frame {} was NOT drawn "
                + "before the pass ended - it was still queued. Whatever matrices are live when it "
                + "finally goes out are not the ones it was built for.", submitFrame);
        }
    }
}

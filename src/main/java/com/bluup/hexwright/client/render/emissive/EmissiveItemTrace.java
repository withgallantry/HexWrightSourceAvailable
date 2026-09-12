package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.server.item.ConfigurableStaffItem;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EmissiveItemTrace {
    private static boolean requested;
    private static boolean armed;

    private static final Set<String> SEEN = new LinkedHashSet<>();

    private static final List<String> LINES = new ArrayList<>();

    private EmissiveItemTrace() {
    }

    public static void request() {
        requested = true;
    }

    static boolean pending() {
        return requested || armed;
    }

    static void nextFrame() {
        if (armed) {
            report();
            armed = false;
            SEEN.clear();
            LINES.clear();
            return;
        }
        if (requested) {
            requested = false;
            armed = true;
            SEEN.clear();
            LINES.clear();
        }
    }

    public static void note(ItemStack stack, ItemDisplayContext context, BakedModel model,
                            boolean handPose) {
        if (!armed) {
            return;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = item + "@" + context;
        if (!SEEN.add(key)) {
            return;
        }

        StringBuilder line = new StringBuilder("  ").append(item).append(" as ").append(context);
        if (stack.getItem() instanceof ConfigurableStaffItem) {
            line.append(" [model part: ").append(StaffParts.modelId(stack)).append(']');
        }

        if (!(model instanceof EmissiveBakedModel emissive)) {
            line.append(" -> NOT WRAPPED: no mask, no glow-named cube, no brightness source. "
                + "Nothing is drawn and nothing was asked to be.");
            LINES.add(line.toString());
            return;
        }

        if (emissive.hasTwin()) {
            BakedModel twin = emissive.glowModel();
            line.append(" -> twin ").append(emissive.glowModelId())
                .append(twin == null ? " FAILED TO BAKE (nothing will draw)" : " baked");
        } else {
            line.append(" -> own quads, by brightness");
        }
        line.append(", strength ").append(emissive.params().strength())
            .append(emissive.thresholded() ? ", thresholded" : ", no thresholds")
            .append(" | halo: ").append(EmissiveBloom.haloDisposition(handPose));
        LINES.add(line.toString());
    }

    private static void report() {
        Hexwright.LOGGER.warn("Item glow trace: {} item(s) reached the emissive hook this frame.",
            LINES.size());
        for (String line : LINES) {
            Hexwright.LOGGER.warn(line);
        }
        if (LINES.isEmpty()) {
            Hexwright.LOGGER.warn("  Nothing at all - the hook did not fire. Either no item was "
                + "drawn, or something else is rendering them.");
        }
    }
}

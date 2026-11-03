package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.client.render.RenderLib;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.server.hexweave.EmbeddedIota;
import com.bluup.hexwright.server.hexweave.PlacedPattern;
import com.bluup.hexwright.server.hexweave.ThreadDraft;
import com.bluup.hexwright.server.hexweave.ThreadEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
final class EmbedKnot {

    private static final List<Vec2> TREFOIL = trefoil(60);
    private static final int MAX_VALUE_WIDTH = 320;

    private EmbedKnot() {
    }

    static int colour(EmbeddedIota embedded) {
        return IotaType.getColor(embedded.iota()) & 0xFFFFFF;
    }

    static void draw(Matrix4f mat, Vec2 centre, float radius, int colour, int alpha) {
        List<Vec2> points = new ArrayList<>(TREFOIL.size());
        for (Vec2 point : TREFOIL) {
            points.add(new Vec2(centre.x + point.x * radius, centre.y + point.y * radius));
        }
        int outer = alpha << 24 | colour & 0xFFFFFF;
        int inner = RenderLib.screenCol(outer);
        PatternDrawBatch.drawLineSeq(mat, points, radius * 0.3f, 0f, outer, outer);
        PatternDrawBatch.drawLineSeq(mat, points, radius * 0.11f, 1f, inner, inner);
    }

    static int embedded(ThreadDraft thread) {
        int count = 0;
        for (ThreadEntry entry : thread.entries()) {
            if (entry instanceof EmbeddedIota) {
                count++;
            }
        }
        return count;
    }

    static List<Component> describe(ThreadEntry entry, int index) {
        if (entry instanceof PlacedPattern placed) {
            Component name = LoomPatternNames.name(placed.pattern());
            return List.of(Component.translatable("gui.hexwright.hex_loom.pattern", index + 1,
                name != null ? name : Component.translatable("gui.hexwright.hex_loom.unnamed")));
        }
        CompoundTag iota = ((EmbeddedIota) entry).iota();
        return List.of(Component.translatable("gui.hexwright.hex_loom.embedded", index + 1, typeName(iota)),
            value(iota));
    }

    static Component name(ThreadEntry entry) {
        if (entry instanceof PlacedPattern placed) {
            Component name = LoomPatternNames.name(placed.pattern());
            return name != null ? name : Component.translatable("gui.hexwright.hex_loom.unnamed");
        }
        return Component.translatable("gui.hexwright.hex_loom.embedded_kind", typeName(((EmbeddedIota) entry).iota()));
    }

    private static Component typeName(CompoundTag iota) {
        IotaType<?> type = IotaType.getTypeFromTag(iota);
        return type != null ? type.typeName() : Component.translatable("gui.hexwright.hex_loom.unknown_iota");
    }

    private static Component value(CompoundTag iota) {
        Component display = IotaType.getDisplay(iota);
        Font font = Minecraft.getInstance().font;
        if (font.width(display) <= MAX_VALUE_WIDTH) {
            return display;
        }
        return Component.literal(font.plainSubstrByWidth(display.getString(), MAX_VALUE_WIDTH) + "...")
            .withStyle(display.getStyle());
    }

    private static List<Vec2> trefoil(int steps) {
        List<Vec2> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            float t = Mth.TWO_PI * i / steps;
            points.add(new Vec2((Mth.sin(t) + 2 * Mth.sin(2 * t)) / 3f, (Mth.cos(t) - 2 * Mth.cos(2 * t)) / 3f));
        }
        return List.copyOf(points);
    }
}

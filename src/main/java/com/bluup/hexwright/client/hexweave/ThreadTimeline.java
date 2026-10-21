package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.render.RenderLib;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.server.hexweave.EmbeddedIota;
import com.bluup.hexwright.server.hexweave.PlacedPattern;
import com.bluup.hexwright.server.hexweave.ThreadEntry;
import com.bluup.hexwright.server.hexweave.ThreadDraft;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
final class ThreadTimeline {

    private static final int PAD = 4;
    private static final int GAP = 6;
    private static final float BOX = 64f;
    private static final float MAX_SPACING = 20f;
    private static final float KNOT_RADIUS = BOX * 0.3f;

    private int left;
    private int top;
    private int right;
    private int bottom;
    private int cell;
    private int pitch;
    private float scroll;
    private @Nullable ThreadCanvas shown;
    private int followedPoint = -1;
    private int followedEdits = -1;
    private int glyphEdits = -1;
    private List<Glyph> glyphs = List.of();

    record Look(@Nullable IGuiTexture background, @Nullable IGuiTexture cell, @Nullable IGuiTexture hoveredCell,
                @Nullable IGuiTexture selectedCell) {
    }

    private record Glyph(List<Vec2> points, Set<Integer> dups, @Nullable Integer knot) {
    }

    void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        cell = Math.max(1, bottom - top - 2 * PAD);
        pitch = cell + GAP;
        clampScroll();
    }

    boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }


    private float gapX(int index) {
        return left + PAD + GAP / 2f - scroll + index * pitch;
    }

    private float cellLeft(int index) {
        return gapX(index) + GAP / 2f;
    }

    private int nearestGap(double x, int size) {
        return Mth.clamp(Math.round((float) (x - gapX(0)) / pitch), 0, size);
    }

    private @Nullable Integer cellAt(double x, double y, int size) {
        int index = Mth.floor((x - cellLeft(0)) / pitch);
        if (index < 0 || index >= size || y < top + PAD || y >= top + PAD + cell) {
            return null;
        }
        float from = cellLeft(index);
        return x >= from && x < from + cell ? index : null;
    }

    private float view() {
        return right - left - 2 * PAD;
    }

    private float maxScroll(int size) {
        return Math.max(0f, size * pitch + GAP - view());
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0f, maxScroll(shown == null ? 0 : shown.draft().size()));
    }

    private void follow(ThreadCanvas canvas, ThreadDraft draft, int point) {
        if (canvas != shown) {
            shown = canvas;
            scroll = 0f;
            followedPoint = -1;
            glyphEdits = -1;
        }
        if (point == followedPoint && draft.edits() == followedEdits) {
            return;
        }
        followedPoint = point;
        followedEdits = draft.edits();
        float at = point * pitch;
        scroll = Mth.clamp(scroll, at + pitch - view(), at - pitch);
        clampScroll();
    }


    boolean mouseClicked(ThreadCanvas canvas, double x, double y, int button) {
        if (!contains(x, y)) {
            return false;
        }
        if (button == 0) {
            int size = canvas.draft().size();
            Integer index = cellAt(x, y, size);
            canvas.select(index);
            if (index == null) {
                canvas.setInsertionPoint(nearestGap(x, size));
            }
        }
        return true;
    }

    boolean mouseScrolled(double x, double y, double delta) {
        if (!contains(x, y)) {
            return false;
        }
        scroll -= (float) delta * pitch;
        clampScroll();
        return true;
    }

    @Nullable List<Component> hover(ThreadCanvas canvas, int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return null;
        }
        Integer index = cellAt(mouseX, mouseY, canvas.draft().size());
        return index != null ? EmbedKnot.describe(canvas.draft().entries().get(index), index)
            : List.of(Component.translatable("gui.hexwright.hex_loom.timeline_insert_here"));
    }


    void render(GuiGraphics graphics, ThreadCanvas canvas, int mouseX, int mouseY, LoomPalette palette, Look look) {
        ThreadDraft draft = canvas.draft();
        int point = canvas.insertionPoint();
        follow(canvas, draft, point);
        List<Glyph> glyphs = glyphs(draft);
        Integer selected = canvas.selected();
        Integer hovered = contains(mouseX, mouseY) ? cellAt(mouseX, mouseY, glyphs.size()) : null;
        List<Integer> outside = draft.outOfBounds();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 40);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        LoomMenu.draw(look.background(), graphics, mouseX, mouseY, left, top, right - left, bottom - top);
        graphics.enableScissor(left + PAD, top, right - PAD, bottom);

        int first = Math.max(0, Mth.floor((left - cellLeft(0)) / pitch));
        int last = Math.min(glyphs.size() - 1, Mth.floor((right - cellLeft(0)) / pitch));
        for (int i = first; i <= last; i++) {
            int x = Math.round(cellLeft(i));
            boolean isSelected = selected != null && selected == i;
            boolean isHovered = hovered != null && hovered == i;
            IGuiTexture face = isSelected && look.selectedCell() != null ? look.selectedCell()
                : isHovered && look.hoveredCell() != null ? look.hoveredCell()
                : look.cell();
            LoomMenu.draw(face, graphics, mouseX, mouseY, x, top + PAD, cell, cell);
            if (isSelected && look.selectedCell() == null) {
                graphics.renderOutline(x, top + PAD, cell, cell, tint(palette.selected(), 0xFF));
            }
        }

        pose.translate(0, 0, 1);
        float scale = cell / BOX;
        for (int i = first; i <= last; i++) {
            ResolvedPatternType type = outside.contains(i) ? ResolvedPatternType.ERRORED : ResolvedPatternType.EVALUATED;
            boolean isSelected = selected != null && selected == i;
            int start = isSelected ? palette.selected() : type.getColor();
            int end = isSelected ? palette.selectedFade() : type.getFadeColor();
            Matrix4f mat = new Matrix4f(pose.last().pose())
                .translate(cellLeft(i) + cell / 2f, top + PAD + cell / 2f, 0).scale(scale, scale, 1f);
            Glyph glyph = glyphs.get(i);
            if (glyph.knot() != null) {
                int colour = isSelected ? palette.selected() : outside.contains(i) ? type.getColor() : glyph.knot();
                EmbedKnot.draw(mat, Vec2.ZERO, KNOT_RADIUS, colour, 0xE6);
                continue;
            }
            PatternDrawBatch.drawPattern(mat, glyph.points(), glyph.dups(), true, tint(start, 0xE6), tint(end, 0xE6),
                outside.contains(i) ? 0.9f : 0.2f, RenderLib.DEFAULT_READABILITY_OFFSET, 1f, i);
        }
        PatternDrawBatch.flush();
        RenderSystem.enableCull();

        pose.translate(0, 0, 2);
        if (glyphs.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.translatable("gui.hexwright.hex_loom.timeline_empty"),
                Math.round(gapX(0)) + GAP, (top + bottom - font.lineHeight) / 2 + 1, tint(palette.label(), 0xA0));
        }
        drawInsertionLine(graphics, Math.round(gapX(point)), palette.insert());
        graphics.disableScissor();
        pose.popPose();
    }

    private void drawInsertionLine(GuiGraphics graphics, int x, int colour) {
        float pulse = 0.5f + 0.5f * Mth.sin(Util.getMillis() / 220f);
        int tinted = tint(colour, 0xB0 + (int) (pulse * 0x4F));
        graphics.fill(x - 1, top + 2, x + 1, bottom - 2, tinted);
        graphics.fill(x - 3, top + 2, x + 3, top + 3, tinted);
        graphics.fill(x - 3, bottom - 3, x + 3, bottom - 2, tinted);
    }

    private List<Glyph> glyphs(ThreadDraft draft) {
        if (draft.edits() == glyphEdits) {
            return glyphs;
        }
        List<Glyph> built = new ArrayList<>(draft.size());
        for (ThreadEntry entry : draft.entries()) {
            if (entry instanceof EmbeddedIota embedded) {
                built.add(new Glyph(List.of(), Set.of(), EmbedKnot.colour(embedded)));
                continue;
            }
            HexPattern pattern = ((PlacedPattern) entry).pattern();
            built.add(new Glyph(RenderLib.getCenteredPattern(pattern, BOX, BOX, MAX_SPACING).getSecond(),
                RenderLib.findDupIndices(pattern.positions()), null));
        }
        glyphs = built;
        glyphEdits = draft.edits();
        return glyphs;
    }

    private static int tint(int colour, int alpha) {
        return alpha << 24 | colour & 0xFFFFFF;
    }
}

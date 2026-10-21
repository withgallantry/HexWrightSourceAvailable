package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.client.render.RenderLib;
import at.petrak.hexcasting.common.lib.HexSounds;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.client.spellcasting.PatternGeometryCache;
import com.bluup.hexwright.server.hexweave.EmbeddedIota;
import com.bluup.hexwright.server.hexweave.PlacedPattern;
import com.bluup.hexwright.server.hexweave.ThreadEntry;
import com.bluup.hexwright.server.hexweave.ThreadDraft;
import com.bluup.hexwright.server.hexweave.WeaveDraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class ThreadCanvas {

    private static final float REF = 24f;
    private static final float SQRT_3 = (float) Math.sqrt(3);
    private static final float[] ZOOMS = {1f, 1.5f, 2f, 3f, 4f};
    private static final int CURSOR_GLOW = 3;
    private static final float KNOT_RADIUS = REF * 0.42f;
    private static final List<HexCoord> GRID = gridPoints();

    private final WeaveDraft weave;
    private final ThreadDraft draft;
    private final Consumer<Component> status;
    private final RandomSource random = SoundInstance.createUnseededRandom();
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int zoom;
    private float panX;
    private float panY;
    private boolean panning;
    private @Nullable HexCoord started;
    private @Nullable Stroke stroke;
    private @Nullable Integer selected;
    private @Nullable Integer insertAt;
    private final Deque<ThreadEntry> carried = new ArrayDeque<>();
    private int carriedTotal;
    private int turn;

    private static final class Stroke {
        final HexCoord start;
        HexCoord current;
        final HexPattern pattern;

        Stroke(HexCoord start, HexCoord current, HexPattern pattern) {
            this.start = start;
            this.current = current;
            this.pattern = pattern;
        }
    }

    public ThreadCanvas(WeaveDraft weave, ThreadDraft draft, Consumer<Component> status) {
        this.weave = weave;
        this.draft = draft;
        this.status = status;
    }

    public ThreadDraft draft() {
        return draft;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        clampPan();
    }

    public boolean drawing() {
        return started != null || stroke != null;
    }

    public void cancelStroke() {
        started = null;
        stroke = null;
    }

    public @Nullable Integer selected() {
        return selected;
    }

    public void select(@Nullable Integer index) {
        selected = index;
    }

    public void clearSelection() {
        selected = null;
    }

    public int insertionPoint() {
        int size = draft.size();
        return insertAt == null ? size : Math.min(insertAt, size);
    }

    public void setInsertionPoint(int index) {
        insertAt = index >= draft.size() ? null : Math.max(0, index);
    }

    @Nullable Integer heldInsertionPoint() {
        return insertAt;
    }

    void restored(@Nullable Integer insertionPoint) {
        insertAt = insertionPoint;
        selected = null;
        cancelStroke();
        carried.clear();
    }

    boolean insertRun(List<ThreadEntry> run) {
        int at = insertionPoint();
        if (!draft.insertAll(at, run)) {
            return false;
        }
        if (insertAt != null) {
            insertAt = at + run.size();
        }
        return true;
    }

    private boolean insertAtPoint(ThreadEntry entry) {
        int at = insertionPoint();
        if (!draft.insert(at, entry)) {
            return false;
        }
        if (insertAt != null) {
            insertAt = at + 1;
        }
        return true;
    }


    void carry(List<ThreadEntry> entries) {
        carried.clear();
        carried.addAll(entries);
        carriedTotal = entries.size();
        turn = 0;
        selected = null;
        cancelStroke();
    }

    boolean carrying() {
        return !carried.isEmpty();
    }

    int stopCarrying() {
        int left = carried.size();
        carried.clear();
        return left;
    }

    void turnCarried() {
        turn = (turn + 1) % HexAngle.values().length;
    }

    @Nullable ThreadEntry carriedNext() {
        return carried.peekFirst();
    }

    int carriedIndex() {
        return carriedTotal - carried.size() + 1;
    }

    int carriedTotal() {
        return carriedTotal;
    }

    private @Nullable ThreadEntry carriedAt(double x, double y) {
        ThreadEntry next = carried.peekFirst();
        if (next == null) {
            return null;
        }
        HexCoord cursor = toGrid(x, y);
        if (!(next instanceof PlacedPattern placed)) {
            return next.at(cursor);
        }
        HexPattern pattern = placed.pattern();
        HexPattern turned = new HexPattern(pattern.getStartDir().rotatedBy(HexAngle.values()[turn]),
            new ArrayList<>(pattern.getAngles()));
        List<HexCoord> points = turned.positions();
        int q = 0;
        int r = 0;
        for (HexCoord point : points) {
            q += point.getQ();
            r += point.getR();
        }
        HexCoord centre = new HexCoord(Math.round((float) q / points.size()), Math.round((float) r / points.size()));
        return new PlacedPattern(turned, cursor.minus(centre));
    }

    private void placeCarried(double x, double y) {
        ThreadEntry candidate = carriedAt(x, y);
        if (candidate == null) {
            return;
        }
        if (!insertAtPoint(candidate)) {
            status.accept(Component.translatable("gui.hexwright.hex_loom.carry_blocked"));
            return;
        }
        carried.pollFirst();
        playStrokeSound(HexSounds.ADD_TO_PATTERN, 1f);
        if (carried.isEmpty()) {
            status.accept(Component.translatable("gui.hexwright.hex_loom.carry_done", carriedTotal));
        }
    }

    public void removeSelected() {
        if (selected != null) {
            remove(selected);
        }
    }

    public void clear() {
        draft.clear();
        selected = null;
        insertAt = null;
    }

    private void remove(int index) {
        draft.remove(index);
        if (insertAt != null && index < insertAt) {
            insertAt--;
        }
        selected = null;
    }


    private float fitSpacing() {
        float across = 2 * ThreadFrame.HALF_WIDTH + 2;
        float down = 2 * ThreadFrame.HALF_HEIGHT + 2;
        return Math.max(1f, Math.min((right - left) / across, (bottom - top) / down));
    }

    public float spacing() {
        return fitSpacing() * ZOOMS[zoom];
    }

    public boolean atWidestZoom() {
        return zoom == 0;
    }

    public boolean canZoomIn() {
        return zoom < ZOOMS.length - 1;
    }

    public void zoomAboutCentre(int direction) {
        mouseScrolled((left + right) / 2.0, (top + bottom) / 2.0, direction);
    }

    public void resetView() {
        zoom = 0;
        panX = 0;
        panY = 0;
    }

    public Vec2 armatureAt(int side) {
        Vec2 centre = toScreen(HexCoord.getOrigin());
        return new Vec2(centre.x + side * ThreadFrame.ARMATURE * spacing(), centre.y);
    }

    public Vec2 frameTip(int side) {
        Vec2 centre = toScreen(HexCoord.getOrigin());
        return new Vec2(centre.x + side * ThreadFrame.HALF_WIDTH * spacing(), centre.y);
    }

    private Vec2 centre() {
        return new Vec2((left + right) / 2f + panX, (top + bottom) / 2f + panY);
    }

    public Vec2 toScreen(HexCoord point) {
        return HexUtils.coordToPx(point, spacing(), centre());
    }

    public HexCoord toGrid(double x, double y) {
        return HexUtils.pxToCoord(new Vec2((float) x, (float) y), spacing(), centre());
    }

    public boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private void clampPan() {
        float spacing = spacing();
        float reachX = SQRT_3 * ThreadDraft.RADIUS * spacing;
        float reachY = 1.5f * ThreadDraft.RADIUS * spacing;
        panX = Mth.clamp(panX, -reachX, reachX);
        panY = Mth.clamp(panY, -reachY, reachY);
    }


    public boolean mouseClicked(double x, double y, int button) {
        if (!contains(x, y)) {
            return false;
        }
        if (button == 0 && carrying()) {
            placeCarried(x, y);
            return true;
        }
        if (button == 0) {
            HexCoord at = toGrid(x, y);
            Integer owner = draft.entryAt(at);
            if (owner != null) {
                selected = owner;
                return true;
            }
            selected = null;
            if (ThreadDraft.inBounds(at)) {
                started = at;
                stroke = null;
                playStrokeSound(HexSounds.START_PATTERN, 1f);
            }
            return true;
        }
        if (button == 1 || button == 2) {
            panning = true;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button == 0 && drawing()) {
            extend(x, y);
            return true;
        }
        if (panning && (button == 1 || button == 2)) {
            panX += (float) dragX;
            panY += (float) dragY;
            clampPan();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && drawing()) {
            finish();
            return true;
        }
        if (panning && (button == 1 || button == 2)) {
            panning = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (!contains(x, y) || delta == 0) {
            return false;
        }
        int next = Mth.clamp(zoom + (delta > 0 ? 1 : -1), 0, ZOOMS.length - 1);
        if (next == zoom) {
            return true;
        }
        Vec2 centre = centre();
        float before = spacing();
        float unitsX = (float) (x - centre.x) / before;
        float unitsY = (float) (y - centre.y) / before;
        zoom = next;
        float after = spacing();
        panX = (float) x - unitsX * after - (left + right) / 2f;
        panY = (float) y - unitsY * after - (top + bottom) / 2f;
        if (zoom == 0) {
            panX = 0;
            panY = 0;
        }
        clampPan();
        return true;
    }

    private void extend(double x, double y) {
        HexCoord anchor = stroke != null ? stroke.current : started;
        if (anchor == null) {
            return;
        }
        Vec2 anchorPx = toScreen(anchor);
        float dx = (float) x - anchorPx.x;
        float dy = (float) y - anchorPx.y;
        float spacing = spacing();
        double snap = spacing * spacing * 2.0 * Mth.clamp(HexConfig.client().gridSnapThreshold(), 0.5, 1.0);
        if (dx * dx + dy * dy < snap) {
            return;
        }
        float turns = Mth.positiveModulo((float) Math.atan2(dy, dx) / Mth.TWO_PI, 6f);
        HexDir dir = HexDir.values()[Math.floorMod(Math.round(turns * 6) + 1, 6)];
        HexCoord next = anchor.plus(dir);
        if (draft.occupied(next) || !ThreadDraft.inBounds(next)) {
            return;
        }

        if (stroke == null) {
            stroke = new Stroke(started, next, new HexPattern(dir, new ArrayList<>()));
            started = null;
            playStrokeSound(HexSounds.ADD_TO_PATTERN, 1f + (random.nextFloat() - 0.5f) * 0.1f);
            return;
        }
        List<HexAngle> angles = stroke.pattern.getAngles();
        if (dir == stroke.pattern.finalDir().rotatedBy(HexAngle.BACK)) {
            if (angles.isEmpty()) {
                started = stroke.start;
                stroke = null;
            } else {
                stroke.current = next;
                angles.remove(angles.size() - 1);
            }
        } else if (stroke.pattern.tryAppendDir(dir)) {
            stroke.current = next;
        } else {
            return;
        }
        playStrokeSound(HexSounds.ADD_TO_PATTERN, 1f + (random.nextFloat() - 0.5f) * 0.1f);
    }

    private void playStrokeSound(SoundEvent sound, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, 0.25f,
            pitch, random, player.getX(), player.getY(), player.getZ()));
    }

    private void finish() {
        if (stroke != null && !insertAtPoint(new PlacedPattern(stroke.pattern, stroke.start))) {
            status.accept(Component.translatable("gui.hexwright.hex_loom.refused_stroke"));
        }
        cancelStroke();
    }


    public void render(GuiGraphics graphics, int mouseX, int mouseY, LoomPalette palette) {
        float spacing = spacing();
        float scale = spacing / REF;
        Vec2 centre = centre();
        HexCoord cursor = contains(mouseX, mouseY) ? toGrid(mouseX, mouseY) : null;

        graphics.enableScissor(left, top, right, bottom);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centre.x, centre.y, 0);
        pose.scale(scale, scale, 1f);
        Matrix4f mat = pose.last().pose();
        RenderSystem.disableDepthTest();

        ThreadFrame.draw(graphics, 0, 0, REF, spacing, 1f);
        drawGrid(mat, cursor, palette);
        drawArmature(mat, -1, entered(), palette.eye());
        drawArmature(mat, 1, leaves(), palette.tail());
        drawEntries(mat, palette);
        drawStroke(mat, (mouseX - centre.x) / scale, (mouseY - centre.y) / scale);
        if (cursor != null) {
            drawCarried(mat, mouseX, mouseY);
        }

        PatternDrawBatch.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        pose.popPose();

        drawLabels(graphics, spacing, palette);
        graphics.disableScissor();
    }

    public @Nullable List<Component> hover(int mouseX, int mouseY) {
        if (drawing() || carrying() || !contains(mouseX, mouseY)) {
            return null;
        }
        Integer index = draft.entryAt(toGrid(mouseX, mouseY));
        return index == null ? null : EmbedKnot.describe(draft.entries().get(index), index);
    }

    private void drawGrid(Matrix4f mat, @Nullable HexCoord cursor, LoomPalette palette) {
        for (HexCoord point : GRID) {
            if (draft.occupied(point)) {
                continue;
            }
            boolean rim = point.distanceTo(HexCoord.getOrigin()) == ThreadDraft.RADIUS;
            int near = cursor == null ? Integer.MAX_VALUE : point.distanceTo(cursor);
            float glow = near <= CURSOR_GLOW ? 1f - near / (CURSOR_GLOW + 1f) : 0f;
            float radius = (rim ? 1.7f : 1.1f) + glow * 1.4f;
            int alpha = (int) (255 * Math.min(1f, (rim ? 0.55f : 0.28f) + glow * 0.6f));
            int colour = rim ? palette.rim() : palette.grid();
            PatternDrawBatch.drawRoundSpot(mat, grid(point), radius, FastColor.ARGB32.color(alpha,
                FastColor.ARGB32.red(colour), FastColor.ARGB32.green(colour), FastColor.ARGB32.blue(colour)));
        }
    }

    private void drawArmature(Matrix4f mat, int side, boolean on, int colour) {
        Vec2 at = new Vec2(side * ThreadFrame.ARMATURE * REF, 0);
        int alpha = on ? 0xF0 : 0x50;
        PatternDrawBatch.drawRoundSpot(mat, at, on ? 5.5f : 3.5f, FastColor.ARGB32.color(alpha,
            FastColor.ARGB32.red(colour), FastColor.ARGB32.green(colour), FastColor.ARGB32.blue(colour)));
        if (on) {
            PatternDrawBatch.drawRoundSpot(mat, at, 2.5f, 0xFFFFFFFF);
        }
    }

    private void drawEntries(Matrix4f mat, LoomPalette palette) {
        List<ThreadEntry> entries = draft.entries();
        List<Integer> outside = draft.outOfBounds();
        for (int i = 0; i < entries.size(); i++) {
            boolean isSelected = selected != null && selected == i;
            if (entries.get(i) instanceof EmbeddedIota embedded) {
                int colour = isSelected ? palette.selected() : outside.contains(i)
                    ? ResolvedPatternType.ERRORED.getColor() : EmbedKnot.colour(embedded);
                EmbedKnot.draw(mat, grid(embedded.origin()), KNOT_RADIUS, colour, 0xE6);
                continue;
            }
            PlacedPattern placed = (PlacedPattern) entries.get(i);
            int start;
            int end;
            float irregular = 0.2f;
            if (isSelected) {
                start = palette.selected();
                end = palette.selectedFade();
            } else if (outside.contains(i)) {
                start = ResolvedPatternType.ERRORED.getColor();
                end = ResolvedPatternType.ERRORED.getFadeColor();
                irregular = 0.9f;
            } else {
                start = ResolvedPatternType.EVALUATED.getColor();
                end = ResolvedPatternType.EVALUATED.getFadeColor();
            }
            List<Vec2> points = PatternGeometryCache.toLines(placed.pattern(), REF, grid(placed.origin()));
            Set<Integer> dups = RenderLib.findDupIndices(placed.pattern().positions());
            PatternDrawBatch.drawPattern(mat, points, dups, true, start | 0xC8000000, end | 0xC8000000,
                irregular, RenderLib.DEFAULT_READABILITY_OFFSET, 1f, i);
        }
    }

    private void drawStroke(Matrix4f mat, float cursorX, float cursorY) {
        List<Vec2> points = new ArrayList<>();
        Set<Integer> dups = Set.of();
        if (stroke != null) {
            List<HexCoord> positions = stroke.pattern.positions(stroke.start);
            for (HexCoord point : positions) {
                points.add(grid(point));
            }
            dups = RenderLib.findDupIndices(positions);
        } else if (started != null) {
            points.add(grid(started));
        } else {
            return;
        }
        points.add(new Vec2(cursorX, cursorY));
        PatternDrawBatch.drawPattern(mat, points, dups, false, 0xFF64C8FF, 0xFFFECBE6, 0.1f,
            RenderLib.DEFAULT_READABILITY_OFFSET, 1f, draft.size());
    }

    private void drawCarried(Matrix4f mat, int mouseX, int mouseY) {
        ThreadEntry candidate = carriedAt(mouseX, mouseY);
        if (candidate == null) {
            return;
        }
        boolean fits = draft.accepts(candidate);
        if (candidate instanceof EmbeddedIota embedded) {
            EmbedKnot.draw(mat, grid(embedded.origin()), KNOT_RADIUS,
                fits ? EmbedKnot.colour(embedded) : ResolvedPatternType.ERRORED.getColor(), 0xB0);
            return;
        }
        List<HexCoord> positions = candidate.points();
        List<Vec2> points = new ArrayList<>(positions.size());
        for (HexCoord point : positions) {
            points.add(grid(point));
        }
        int start = fits ? 0x64C8FF : ResolvedPatternType.ERRORED.getColor();
        int end = fits ? 0xFECBE6 : ResolvedPatternType.ERRORED.getFadeColor();
        PatternDrawBatch.drawPattern(mat, points, RenderLib.findDupIndices(positions), true,
            (start & 0xFFFFFF) | 0xB0000000, (end & 0xFFFFFF) | 0xB0000000, fits ? 0.1f : 0.9f,
            RenderLib.DEFAULT_READABILITY_OFFSET, 1f, draft.size());
    }

    private void drawLabels(GuiGraphics graphics, float spacing, LoomPalette palette) {
        Font font = Minecraft.getInstance().font;
        if (spacing >= 9f) {
            List<ThreadEntry> entries = draft.entries();
            for (int i = 0; i < entries.size(); i++) {
                Vec2 at = toScreen(entries.get(i).origin());
                graphics.drawString(font, String.valueOf(i + 1), (int) at.x + 3, (int) at.y - 10,
                    0xFF000000 | palette.number());
            }
        }
        drawArmatureLabel(graphics, font, -1, "gui.hexwright.hex_loom.eye_mark", entered(), palette.eye());
        drawArmatureLabel(graphics, font, 1, "gui.hexwright.hex_loom.tail_mark", leaves(), palette.tail());
    }

    private boolean entered() {
        return weave.entered(draft.id());
    }

    private boolean leaves() {
        return !weave.linksFrom(draft.id()).isEmpty();
    }

    private void drawArmatureLabel(GuiGraphics graphics, Font font, int side, String key, boolean on, int colour) {
        Vec2 tip = frameTip(side);
        Component text = Component.translatable(key);
        int x = side < 0 ? (int) tip.x - 4 - font.width(text) : (int) tip.x + 4;
        graphics.drawString(font, text, x, (int) tip.y - 9, (on ? 0xFF000000 : 0x70000000) | colour);
    }

    private static Vec2 grid(HexCoord point) {
        return HexUtils.coordToPx(point, REF, Vec2.ZERO);
    }

    private static List<HexCoord> gridPoints() {
        List<HexCoord> points = new ArrayList<>();
        int radius = ThreadDraft.RADIUS;
        for (int q = -radius; q <= radius; q++) {
            for (int r = Math.max(-radius, -q - radius); r <= Math.min(radius, -q + radius); r++) {
                points.add(new HexCoord(q, r));
            }
        }
        return List.copyOf(points);
    }
}

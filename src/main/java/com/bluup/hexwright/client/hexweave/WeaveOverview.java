package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.client.render.RenderLib;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.server.hexweave.Hexweave;
import com.bluup.hexwright.server.hexweave.EmbeddedIota;
import com.bluup.hexwright.server.hexweave.PlacedPattern;
import com.bluup.hexwright.server.hexweave.ThreadEntry;
import com.bluup.hexwright.server.hexweave.ThreadDraft;
import com.bluup.hexwright.server.hexweave.TriggerLink;
import com.bluup.hexwright.server.hexweave.WeaveDraft;
import com.bluup.hexwright.server.hexweave.WeaveLayout;
import com.bluup.hexwright.server.hexweave.WeaveLink;
import com.bluup.hexwright.server.hexweave.WeaveTrigger;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class WeaveOverview {

    private static final float SQRT_3 = Mth.sqrt(3);
    private static final float HALF_WIDTH = ThreadFrame.HALF_WIDTH;
    private static final float HALF_HEIGHT = ThreadFrame.HALF_HEIGHT;
    private static final float ARMATURE = ThreadFrame.ARMATURE;
    private static final float LEAD = 4f;
    private static final float OUTLINE_REACH = HALF_WIDTH + 3f;
    private static final float TRIGGER_RADIUS = 7f;
    private static final float TRIGGER_LEAD = TRIGGER_RADIUS + 3f;
    private static final float TRIGGER_RING = TRIGGER_RADIUS + 3f;
    private static final LoomArt SEAL_PLANE = LoomArt.of("trigger_plane", 58, 50);
    private static final LoomArt SEAL = LoomArt.of("trigger", 58, 50);
    private static final LoomArt SEAL_SELECTED = LoomArt.of("trigger_selected", 80, 70);

    private static final float MIN_SCALE = 0.8f;
    private static final float MAX_SCALE = 7f;
    private static final float FIT_MIN = 1.6f;
    private static final float FIT_MAX = 4.5f;
    private static final float ZOOM_STEP = 1.25f;
    private static final float ARMATURE_HIT = 8f;
    private static final float LINK_HIT = 4f;
    private static final float LINK_PIECE = 24f;
    private static final double DRAG_START = 4;
    private static final long DOUBLE_CLICK_MS = 350;
    private static final int PICKER_COLUMN = 170;
    private static final int PICKER_PAD = 4;
    private static final int PICKER_INSET = 4;
    private static final int PICKER_NAME = 12;
    private static final int REACH_BUTTON = 18;

    private static final int PLANE = 0x322B39;

    private final WeaveDraft weave;
    private final Consumer<Component> status;
    private final IntConsumer open;
    private final Map<ThreadDraft, Miniature> miniatures = new HashMap<>();
    private int left;
    private int top;
    private int right;
    private int bottom;
    private float scale = 3f;
    private float camX;
    private float camY;
    private boolean fitted;
    private @Nullable Node selected;
    private @Nullable WeaveLink selectedLink;
    private @Nullable TriggerLink selectedTriggerLink;
    private @Nullable Node linkingFrom;
    private @Nullable Node moving;
    private @Nullable Integer picking;
    private @Nullable Integer reaching;
    private @Nullable Integer capturing;
    private WeaveTrigger.@Nullable Kind capturingKind;
    private boolean moved;
    private boolean panning;
    private double pressX;
    private double pressY;
    private float grabX;
    private float grabY;
    private @Nullable Node lastClicked;
    private long lastClick;
    private int reachEdits = -1;
    private Set<Integer> reached = Set.of();

    private record Node(boolean trigger, int id) {

        static Node thread(int id) {
            return new Node(false, id);
        }

        static Node trigger(int id) {
            return new Node(true, id);
        }
    }

    private record Miniature(int edits, List<List<Vec2>> strokes, List<Knot> knots, Set<Integer> outside) {
    }

    private record Knot(Vec2 at, int colour) {
    }

    public WeaveOverview(WeaveDraft weave, Consumer<Component> status, IntConsumer open) {
        this.weave = weave;
        this.status = status;
        this.open = open;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        if (!fitted) {
            fitAll();
            fitted = true;
        }
    }

    public float scale() {
        return scale;
    }

    public boolean atClosest() {
        return scale >= MAX_SCALE;
    }

    public boolean atWidest() {
        return scale <= MIN_SCALE;
    }

    public void zoomAboutCentre(int direction) {
        zoomAbout(centreX(), centreY(), direction);
    }

    public @Nullable Integer selectedThread() {
        return selected != null && !selected.trigger() ? selected.id() : null;
    }

    public @Nullable Integer selectedTrigger() {
        return selected != null && selected.trigger() ? selected.id() : null;
    }

    public @Nullable WeaveLink selectedLink() {
        return selectedLink;
    }

    public @Nullable TriggerLink selectedTriggerLink() {
        return selectedTriggerLink;
    }

    public void select(int thread) {
        selectNode(Node.thread(thread));
    }

    public void selectTrigger(int trigger) {
        selectNode(Node.trigger(trigger));
    }

    private void selectNode(Node node) {
        selected = node;
        selectedLink = null;
        selectedTriggerLink = null;
    }

    public void clearSelection() {
        selected = null;
        selectedLink = null;
        selectedTriggerLink = null;
    }

    public boolean busy() {
        return linkingFrom != null || moving != null || picking != null || reaching != null || capturing != null;
    }

    public void cancel() {
        linkingFrom = null;
        moving = null;
        picking = null;
        reaching = null;
        capturing = null;
        capturingKind = null;
    }

    public boolean capturing() {
        return capturing != null;
    }

    public void capture(InputConstants.Key key) {
        if (capturing == null || capturingKind == null || key == InputConstants.UNKNOWN
            || key.getName().length() > WeaveTrigger.MAX_KEY_LENGTH) {
            return;
        }
        int trigger = capturing;
        weave.setKind(trigger, capturingKind, key.getName());
        cancel();
        status.accept(Component.translatable("gui.hexwright.hex_loom.trigger_set", trigger, triggerKind(weave, trigger)));
    }

    public boolean choosingReach() {
        return reaching != null;
    }

    public void chooseReach(int reach) {
        if (reaching == null || reach < WeaveTrigger.MIN_REACH || reach > WeaveTrigger.MAX_REACH) {
            return;
        }
        int trigger = reaching;
        weave.setReach(trigger, reach);
        reaching = null;
        status.accept(Component.translatable("gui.hexwright.hex_loom.trigger_set", trigger, triggerKind(weave, trigger)));
    }

    public void openPicker(int trigger) {
        if (weave.kind(trigger) != null) {
            cancel();
            selectTrigger(trigger);
            picking = trigger;
        }
    }

    public void fitAll() {
        if (weave.threads().isEmpty() && weave.triggers().isEmpty()) {
            scale = 3f;
            camX = 0;
            camY = 0;
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (ThreadDraft thread : weave.threads()) {
            WeaveLayout.Position at = weave.position(thread.id());
            minX = Math.min(minX, at.x() - ARMATURE - LEAD);
            maxX = Math.max(maxX, at.x() + ARMATURE + LEAD);
            minY = Math.min(minY, at.y() - HALF_HEIGHT - 5f);
            maxY = Math.max(maxY, at.y() + HALF_HEIGHT + 2f);
        }
        for (int trigger : weave.triggers()) {
            WeaveLayout.Position at = weave.triggerPosition(trigger);
            minX = Math.min(minX, at.x() - TRIGGER_RADIUS - 2f);
            maxX = Math.max(maxX, at.x() + TRIGGER_LEAD + LEAD);
            minY = Math.min(minY, at.y() - WeaveLayout.TRIGGER_HALF_HEIGHT);
            maxY = Math.max(maxY, at.y() + WeaveLayout.TRIGGER_HALF_HEIGHT);
        }
        float width = Math.max(1, right - left - 16);
        float height = Math.max(1, bottom - top - 16);
        scale = Mth.clamp(Math.min(width / (maxX - minX), height / (maxY - minY)), FIT_MIN, FIT_MAX);
        camX = (minX + maxX) / 2;
        camY = (minY + maxY) / 2;
    }

    public WeaveLayout.Position spotForNewThread() {
        Integer thread = selectedThread();
        WeaveLayout.Position from = thread == null ? null : weave.position(thread);
        WeaveLayout.Position start = from != null
            ? new WeaveLayout.Position(from.x() + WeaveLayout.COLUMN_STEP, from.y())
            : new WeaveLayout.Position(camX, camY);
        return WeaveLayout.freeSpot(footprints(), WeaveLayout.Footprint.thread(start)).at();
    }

    public WeaveLayout.Position spotForNewTrigger() {
        Integer thread = selectedThread();
        WeaveLayout.Position from = thread == null ? null : weave.position(thread);
        WeaveLayout.Position start = from != null
            ? new WeaveLayout.Position(from.x() - WeaveLayout.TRIGGER_STEP, from.y())
            : new WeaveLayout.Position(camX, camY);
        return WeaveLayout.freeSpot(footprints(), WeaveLayout.Footprint.trigger(start)).at();
    }

    private List<WeaveLayout.Footprint> footprints() {
        List<WeaveLayout.Footprint> taken = new ArrayList<>();
        weave.threads().forEach(each -> taken.add(WeaveLayout.Footprint.thread(weave.position(each.id()))));
        weave.triggers().forEach(each -> taken.add(WeaveLayout.Footprint.trigger(weave.triggerPosition(each))));
        return taken;
    }

    public Vec2 fragmentCentre(int id) {
        WeaveLayout.Position at = weave.position(id);
        return at == null ? new Vec2(centreX(), centreY()) : toScreen(at.x(), at.y());
    }


    private float centreX() {
        return (left + right) / 2f;
    }

    private float centreY() {
        return (top + bottom) / 2f;
    }

    private Vec2 toScreen(float x, float y) {
        return new Vec2(centreX() + (x - camX) * scale, centreY() + (y - camY) * scale);
    }

    private Vec2 toScreen(Vec2 point) {
        return toScreen(point.x, point.y);
    }

    private float workspaceX(double x) {
        return camX + (float) (x - centreX()) / scale;
    }

    private float workspaceY(double y) {
        return camY + (float) (y - centreY()) / scale;
    }

    public boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private void clampCamera() {
        camX = Mth.clamp(camX, -WeaveLayout.EXTENT, WeaveLayout.EXTENT);
        camY = Mth.clamp(camY, -WeaveLayout.EXTENT, WeaveLayout.EXTENT);
    }

    private @Nullable WeaveLayout.Position position(Node node) {
        return node.trigger() ? weave.triggerPosition(node.id()) : weave.position(node.id());
    }

    private Vec2 armature(int id, boolean tail) {
        WeaveLayout.Position at = weave.position(id);
        return new Vec2(at.x() + (tail ? ARMATURE : -ARMATURE), at.y());
    }

    private Vec2 triggerLead(int trigger) {
        WeaveLayout.Position at = weave.triggerPosition(trigger);
        return new Vec2(at.x() + TRIGGER_LEAD, at.y());
    }

    private Vec2 outlet(Node node) {
        return node.trigger() ? triggerLead(node.id()) : armature(node.id(), true);
    }

    private List<Vec2> course(WeaveLink link) {
        return course(armature(link.from(), true), HALF_HEIGHT, armature(link.to(), false));
    }

    private List<Vec2> course(TriggerLink link) {
        return course(triggerLead(link.trigger()), TRIGGER_RING, armature(link.thread(), false));
    }

    private static float reach(Node node) {
        return node.trigger() ? TRIGGER_RING : HALF_HEIGHT;
    }

    private static List<Vec2> course(Vec2 from, float fromReach, Vec2 to) {
        Vec2 out = new Vec2(from.x + LEAD, from.y);
        Vec2 in = new Vec2(to.x - LEAD, to.y);
        if (in.x >= out.x) {
            return List.of(from, out, in, to);
        }
        float fromTop = from.y - fromReach;
        float fromBottom = from.y + fromReach;
        float toTop = to.y - HALF_HEIGHT;
        float toBottom = to.y + HALF_HEIGHT;
        float across;
        if (fromBottom < toTop) {
            across = (fromBottom + toTop) / 2;
        } else if (toBottom < fromTop) {
            across = (toBottom + fromTop) / 2;
        } else {
            across = Math.min(fromTop, toTop) - 5f;
        }
        return List.of(from, out, new Vec2(out.x, across), new Vec2(in.x, across), in, to);
    }

    private List<Vec2> onScreen(List<Vec2> points) {
        List<Vec2> screen = new ArrayList<>(points.size());
        for (Vec2 point : points) {
            screen.add(toScreen(point));
        }
        return screen;
    }

    private boolean shown(int id) {
        Vec2 centre = fragmentCentre(id);
        float reachX = (HALF_WIDTH + LEAD) * scale + 12;
        float reachY = HALF_HEIGHT * scale + 16;
        return centre.x + reachX >= left && centre.x - reachX < right
            && centre.y + reachY >= top && centre.y - reachY < bottom;
    }


    private List<ThreadDraft> topmostFirst() {
        List<ThreadDraft> threads = new ArrayList<>(weave.threads());
        Collections.reverse(threads);
        return threads;
    }

    private @Nullable Integer tailAt(double x, double y) {
        for (ThreadDraft thread : topmostFirst()) {
            Vec2 at = toScreen(armature(thread.id(), true));
            if (Mth.lengthSquared(at.x - x, at.y - y) <= ARMATURE_HIT * ARMATURE_HIT) {
                return thread.id();
            }
        }
        return null;
    }

    private @Nullable Integer eyeAt(double x, double y, float reach) {
        for (ThreadDraft thread : topmostFirst()) {
            Vec2 at = toScreen(armature(thread.id(), false));
            if (Mth.lengthSquared(at.x - x, at.y - y) <= reach * reach) {
                return thread.id();
            }
        }
        return null;
    }

    private @Nullable Integer leadAt(double x, double y) {
        for (int trigger : weave.triggers()) {
            Vec2 at = toScreen(triggerLead(trigger));
            if (Mth.lengthSquared(at.x - x, at.y - y) <= ARMATURE_HIT * ARMATURE_HIT) {
                return trigger;
            }
        }
        return null;
    }

    private @Nullable Integer threadAt(double x, double y) {
        float wx = workspaceX(x);
        float wy = workspaceY(y);
        for (ThreadDraft thread : topmostFirst()) {
            WeaveLayout.Position at = weave.position(thread.id());
            float dx = Math.abs(wx - at.x());
            float dy = Math.abs(wy - at.y());
            if (dy <= HALF_WIDTH * SQRT_3 / 2 && SQRT_3 * dx + dy <= SQRT_3 * HALF_WIDTH) {
                return thread.id();
            }
        }
        return null;
    }

    private @Nullable Integer triggerAt(double x, double y) {
        float wx = workspaceX(x);
        float wy = workspaceY(y);
        for (int trigger : weave.triggers()) {
            WeaveLayout.Position at = weave.triggerPosition(trigger);
            if (Mth.lengthSquared(wx - at.x(), wy - at.y()) <= (TRIGGER_RADIUS + 1) * (TRIGGER_RADIUS + 1)) {
                return trigger;
            }
        }
        return null;
    }

    private @Nullable Node nodeAt(double x, double y) {
        Integer trigger = triggerAt(x, y);
        if (trigger != null) {
            return Node.trigger(trigger);
        }
        Integer thread = threadAt(x, y);
        return thread == null ? null : Node.thread(thread);
    }

    private @Nullable Object linkAt(double x, double y) {
        Object nearest = null;
        double best = LINK_HIT;
        for (WeaveLink link : weave.links()) {
            double distance = distanceToCourse(x, y, onScreen(course(link)));
            if (distance < best) {
                best = distance;
                nearest = link;
            }
        }
        for (TriggerLink link : weave.triggerLinks()) {
            double distance = distanceToCourse(x, y, onScreen(course(link)));
            if (distance < best) {
                best = distance;
                nearest = link;
            }
        }
        return nearest;
    }

    private static double distanceToCourse(double x, double y, List<Vec2> points) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            best = Math.min(best, distanceToSegment(x, y, points.get(i - 1), points.get(i)));
        }
        return best;
    }

    private @Nullable Integer linkTarget(double x, double y) {
        Integer eye = eyeAt(x, y, ARMATURE_HIT + 3);
        return eye != null ? eye : threadAt(x, y);
    }

    private WeaveDraft.LinkCheck checkLink(Node from, int to) {
        return from.trigger() ? weave.checkTriggerLink(from.id(), to) : weave.checkLink(from.id(), to);
    }

    private static double distanceToSegment(double x, double y, Vec2 a, Vec2 b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Mth.clamp(((x - a.x) * dx + (y - a.y) * dy) / length, 0, 1);
        return Math.sqrt(Mth.lengthSquared(x - (a.x + t * dx), y - (a.y + t * dy)));
    }

    private boolean exists(@Nullable Node node) {
        return node != null && position(node) != null;
    }

    private void validate() {
        if (selected != null && !exists(selected)) {
            selected = null;
        }
        if (selectedLink != null && !weave.links().contains(selectedLink)) {
            selectedLink = null;
        }
        if (selectedTriggerLink != null && !weave.triggerLinks().contains(selectedTriggerLink)) {
            selectedTriggerLink = null;
        }
        if (linkingFrom != null && !exists(linkingFrom)) {
            linkingFrom = null;
        }
        if (moving != null && !exists(moving)) {
            moving = null;
        }
        if (picking != null && weave.kind(picking) == null) {
            picking = null;
        }
        if (reaching != null && weave.kind(reaching) == null) {
            reaching = null;
        }
        if (capturing != null && weave.kind(capturing) == null) {
            capturing = null;
        }
        miniatures.keySet().removeIf(thread -> weave.thread(thread.id()) != thread);
    }

    private Set<Integer> reached() {
        int edits = weave.edits();
        if (edits != reachEdits) {
            reached = weave.reachable();
            reachEdits = edits;
        }
        return reached;
    }


    public boolean mouseClicked(double x, double y, int button) {
        if (!contains(x, y)) {
            return false;
        }
        if (reaching != null) {
            ReachBox box = reachBox(Minecraft.getInstance().font);
            Integer reach = button == 0 ? box.reachAt(x, y) : null;
            if (reach != null) {
                chooseReach(reach);
            } else if (!box.contains(x, y) || button != 0) {
                reaching = null;
            }
            return true;
        }
        if (picking != null) {
            Picker picker = picker(Minecraft.getInstance().font);
            PickerEntry entry = picker.entryAt(x, y);
            if (button == 0 && entry == null && x >= picker.x() && x < picker.x() + picker.width()
                && y >= picker.y() && y < picker.y() + picker.height()) {
                return true;
            }
            WeaveTrigger.Kind kind = button == 0 && entry != null ? entry.kind() : null;
            int trigger = picking;
            picking = null;
            if (kind != null && kind.keyed()) {
                capturing = trigger;
                capturingKind = kind;
                status.accept(Component.translatable("gui.hexwright.hex_loom.trigger_press", trigger, kindName(kind))
                    .withStyle(ChatFormatting.GOLD));
            } else if (kind != null && kind.ranged()) {
                weave.setKind(trigger, kind, "");
                reaching = trigger;
                status.accept(Component.translatable("gui.hexwright.hex_loom.trigger_reach_ask", trigger,
                    WeaveTrigger.MIN_REACH, WeaveTrigger.MAX_REACH).withStyle(ChatFormatting.GOLD));
            } else if (kind != null) {
                weave.setKind(trigger, kind, "");
                status.accept(Component.translatable("gui.hexwright.hex_loom.trigger_set", trigger, kindName(kind)));
            }
            return true;
        }
        if (button == 1 || button == 2) {
            panning = true;
            return true;
        }
        if (button != 0) {
            return false;
        }
        pressX = x;
        pressY = y;
        Integer tail = tailAt(x, y);
        Integer lead = tail == null ? leadAt(x, y) : null;
        if (tail != null || lead != null) {
            linkingFrom = tail != null ? Node.thread(tail) : Node.trigger(lead);
            selectNode(linkingFrom);
            return true;
        }
        Object link = linkAt(x, y);
        if (link != null) {
            clearSelection();
            if (link instanceof WeaveLink threads) {
                selectedLink = threads;
            } else {
                selectedTriggerLink = (TriggerLink) link;
            }
            return true;
        }
        Node node = nodeAt(x, y);
        if (node == null) {
            clearSelection();
            lastClicked = null;
            return true;
        }
        long now = Util.getMillis();
        if (node.equals(lastClicked) && now - lastClick <= DOUBLE_CLICK_MS) {
            lastClicked = null;
            if (node.trigger()) {
                openPicker(node.id());
            } else {
                open.accept(node.id());
            }
            return true;
        }
        lastClicked = node;
        lastClick = now;
        selectNode(node);
        WeaveLayout.Position at = position(node);
        moving = node;
        moved = false;
        grabX = workspaceX(x) - at.x();
        grabY = workspaceY(y) - at.y();
        return true;
    }

    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button == 0 && linkingFrom != null) {
            return true;
        }
        if (button == 0 && moving != null) {
            if (!moved && Math.abs(x - pressX) + Math.abs(y - pressY) < DRAG_START) {
                return true;
            }
            moved = true;
            lastClicked = null;
            float wx = workspaceX(x) - grabX;
            float wy = workspaceY(y) - grabY;
            if (moving.trigger()) {
                weave.moveTrigger(moving.id(), wx, wy);
            } else {
                weave.move(moving.id(), wx, wy);
            }
            return true;
        }
        if (panning && (button == 1 || button == 2)) {
            camX -= (float) dragX / scale;
            camY -= (float) dragY / scale;
            clampCamera();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && linkingFrom != null) {
            Node from = linkingFrom;
            linkingFrom = null;
            if (contains(x, y) && Math.abs(x - pressX) + Math.abs(y - pressY) >= DRAG_START) {
                Integer to = linkTarget(x, y);
                if (to != null) {
                    link(from, to);
                }
            }
            return true;
        }
        if (button == 0 && moving != null) {
            moving = null;
            return true;
        }
        if (panning && (button == 1 || button == 2)) {
            panning = false;
            return true;
        }
        return false;
    }

    private void link(Node from, int to) {
        String subject = from.trigger() ? "trigger" : "thread";
        switch (checkLink(from, to)) {
            case OK -> {
                clearSelection();
                if (from.trigger()) {
                    weave.linkTrigger(from.id(), to);
                    selectedTriggerLink = new TriggerLink(from.id(), to);
                } else {
                    weave.link(from.id(), to);
                    selectedLink = new WeaveLink(from.id(), to);
                }
                status.accept(Component.translatable("gui.hexwright.hex_loom.linked." + subject, from.id(), to));
            }
            case DUPLICATE -> status.accept(Component.translatable("gui.hexwright.hex_loom.link.duplicate." + subject,
                from.id(), to).withStyle(ChatFormatting.GOLD));
            case FULL -> status.accept(Component.translatable("gui.hexwright.hex_loom.link.full",
                Hexweave.MAX_LINKS).withStyle(ChatFormatting.GOLD));
            default -> {
            }
        }
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (!contains(x, y) || delta == 0) {
            return false;
        }
        picking = null;
        if (delta > 0 && scale >= MAX_SCALE) {
            Integer thread = threadAt(x, y);
            if (thread != null) {
                open.accept(thread);
            }
            return true;
        }
        zoomAbout(x, y, delta);
        return true;
    }

    private void zoomAbout(double x, double y, double delta) {
        float wx = workspaceX(x);
        float wy = workspaceY(y);
        scale = Mth.clamp(delta > 0 ? scale * ZOOM_STEP : scale / ZOOM_STEP, MIN_SCALE, MAX_SCALE);
        camX = wx - (float) (x - centreX()) / scale;
        camY = wy - (float) (y - centreY()) / scale;
        clampCamera();
    }


    private record Picker(int x, int y, int width, int height, List<PickerEntry> entries) {
        @Nullable PickerEntry entryAt(double px, double py) {
            for (PickerEntry entry : entries) {
                if (px >= entry.x && px < entry.x + entry.width && py >= entry.y && py < entry.y + entry.height) {
                    return entry;
                }
            }
            return null;
        }
    }

    private record PickerEntry(WeaveTrigger.Kind kind, int x, int y, int width, int height,
                               List<FormattedCharSequence> description) {
    }

    private Picker picker(Font font) {
        WeaveTrigger.Kind[] kinds = WeaveTrigger.Kind.values();
        int room = right - left - LoomMenu.CHROME_WIDTH;
        int columns = room >= 2 * PICKER_COLUMN + PICKER_PAD ? 2 : 1;
        int column = Math.min(PICKER_COLUMN, (room - (columns - 1) * PICKER_PAD) / columns);
        int rows = (kinds.length + columns - 1) / columns;
        List<List<FormattedCharSequence>> descriptions = new ArrayList<>(kinds.length);
        int[] rowHeights = new int[rows];
        for (int i = 0; i < kinds.length; i++) {
            List<FormattedCharSequence> lines = font.split(kindDescription(kinds[i]), column - 2 * PICKER_INSET);
            descriptions.add(lines);
            int height = PICKER_NAME + lines.size() * font.lineHeight + 2 * PICKER_INSET;
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], height);
        }
        int width = columns * column + (columns - 1) * PICKER_PAD + LoomMenu.CHROME_WIDTH;
        int height = LoomMenu.CHROME_HEIGHT - PICKER_PAD;
        for (int rowHeight : rowHeights) {
            height += rowHeight + PICKER_PAD;
        }
        int boxX = Math.round(centreX() - width / 2f);
        int boxY = Math.max(top, Math.round(centreY() - height / 2f));
        List<PickerEntry> entries = new ArrayList<>(kinds.length);
        int rowY = LoomMenu.contentY(boxY);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns && row * columns + col < kinds.length; col++) {
                int i = row * columns + col;
                entries.add(new PickerEntry(kinds[i], LoomMenu.contentX(boxX) + col * (column + PICKER_PAD), rowY, column,
                    rowHeights[row], descriptions.get(i)));
            }
            rowY += rowHeights[row] + PICKER_PAD;
        }
        return new Picker(boxX, boxY, width, height, entries);
    }

    private record ReachBox(int x, int y, int width, int height, int buttonsY) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        @Nullable Integer reachAt(double px, double py) {
            if (py < buttonsY || py >= buttonsY + REACH_BUTTON) {
                return null;
            }
            for (int reach = WeaveTrigger.MIN_REACH; reach <= WeaveTrigger.MAX_REACH; reach++) {
                int bx = buttonX(reach);
                if (px >= bx && px < bx + REACH_BUTTON) {
                    return reach;
                }
            }
            return null;
        }

        int buttonX(int reach) {
            int row = (WeaveTrigger.MAX_REACH - WeaveTrigger.MIN_REACH + 1) * (REACH_BUTTON + PICKER_PAD) - PICKER_PAD;
            return x + (width - row) / 2 + (reach - WeaveTrigger.MIN_REACH) * (REACH_BUTTON + PICKER_PAD);
        }
    }

    private ReachBox reachBox(Font font) {
        Component title = Component.translatable("gui.hexwright.hex_loom.trigger_reach", reaching);
        int buttons = (WeaveTrigger.MAX_REACH - WeaveTrigger.MIN_REACH + 1) * (REACH_BUTTON + PICKER_PAD) - PICKER_PAD;
        int width = Math.max(buttons, font.width(title)) + LoomMenu.CHROME_WIDTH;
        int height = REACH_BUTTON + PICKER_PAD + font.lineHeight + LoomMenu.CHROME_HEIGHT;
        int boxX = Math.round(centreX() - width / 2f);
        int boxY = Math.max(top, Math.round(centreY() - height / 2f));
        return new ReachBox(boxX, boxY, width, height, LoomMenu.contentY(boxY));
    }

    private void drawReach(GuiGraphics graphics, int mouseX, int mouseY, LoomPalette palette, LoomMenu.Look look) {
        if (reaching == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        ReachBox box = reachBox(font);
        Integer hovered = box.reachAt(mouseX, mouseY);
        int current = weave.reach(reaching);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 40);
        LoomMenu.drawFrame(graphics, look, mouseX, mouseY, box.x(), box.y(), box.width(), box.height(),
            Component.translatable("gui.hexwright.hex_loom.trigger_reach", reaching), 0xFF000000 | palette.label());
        for (int reach = WeaveTrigger.MIN_REACH; reach <= WeaveTrigger.MAX_REACH; reach++) {
            int bx = box.buttonX(reach);
            boolean chosen = reach == current;
            LoomMenu.drawRow(graphics, look, mouseX, mouseY, bx, box.buttonsY(), REACH_BUTTON, REACH_BUTTON,
                Objects.equals(hovered, reach));
            if (chosen) {
                graphics.renderOutline(bx, box.buttonsY(), REACH_BUTTON, REACH_BUTTON, 0xFF000000 | palette.trigger());
            }
            String number = String.valueOf(reach);
            graphics.drawString(font, number, bx + (REACH_BUTTON - font.width(number)) / 2 + 1,
                box.buttonsY() + (REACH_BUTTON - font.lineHeight) / 2 + 1,
                chosen ? 0xFF000000 | palette.trigger() : 0xFFFFFFFF);
        }
        Component unit = Component.translatable("gui.hexwright.hex_loom.trigger_reach_unit");
        graphics.drawString(font, unit, box.x() + (box.width() - font.width(unit)) / 2,
            box.buttonsY() + REACH_BUTTON + PICKER_PAD, 0xA0000000 | palette.label(), false);
        pose.popPose();
    }

    private void drawPicker(GuiGraphics graphics, int mouseX, int mouseY, LoomPalette palette, LoomMenu.Look look) {
        if (picking == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Picker picker = picker(font);
        PickerEntry hovered = picker.entryAt(mouseX, mouseY);
        WeaveTrigger.Kind current = weave.kind(picking);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 40);
        LoomMenu.drawFrame(graphics, look, mouseX, mouseY, picker.x(), picker.y(), picker.width(), picker.height(),
            Component.translatable("gui.hexwright.hex_loom.trigger_type", picking), 0xFF000000 | palette.label());
        for (PickerEntry entry : picker.entries()) {
            boolean chosen = entry.kind() == current;
            LoomMenu.drawRow(graphics, look, mouseX, mouseY, entry.x(), entry.y(), entry.width(), entry.height(),
                entry == hovered);
            int nameColour = chosen ? 0xFF000000 | palette.trigger() : 0xFFFFFFFF;
            int textX = entry.x() + PICKER_INSET;
            int textY = entry.y() + PICKER_INSET;
            if (chosen) {
                graphics.fill(textX, textY + 2, textX + 3, textY + 5, nameColour);
            }
            graphics.drawString(font, kindName(entry.kind()), textX + (chosen ? 6 : 0), textY, nameColour);
            int lineY = textY + PICKER_NAME;
            for (FormattedCharSequence line : entry.description()) {
                graphics.drawString(font, line, textX, lineY, 0xFF000000 | palette.label(), false);
                lineY += font.lineHeight;
            }
        }
        pose.popPose();
    }


    public void render(GuiGraphics graphics, int mouseX, int mouseY, LoomPalette palette, LoomMenu.Look menus) {
        validate();
        Set<Integer> reached = reached();
        boolean idle = contains(mouseX, mouseY) && !busy() && !panning;
        Object hoveredLink = idle ? linkAt(mouseX, mouseY) : null;
        Node hovered = idle && hoveredLink == null ? nodeAt(mouseX, mouseY) : null;
        Integer target = linkingFrom != null && contains(mouseX, mouseY) ? linkTarget(mouseX, mouseY) : null;
        List<ThreadDraft> shown = weave.threads().stream().filter(thread -> shown(thread.id())).toList();

        graphics.enableScissor(left, top, right, bottom);
        PoseStack pose = graphics.pose();
        RenderSystem.disableDepthTest();

        for (ThreadDraft thread : shown) {
            drawFrame(graphics, thread, Node.thread(thread.id()).equals(selected),
                Node.thread(thread.id()).equals(hovered), reached.contains(thread.id()), palette);
        }

        pose.pushPose();
        pose.translate(centreX(), centreY(), 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-camX, -camY, 3);
        for (ThreadDraft thread : shown) {
            drawMiniature(pose, thread, reached.contains(thread.id()));
        }
        pose.popPose();

        pose.pushPose();
        pose.translate(0, 0, 1.5f);
        Matrix4f screen = pose.last().pose();
        for (WeaveLink link : weave.links()) {
            drawLink(screen, course(link), link.equals(selectedLink), link.equals(hoveredLink),
                reached.contains(link.from()), palette.tail(), palette, link.from() * 31.0 + link.to());
        }
        for (TriggerLink link : weave.triggerLinks()) {
            drawLink(screen, course(link), link.equals(selectedTriggerLink), link.equals(hoveredLink), true,
                palette.trigger(), palette, -link.trigger() * 31.0 - link.thread());
        }
        pose.translate(0, 0, 3);
        screen = pose.last().pose();
        for (ThreadDraft thread : shown) {
            drawArmatures(screen, thread, target, palette);
        }
        for (int trigger : weave.triggers()) {
            drawTriggerLead(screen, trigger, palette);
        }
        drawPendingLink(screen, mouseX, mouseY, target);
        pose.popPose();

        PatternDrawBatch.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        for (int trigger : weave.triggers()) {
            drawSeal(graphics, trigger, Node.trigger(trigger).equals(selected), Node.trigger(trigger).equals(hovered),
                palette);
        }

        drawLabels(graphics, shown, palette);
        drawPicker(graphics, mouseX, mouseY, palette, menus);
        drawReach(graphics, mouseX, mouseY, palette, menus);
        graphics.disableScissor();
    }

    private void drawFrame(GuiGraphics graphics, ThreadDraft thread, boolean selected, boolean hovered,
                           boolean reached, LoomPalette palette) {
        Vec2 at = pixelSnapped(weave.position(thread.id()));
        ThreadFrame.draw(graphics, at.x, at.y, scale, scale, hovered ? 1.2f : reached ? 1f : 0.6f);
        if (selected) {
            ThreadFrame.drawSelected(graphics, at.x, at.y, scale, scale, palette.selected());
        }
    }

    private Vec2 pixelSnapped(WeaveLayout.Position at) {
        Vec2 point = toScreen(at.x(), at.y());
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        return new Vec2((float) (Math.round(point.x * guiScale) / guiScale),
            (float) (Math.round(point.y * guiScale) / guiScale));
    }

    private void drawMiniature(PoseStack pose, ThreadDraft thread, boolean reached) {
        Miniature miniature = miniature(thread);
        if (miniature.strokes().isEmpty()) {
            return;
        }
        WeaveLayout.Position at = weave.position(thread.id());
        pose.pushPose();
        pose.translate(at.x(), at.y(), 0);
        Matrix4f mat = pose.last().pose();
        float outer = Mth.clamp(scale * 0.55f, 1.3f, 3.2f) / scale;
        float inner = outer * 0.45f;
        int alpha = reached ? 0xE6 : 0x60;
        for (int i = 0; i < miniature.strokes().size(); i++) {
            List<Vec2> stroke = miniature.strokes().get(i);
            if (stroke.isEmpty()) {
                continue;
            }
            ResolvedPatternType look = miniature.outside().contains(i) ? ResolvedPatternType.ERRORED
                : ResolvedPatternType.EVALUATED;
            int start = tint(look.getColor(), alpha);
            int end = tint(look.getFadeColor(), alpha);
            PatternDrawBatch.drawLineSeq(mat, stroke, outer, start, end);
            PatternDrawBatch.drawLineSeq(mat, stroke, inner, RenderLib.screenCol(start), RenderLib.screenCol(end));
        }
        for (Knot knot : miniature.knots()) {
            PatternDrawBatch.drawRoundSpot(mat, knot.at(), outer * 1.6f, tint(knot.colour(), alpha));
            PatternDrawBatch.drawRoundSpot(mat, knot.at(), inner, tint(RenderLib.screenCol(knot.colour()), alpha));
        }
        pose.popPose();
    }

    private Miniature miniature(ThreadDraft thread) {
        Miniature cached = miniatures.get(thread);
        if (cached != null && cached.edits() == thread.edits()) {
            return cached;
        }
        List<ThreadEntry> entries = thread.entries();
        List<List<Vec2>> strokes = new ArrayList<>(entries.size());
        List<Knot> knots = new ArrayList<>();
        for (ThreadEntry entry : entries) {
            Vec2 origin = HexUtils.coordToPx(entry.origin(), 1f, Vec2.ZERO);
            if (entry instanceof PlacedPattern placed) {
                strokes.add(placed.pattern().toLines(1f, origin));
            } else {
                strokes.add(List.of());
                knots.add(new Knot(origin, EmbedKnot.colour((EmbeddedIota) entry)));
            }
        }
        Miniature built = new Miniature(thread.edits(), strokes, knots, Set.copyOf(thread.outOfBounds()));
        miniatures.put(thread, built);
        return built;
    }

    private void drawLink(Matrix4f mat, List<Vec2> course, boolean selected, boolean hovered, boolean reached,
                          int from, LoomPalette palette, double seed) {
        int alpha = selected || hovered ? 0xF0 : reached ? 0xB4 : 0x5A;
        int start = tint(selected ? palette.selected() : from, alpha);
        int end = tint(selected ? palette.selectedFade() : palette.eye(), alpha);
        PatternDrawBatch.drawCourse(mat, onScreen(course), LINK_PIECE, start, end, 0.25f, seed);
    }

    private void drawArmatures(Matrix4f mat, ThreadDraft thread, @Nullable Integer target, LoomPalette palette) {
        int id = thread.id();
        Vec2 west = toScreen(armature(id, false));
        Vec2 east = toScreen(armature(id, true));
        boolean aimed = Objects.equals(target, id);
        if (linkingFrom != null && aimed && checkLink(linkingFrom, id) != WeaveDraft.LinkCheck.OK) {
            PatternDrawBatch.drawRoundSpot(mat, west, 8f, tint(ResolvedPatternType.ERRORED.getColor(), 0x70));
        }
        if (linkingFrom != null && checkLink(linkingFrom, id) == WeaveDraft.LinkCheck.OK) {
            float pulse = 0.5f + 0.5f * Mth.sin(Util.getMillis() / 180f);
            PatternDrawBatch.drawRoundSpot(mat, west, aimed ? 9f : 6.5f + pulse * 1.5f,
                tint(palette.eye(), aimed ? 0x90 : 0x40 + (int) (pulse * 0x30)));
        }
        drawMark(mat, west, weave.entered(id), palette.eye());
        drawMark(mat, east, !weave.linksFrom(id).isEmpty(), palette.tail());
    }

    private static void drawMark(Matrix4f mat, Vec2 at, boolean linked, int colour) {
        if (linked) {
            PatternDrawBatch.drawRoundSpot(mat, at, 4.5f, tint(colour, 0xF0));
            PatternDrawBatch.drawRoundSpot(mat, at, 2f, 0xFFFFFFFF);
        } else {
            PatternDrawBatch.drawRoundSpot(mat, at, 3.5f, tint(colour, 0x60));
            PatternDrawBatch.drawRoundSpot(mat, at, 1.8f, tint(PLANE, 0xFF));
        }
    }

    private void drawTriggerLead(Matrix4f mat, int trigger, LoomPalette palette) {
        WeaveLayout.Position at = weave.triggerPosition(trigger);
        Vec2 centre = toScreen(at.x(), at.y());
        Vec2 lead = toScreen(triggerLead(trigger));
        boolean linked = !weave.triggerLinksFrom(trigger).isEmpty();
        int amber = tint(palette.trigger(), linked ? 0xE6 : 0x90);
        float line = Math.max(1.5f, 0.35f * scale);
        PatternDrawBatch.drawLineSeq(mat, List.of(centre, lead), line, amber, amber);
        drawMark(mat, lead, linked, palette.trigger());
    }

    private void drawSeal(GuiGraphics graphics, int trigger, boolean selected, boolean hovered, LoomPalette palette) {
        Vec2 at = pixelSnapped(weave.triggerPosition(trigger));
        boolean linked = !weave.triggerLinksFrom(trigger).isEmpty();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 4.5f);
        SEAL_PLANE.draw(graphics, at.x, at.y, scale, scale, 0xFFFFFF, 1f);
        SEAL.draw(graphics, at.x, at.y, scale, scale, palette.trigger(), hovered ? 1.2f : linked ? 1f : 0.6f);
        if (selected) {
            SEAL_SELECTED.draw(graphics, at.x, at.y, scale, scale, palette.selected(), 1f);
        }
        pose.popPose();
    }

    private void drawPendingLink(Matrix4f mat, int mouseX, int mouseY, @Nullable Integer target) {
        if (linkingFrom == null) {
            return;
        }
        Vec2 from = outlet(linkingFrom);
        List<Vec2> points;
        int start = 0xFF64C8FF;
        int end = 0xFFFECBE6;
        if (target != null && checkLink(linkingFrom, target) == WeaveDraft.LinkCheck.OK) {
            points = onScreen(course(from, reach(linkingFrom), armature(target, false)));
        } else {
            Vec2 origin = toScreen(from);
            points = List.of(origin, new Vec2(origin.x + LEAD * scale, origin.y), new Vec2(mouseX, mouseY));
            if (target != null) {
                start = tint(ResolvedPatternType.ERRORED.getColor(), 0xC0);
                end = tint(ResolvedPatternType.ERRORED.getFadeColor(), 0xC0);
            }
        }
        PatternDrawBatch.drawCourse(mat, points, LINK_PIECE, start, end, 0.1f, 0);
    }

    private void drawLabels(GuiGraphics graphics, List<ThreadDraft> shown, LoomPalette palette) {
        Font font = Minecraft.getInstance().font;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 10);
        for (ThreadDraft thread : shown) {
            WeaveLayout.Position at = weave.position(thread.id());
            Vec2 corner = toScreen(at.x() - OUTLINE_REACH / 2, at.y() - OUTLINE_REACH * SQRT_3 / 2);
            Component text = scale >= 2.5f
                ? Component.translatable("gui.hexwright.hex_loom.label", thread.id())
                : Component.literal(String.valueOf(thread.id()));
            int colour = Node.thread(thread.id()).equals(selected) ? 0xFF000000 | palette.selected()
                : 0xC0000000 | palette.label();
            graphics.drawString(font, text, (int) corner.x, (int) corner.y - 15, colour);
        }
        for (int trigger : weave.triggers()) {
            WeaveLayout.Position at = weave.triggerPosition(trigger);
            Vec2 centre = toScreen(at.x(), at.y());
            float radius = TRIGGER_RING * scale;
            boolean chosen = Node.trigger(trigger).equals(selected);
            boolean asking = Objects.equals(capturing, trigger);
            Component kind = asking ? Component.translatable("gui.hexwright.hex_loom.press_key") : triggerKind(weave, trigger);
            graphics.drawString(font, kind, (int) (centre.x - font.width(kind) / 2f), (int) (centre.y + radius + 4),
                asking ? 0xFF000000 | palette.selected() : chosen ? 0xFF000000 | palette.selected()
                    : 0xE0000000 | palette.trigger());
            if (scale >= 2.5f) {
                Component name = Component.translatable("gui.hexwright.hex_loom.trigger_name", trigger);
                graphics.drawString(font, name, (int) (centre.x - font.width(name) / 2f), (int) (centre.y - radius - 14),
                    chosen ? 0xFF000000 | palette.selected() : 0xC0000000 | palette.label());
            }
        }
        pose.popPose();
    }

    public @Nullable List<Component> hover(int x, int y) {
        if (!contains(x, y) || panning) {
            return null;
        }
        validate();
        if (picking != null || reaching != null) {
            return null;
        }
        if (busy()) {
            return null;
        }
        Integer tail = tailAt(x, y);
        if (tail != null) {
            List<WeaveLink> out = weave.linksFrom(tail);
            return List.of(out.isEmpty() ? Component.translatable("gui.hexwright.hex_loom.hover.tail")
                : Component.translatable("gui.hexwright.hex_loom.hover.to", ids(out, false)),
                Component.translatable("gui.hexwright.hex_loom.hover.tail_help").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (leadAt(x, y) != null) {
            return List.of(Component.translatable("gui.hexwright.hex_loom.hover.lead"));
        }
        Object link = linkAt(x, y);
        if (link instanceof WeaveLink threads) {
            return List.of(Component.translatable("gui.hexwright.hex_loom.hover.link", threads.from(), threads.to()),
                Component.translatable("gui.hexwright.hex_loom.hover.link_help").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (link instanceof TriggerLink started) {
            return List.of(Component.translatable("gui.hexwright.hex_loom.hover.trigger_link",
                    triggerLabel(weave, started.trigger()), started.thread()),
                Component.translatable("gui.hexwright.hex_loom.hover.link_help").withStyle(ChatFormatting.DARK_GRAY));
        }
        Node node = nodeAt(x, y);
        if (node == null) {
            return null;
        }
        return node.trigger() ? triggerHover(node.id()) : threadHover(node.id());
    }

    private List<Component> triggerHover(int trigger) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.hexwright.hex_loom.trigger_name", trigger));
        lines.add(triggerHelp(weave, trigger));
        List<TriggerLink> out = weave.triggerLinksFrom(trigger);
        lines.add(out.isEmpty()
            ? Component.translatable("gui.hexwright.hex_loom.hover.starts_none").withStyle(ChatFormatting.GOLD)
            : Component.translatable("gui.hexwright.hex_loom.hover.starts", out.stream()
                .map(link -> String.valueOf(link.thread())).collect(Collectors.joining(", ")))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.hexwright.hex_loom.hover.trigger_help").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> threadHover(int id) {
        ThreadDraft thread = weave.thread(id);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.hexwright.hex_loom.label", id));
        int embedded = EmbedKnot.embedded(thread);
        lines.add((embedded == 0 ? Component.translatable("gui.hexwright.hex_loom.hover.patterns", thread.size())
            : Component.translatable("gui.hexwright.hex_loom.hover.mixed", thread.size() - embedded, embedded))
            .withStyle(ChatFormatting.GRAY));
        if (weave.entered(id)) {
            lines.add(Component.translatable("gui.hexwright.hex_loom.hover.from", entrants(weave, id))
                .withStyle(ChatFormatting.AQUA));
        }
        List<WeaveLink> out = weave.linksFrom(id);
        if (!out.isEmpty()) {
            lines.add(Component.translatable("gui.hexwright.hex_loom.hover.to", ids(out, false))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (!reached().contains(id)) {
            lines.add(Component.translatable("gui.hexwright.hex_loom.hover.unreached").withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("gui.hexwright.hex_loom.hover.open").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    static String ids(List<WeaveLink> links, boolean sources) {
        return links.stream().map(link -> String.valueOf(sources ? link.from() : link.to()))
            .collect(Collectors.joining(", "));
    }

    static Component entrants(WeaveDraft weave, int thread) {
        List<Component> parts = new ArrayList<>();
        for (TriggerLink link : weave.triggerLinksInto(thread)) {
            parts.add(triggerLabel(weave, link.trigger()));
        }
        for (WeaveLink link : weave.linksInto(thread)) {
            parts.add(Component.translatable("gui.hexwright.hex_loom.label", link.from()));
        }
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            joined.append(i == 0 ? Component.empty() : Component.literal(", ")).append(parts.get(i));
        }
        return joined;
    }

    static Component triggerLabel(WeaveDraft weave, int trigger) {
        return Component.translatable("gui.hexwright.hex_loom.trigger_label", trigger, triggerKind(weave, trigger));
    }

    static Component triggerKind(WeaveDraft weave, int trigger) {
        WeaveTrigger.Kind kind = weave.kind(trigger);
        if (kind != null && kind.keyed()) {
            return Component.translatable("gui.hexwright.hex_loom.trigger." + kind.id() + ".with", keyName(weave.key(trigger)));
        }
        if (kind != null && kind.ranged()) {
            int reach = weave.reach(trigger);
            return Component.translatable("gui.hexwright.hex_loom.trigger." + kind.id() + (reach == 1 ? ".with_one" : ".with"),
                reach);
        }
        return kindName(kind);
    }

    static Component kindName(WeaveTrigger.@Nullable Kind kind) {
        return kind == null ? Component.literal("?")
            : Component.translatable("gui.hexwright.hex_loom.trigger." + kind.id());
    }

    static Component keyName(String key) {
        try {
            return InputConstants.getKey(key).getDisplayName();
        } catch (IllegalArgumentException e) {
            return Component.literal(key);
        }
    }

    private static Component kindDescription(WeaveTrigger.Kind kind) {
        return Component.translatable("gui.hexwright.hex_loom.trigger." + kind.id() + ".description");
    }

    private static Component triggerHelp(WeaveDraft weave, int trigger) {
        WeaveTrigger.Kind kind = weave.kind(trigger);
        if (kind == null) {
            return Component.empty();
        }
        String key = "gui.hexwright.hex_loom.trigger." + kind.id() + ".help";
        Component help = kind.keyed() ? Component.translatable(key, keyName(weave.key(trigger)))
            : kind.ranged() ? Component.translatable(key, weave.reach(trigger)) : Component.translatable(key);
        return help.copy().withStyle(ChatFormatting.YELLOW);
    }

    private static int tint(int colour, int alpha) {
        return (alpha << 24) | (colour & 0xFFFFFF);
    }
}

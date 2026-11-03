package com.bluup.hexwright.client.hexweave;

import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.bluup.hexwright.server.hexweave.HexLoomBlockEntity;
import com.bluup.hexwright.server.hexweave.Hexweave;
import com.bluup.hexwright.server.hexweave.HexweaveNbt;
import com.bluup.hexwright.server.hexweave.LoomNetworking;
import com.bluup.hexwright.server.hexweave.ThreadDraft;
import com.bluup.hexwright.server.hexweave.ThreadEntry;
import com.bluup.hexwright.server.hexweave.TriggerLink;
import com.bluup.hexwright.server.hexweave.WeaveDraft;
import com.bluup.hexwright.server.hexweave.WeaveLayout;
import com.bluup.hexwright.server.hexweave.WeaveLink;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class LoomEditor {

    private static final double REACH_SQR = 64.0;
    private static final long TRANSITION_MS = 220;
    private static final int CONFIRM_TICKS = 80;

    private final @Nullable BlockPos pos;
    private int revision;
    private final WeaveDraft weave;
    private final WeaveOverview overview;
    private final Map<ThreadDraft, ThreadCanvas> canvases = new HashMap<>();
    private final ThreadTimeline timeline = new ThreadTimeline();
    private @Nullable CopyPicker picker;
    private @Nullable Integer editing;
    private @Nullable Transition transition;
    private @Nullable Confirm confirm;
    private Component status = Component.empty();
    private boolean waiting;
    private final LoomHistory history;
    private LoomHistory.Step saved;
    private LoomHistory.@Nullable Step sent;
    private int problemEdits = -1;
    private @Nullable Component problem;
    private int ticks;
    private Runnable closer = () -> {
    };
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int timelineHeight;

    private record Confirm(LoomAction action, int until) {
    }

    private record Transition(int thread, boolean opening, long started) {
    }

    public record StatusLine(Component text, boolean quiet) {
    }

    private LoomEditor(@Nullable BlockPos pos, int revision, WeaveDraft weave) {
        this.pos = pos;
        this.revision = revision;
        this.weave = weave;
        this.overview = new WeaveOverview(weave, this::setStatus, this::openThread);
        this.history = new LoomHistory(weave, insertionPoints());
        this.saved = history.current();
    }

    static LoomEditor forLoom(BlockPos pos, int revision, WeaveDraft weave) {
        return new LoomEditor(pos, revision, weave);
    }

    static LoomEditor preview(boolean inThread) {
        LoomEditor editor = new LoomEditor(null, 0, sampleWeave());
        if (inThread) {
            ThreadDraft root = editor.weave.threads().iterator().next();
            editor.overview.select(root.id());
            editor.canvases.put(root, new ThreadCanvas(editor.weave, root, editor::setStatus));
            editor.editing = root.id();
        }
        return editor;
    }

    private static WeaveDraft sampleWeave() {
        WeaveDraft weave = WeaveDraft.blank();
        ThreadDraft root = weave.threads().iterator().next();
        ThreadDraft upper = weave.addThread(new WeaveLayout.Position(WeaveLayout.COLUMN_STEP, -WeaveLayout.ROW_STEP / 2));
        ThreadDraft lower = weave.addThread(new WeaveLayout.Position(WeaveLayout.COLUMN_STEP, WeaveLayout.ROW_STEP / 2));
        root.add(HexPattern.fromAngles("qaq", HexDir.NORTH_EAST), new HexCoord(-6, 1));
        root.add(HexPattern.fromAngles("aa", HexDir.EAST), new HexCoord(1, -5));
        root.add(HexPattern.fromAngles("wqaawdd", HexDir.EAST), new HexCoord(-2, 4));
        if (upper != null && lower != null) {
            weave.link(root.id(), upper.id());
            weave.link(root.id(), lower.id());
            upper.add(HexPattern.fromAngles("wa", HexDir.EAST), new HexCoord(-3, 0));
            lower.add(HexPattern.fromAngles("qaq", HexDir.NORTH_EAST), new HexCoord(-1, 0));
        }
        return weave;
    }

    public boolean isPreview() {
        return pos == null;
    }

    boolean editing(BlockPos at) {
        return pos != null && pos.equals(at);
    }

    void setCloser(Runnable closer) {
        this.closer = closer;
    }

    public boolean inThread() {
        return editing != null;
    }

    public boolean transitioning() {
        return transition != null;
    }

    public void setArea(int left, int top, int right, int bottom, int timelineHeight) {
        if (left == this.left && top == this.top && right == this.right && bottom == this.bottom
            && timelineHeight == this.timelineHeight) {
            return;
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.timelineHeight = timelineHeight;
        overview.setBounds(left, top, right, bottom);
        timeline.setBounds(left, bottom - timelineHeight, right, bottom);
        canvases.values().forEach(this::place);
    }

    private void place(ThreadCanvas canvas) {
        canvas.setBounds(left, top, right, bottom - timelineHeight);
    }

    private @Nullable Integer current() {
        return editing != null ? editing : overview.selectedThread();
    }

    private @Nullable ThreadDraft currentThread() {
        Integer current = current();
        return current == null ? null : weave.thread(current);
    }

    private @Nullable ThreadCanvas canvas() {
        return editing == null ? null : canvases.get(weave.thread(editing));
    }

    void tick() {
        if (pos != null && !loomStillThere()) {
            closer.run();
            return;
        }
        ticks++;
        if (confirm != null && ticks > confirm.until()) {
            confirm = null;
        }
        if (!midGesture()) {
            commit();
        }
    }

    private boolean midGesture() {
        ThreadCanvas canvas = canvas();
        return overview.busy() || canvas != null && canvas.drawing();
    }

    private void commit() {
        history.commit(insertionPoints());
    }

    private Map<ThreadCanvas, Integer> insertionPoints() {
        Map<ThreadCanvas, Integer> points = new HashMap<>();
        canvases.values().forEach(canvas -> points.put(canvas, canvas.heldInsertionPoint()));
        return points;
    }

    private boolean loomStillThere() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null
            && minecraft.level.getBlockEntity(pos) instanceof HexLoomBlockEntity loom && !loom.orb().isEmpty()
            && minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) <= REACH_SQR;
    }


    public boolean active(LoomAction action) {
        boolean weaving = editing == null;
        ThreadDraft thread = currentThread();
        boolean empty = thread == null || thread.size() == 0;
        ThreadCanvas canvas = canvas();
        return switch (action) {
            case ADD -> weaving && weave.threads().size() < Hexweave.MAX_THREADS;
            case ADD_TRIGGER -> weaving && weave.triggers().size() < Hexweave.MAX_TRIGGERS;
            case OPEN -> weaving && (overview.selectedThread() != null || overview.selectedTrigger() != null);
            case DELETE -> weaving && (overview.selectedThread() != null || overview.selectedTrigger() != null
                || linkSelected());
            case UNLINK -> weaving && linkSelected();
            case FIT, CLOSE -> true;
            case ZOOM_IN -> canvas != null ? canvas.canZoomIn()
                : !overview.atClosest() || overview.selectedThread() != null;
            case ZOOM_OUT -> canvas != null || !overview.atWidest();
            case BACK -> !weaving;
            case UNDO -> history.canUndo();
            case REDO -> history.canRedo();
            case CLEAR -> !weaving && !empty;
            case REMOVE -> canvas != null && canvas.selected() != null;
            case COPY -> canvas != null && !canvas.drawing();
            case SAVE -> pos != null && !waiting && problem() == null;
        };
    }

    public Component label(LoomAction action) {
        if (armed(action)) {
            return Component.translatable("gui.hexwright.hex_loom.confirm").withStyle(ChatFormatting.GOLD);
        }
        if (editing == null && action == LoomAction.OPEN && overview.selectedTrigger() != null) {
            return Component.translatable("gui.hexwright.hex_loom.open_trigger");
        }
        if (editing == null && action == LoomAction.DELETE) {
            if (linkSelected()) {
                return LoomAction.UNLINK.label();
            }
            if (overview.selectedTrigger() != null) {
                return Component.translatable("gui.hexwright.hex_loom.delete_trigger");
            }
            if (overview.selectedThread() != null) {
                return Component.translatable("gui.hexwright.hex_loom.delete_thread");
            }
        }
        return action.label();
    }

    private boolean linkSelected() {
        return overview.selectedLink() != null || overview.selectedTriggerLink() != null;
    }

    public boolean armed(LoomAction action) {
        return confirm != null && confirm.action() == action;
    }

    @Nullable LoomAction armed() {
        return confirm == null ? null : confirm.action();
    }

    void disarm() {
        confirm = null;
        status = Component.empty();
    }

    public Component summary() {
        ThreadDraft thread = editing == null ? null : weave.thread(editing);
        Component summary = thread != null ? threadSummary(thread)
            : Component.translatable("gui.hexwright.hex_loom.weave", weave.threads().size(),
                weave.links().size() + weave.triggerLinks().size());
        if (pos != null && history.differsFrom(saved)) {
            summary = summary.copy().append(Component.translatable("gui.hexwright.hex_loom.unsaved"));
        }
        return summary;
    }

    private static Component threadSummary(ThreadDraft thread) {
        return Component.translatable(EmbedKnot.embedded(thread) == 0 ? "gui.hexwright.hex_loom.thread"
            : "gui.hexwright.hex_loom.thread_entries", thread.id(), thread.size());
    }

    public StatusLine statusLine() {
        Component problem = problem();
        boolean quiet = confirm == null && problem == null && status.getString().isEmpty();
        Component line = confirm != null ? status
            : problem != null ? problem.copy().withStyle(ChatFormatting.GOLD)
            : !quiet ? status
            : help();
        return new StatusLine(line, quiet);
    }

    private Component help() {
        ThreadCanvas canvas = canvas();
        if (canvas == null) {
            return Component.translatable("gui.hexwright.hex_loom.help_weave");
        }
        ThreadEntry carried = canvas.carriedNext();
        if (carried != null) {
            return Component.translatable("gui.hexwright.hex_loom.carrying", canvas.carriedIndex(), canvas.carriedTotal(),
                EmbedKnot.name(carried));
        }
        int point = canvas.insertionPoint();
        return point < canvas.draft().size()
            ? Component.translatable("gui.hexwright.hex_loom.help_insert", point + 1)
            : Component.translatable("gui.hexwright.hex_loom.help");
    }

    private @Nullable Component problem() {
        int edits = weave.edits();
        if (edits != problemEdits) {
            problemEdits = edits;
            problem = findProblem();
        }
        return problem;
    }

    private @Nullable Component findProblem() {
        if (weave.threads().isEmpty()) {
            return Component.translatable("gui.hexwright.hex_loom.empty_weave");
        }
        if (weave.triggerLinks().isEmpty()) {
            return Component.translatable("gui.hexwright.hex_loom.no_trigger");
        }
        for (ThreadDraft thread : weave.threads()) {
            int outside = thread.outOfBounds().size();
            if (outside > 0) {
                return Component.translatable("gui.hexwright.hex_loom.outside", thread.id(), outside);
            }
        }
        return null;
    }

    void setStatus(Component message) {
        status = message;
    }


    public void press(LoomAction action) {
        if (transition != null || !active(action)) {
            return;
        }
        switch (action) {
            case ADD -> addThread();
            case ADD_TRIGGER -> addTrigger();
            case OPEN -> openSelected();
            case DELETE -> deleteSelected();
            case UNLINK -> unlinkSelected();
            case FIT -> fit();
            case ZOOM_IN -> zoom(1);
            case ZOOM_OUT -> zoom(-1);
            case BACK -> back();
            case UNDO -> undo();
            case REDO -> redo();
            case REMOVE -> removeSelectedPattern();
            case CLEAR -> clearThread();
            case COPY -> openPicker();
            case SAVE -> save();
            case CLOSE -> requestClose();
        }
    }

    private void addThread() {
        ThreadDraft added = weave.addThread(overview.spotForNewThread());
        if (added == null) {
            setStatus(Component.translatable("gui.hexwright.hex_loom.full", Hexweave.MAX_THREADS)
                .withStyle(ChatFormatting.GOLD));
            return;
        }
        overview.select(added.id());
        setStatus(Component.translatable("gui.hexwright.hex_loom.added", added.id()));
    }

    private void addTrigger() {
        Integer thread = overview.selectedThread();
        Integer added = weave.addTrigger(overview.spotForNewTrigger());
        if (added == null) {
            setStatus(Component.translatable("gui.hexwright.hex_loom.full_triggers", Hexweave.MAX_TRIGGERS)
                .withStyle(ChatFormatting.GOLD));
            return;
        }
        Component kind = WeaveOverview.triggerKind(weave, added);
        overview.selectTrigger(added);
        if (thread != null && weave.linkTrigger(added, thread)) {
            setStatus(Component.translatable("gui.hexwright.hex_loom.added_trigger_linked", added, kind, thread));
        } else {
            setStatus(Component.translatable("gui.hexwright.hex_loom.added_trigger", added, kind));
        }
    }

    private void openSelected() {
        Integer thread = overview.selectedThread();
        Integer trigger = overview.selectedTrigger();
        if (thread != null) {
            openThread(thread);
        } else if (trigger != null) {
            overview.openPicker(trigger);
        }
    }

    private void openThread(int id) {
        ThreadDraft thread = weave.thread(id);
        if (transition != null || editing != null || thread == null) {
            return;
        }
        overview.cancel();
        overview.select(id);
        ThreadCanvas canvas = canvases.computeIfAbsent(thread, key -> new ThreadCanvas(weave, key, this::setStatus));
        place(canvas);
        editing = id;
        confirm = null;
        status = Component.empty();
        transition = new Transition(id, true, Util.getMillis());
    }

    private void back() {
        if (editing == null || transition != null) {
            return;
        }
        ThreadCanvas canvas = canvas();
        if (canvas != null) {
            canvas.cancelStroke();
            canvas.stopCarrying();
        }
        picker = null;
        confirm = null;
        status = Component.empty();
        transition = new Transition(editing, false, Util.getMillis());
    }

    private void zoom(int direction) {
        ThreadCanvas canvas = canvas();
        if (canvas == null) {
            if (direction > 0 && overview.atClosest()) {
                openSelected();
            } else {
                overview.zoomAboutCentre(direction);
            }
        } else if (direction < 0 && canvas.atWidestZoom()) {
            back();
        } else {
            canvas.zoomAboutCentre(direction);
        }
    }

    private void fit() {
        ThreadCanvas canvas = canvas();
        if (canvas == null) {
            overview.fitAll();
        } else {
            canvas.resetView();
        }
    }

    private void deleteSelected() {
        if (linkSelected()) {
            unlinkSelected();
            return;
        }
        Integer trigger = overview.selectedTrigger();
        if (trigger != null) {
            int links = weave.triggerLinksFrom(trigger).size();
            Runnable remove = () -> {
                weave.removeTrigger(trigger);
                forgetNumbers();
                setStatus(Component.translatable("gui.hexwright.hex_loom.deleted_trigger", trigger));
            };
            if (links == 0) {
                remove.run();
            } else {
                confirmThen(LoomAction.DELETE, Component.translatable("gui.hexwright.hex_loom.confirm_delete_trigger",
                    trigger, links), remove);
            }
            return;
        }
        Integer id = overview.selectedThread();
        ThreadDraft thread = id == null ? null : weave.thread(id);
        if (thread == null) {
            return;
        }
        long links = weave.links().stream().filter(link -> link.from() == id || link.to() == id).count()
            + weave.triggerLinksInto(id).size();
        if (thread.size() == 0 && links == 0) {
            removeThread(id);
            return;
        }
        confirmThen(LoomAction.DELETE, Component.translatable("gui.hexwright.hex_loom.confirm_delete", id,
            thread.size(), links), () -> removeThread(id));
    }

    private void removeThread(int id) {
        canvases.remove(weave.thread(id));
        weave.removeThread(id);
        forgetNumbers();
        setStatus(Component.translatable("gui.hexwright.hex_loom.deleted", id));
    }

    private void forgetNumbers() {
        overview.cancel();
        overview.clearSelection();
    }

    private void unlinkSelected() {
        WeaveLink link = overview.selectedLink();
        TriggerLink started = overview.selectedTriggerLink();
        if (link != null && weave.unlink(link)) {
            overview.clearSelection();
            setStatus(Component.translatable("gui.hexwright.hex_loom.unlinked", link.from(), link.to()));
        } else if (started != null && weave.unlink(started)) {
            overview.clearSelection();
            setStatus(Component.translatable("gui.hexwright.hex_loom.unlinked_trigger", started.trigger(),
                started.thread()));
        }
    }

    private void confirmThen(LoomAction action, Component question, Runnable then) {
        if (confirm != null && confirm.action() == action && ticks <= confirm.until()) {
            confirm = null;
            then.run();
        } else {
            confirm = new Confirm(action, ticks + CONFIRM_TICKS);
            setStatus(question.copy().withStyle(ChatFormatting.GOLD));
        }
    }

    private void requestClose() {
        if (pos != null && history.differsFrom(saved)) {
            confirmThen(LoomAction.CLOSE, Component.translatable("gui.hexwright.hex_loom.confirm_close"), closer);
        } else {
            closer.run();
        }
    }

    private void undo() {
        commit();
        ThreadCanvas open = canvas();
        LoomHistory.Step step = history.undo();
        if (step != null) {
            restored(open, step, "gui.hexwright.hex_loom.undone");
        }
    }

    private void redo() {
        commit();
        ThreadCanvas open = canvas();
        LoomHistory.Step step = history.redo();
        if (step != null) {
            restored(open, step, "gui.hexwright.hex_loom.redone");
        }
    }

    private void restored(@Nullable ThreadCanvas open, LoomHistory.Step step, String message) {
        for (ThreadCanvas canvas : canvases.values()) {
            canvas.restored(step.insertion().containsKey(canvas) ? step.insertion().get(canvas) : null);
        }
        forgetNumbers();
        if (open != null) {
            ThreadDraft thread = open.draft();
            editing = weave.thread(thread.id()) == thread ? thread.id() : null;
            if (editing != null) {
                overview.select(editing);
            }
        }
        confirm = null;
        setStatus(Component.translatable(message));
    }

    private void removeSelectedPattern() {
        ThreadCanvas canvas = canvas();
        if (canvas != null) {
            canvas.removeSelected();
        }
    }

    private void clearThread() {
        ThreadCanvas canvas = canvas();
        if (canvas != null) {
            canvas.clear();
        }
    }


    private void openPicker() {
        LocalPlayer player = Minecraft.getInstance().player;
        picker = player == null ? null : CopyPicker.of(player);
        if (picker == null) {
            setStatus(Component.translatable("gui.hexwright.hex_loom.nothing_to_copy"));
        }
    }

    private void copy(CopyPicker.Source source, boolean byHand) {
        picker = null;
        ThreadCanvas canvas = canvas();
        if (canvas == null) {
            return;
        }
        List<ThreadEntry> entries = source.entries();
        if (!byHand) {
            List<ThreadEntry> placed = canvas.draft().fit(entries);
            if (placed != null && canvas.insertRun(placed)) {
                setStatus(Component.translatable("gui.hexwright.hex_loom.copied", entries.size(),
                    source.stack().getHoverName()));
                return;
            }
            setStatus(Component.translatable("gui.hexwright.hex_loom.copy_no_room", entries.size())
                .withStyle(ChatFormatting.GOLD));
        }
        canvas.carry(entries);
    }


    private void save() {
        CompoundTag weaveTag;
        try {
            weaveTag = HexweaveNbt.write(weave.toWeave(), weave.layout());
        } catch (IllegalArgumentException e) {
            setStatus(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
            return;
        }
        byte[] packed = LoomNetworking.pack(weaveTag);
        if (packed.length > LoomNetworking.MAX_SAVE_BYTES) {
            setStatus(Component.translatable("gui.hexwright.hex_loom.too_large", (packed.length + 1023) / 1024,
                LoomNetworking.MAX_SAVE_BYTES / 1024).withStyle(ChatFormatting.RED));
            return;
        }
        waiting = true;
        commit();
        sent = history.current();
        setStatus(Component.translatable("gui.hexwright.hex_loom.saving").withStyle(ChatFormatting.GRAY));
        LoomClient.sendSave(pos, revision, packed);
    }

    void onSaveResult(boolean saved, int newRevision, Component message) {
        waiting = false;
        if (saved && sent != null) {
            revision = newRevision;
            this.saved = sent;
        }
        setStatus(message);
    }


    public void render(GuiGraphics graphics, int mouseX, int mouseY, LoomPalette palette, ThreadTimeline.Look look,
                       LoomMenu.Look menus) {
        ThreadCanvas canvas = canvas();
        if (transition != null) {
            renderTransition(graphics, palette, look, menus);
        } else if (canvas != null) {
            canvas.render(graphics, mouseX, mouseY, palette);
            timeline.render(graphics, canvas, mouseX, mouseY, palette, look);
            drawArmatureNotes(graphics, canvas, editing, palette);
            if (picker != null) {
                picker.place(left, top, right, bottom - timelineHeight);
                picker.render(graphics, mouseX, mouseY, palette, menus);
            }
        } else {
            overview.render(graphics, mouseX, mouseY, palette, menus);
        }
    }

    public @Nullable List<Component> hover(int mouseX, int mouseY) {
        if (transition != null) {
            return null;
        }
        ThreadCanvas canvas = canvas();
        if (canvas == null) {
            return overview.hover(mouseX, mouseY);
        }
        if (picker != null && picker.contains(mouseX, mouseY)) {
            return null;
        }
        return timeline.contains(mouseX, mouseY) ? timeline.hover(canvas, mouseX, mouseY) : canvas.hover(mouseX, mouseY);
    }

    private void renderTransition(GuiGraphics graphics, LoomPalette palette, ThreadTimeline.Look look,
                                  LoomMenu.Look menus) {
        Transition current = transition;
        float linear = Mth.clamp((Util.getMillis() - current.started()) / (float) TRANSITION_MS, 0f, 1f);
        float eased = linear * linear * (3 - 2 * linear);
        float progress = current.opening() ? eased : 1 - eased;
        ThreadCanvas canvas = canvases.get(weave.thread(current.thread()));

        if (canvas != null && weave.thread(current.thread()) != null) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f - progress);
            overview.render(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, palette, menus);

            Vec2 fragment = overview.fragmentCentre(current.thread());
            Vec2 bound = canvas.toScreen(HexCoord.getOrigin());
            double fromScale = Math.log(overview.scale() / canvas.spacing());
            float scale = (float) Math.exp(Mth.lerp(progress, fromScale, 0));
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(Mth.lerp(progress, fragment.x, bound.x), Mth.lerp(progress, fragment.y, bound.y), 20);
            pose.scale(scale, scale, 1f);
            pose.translate(-bound.x, -bound.y, 0);
            RenderSystem.setShaderColor(1f, 1f, 1f, progress);
            canvas.render(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, palette);
            pose.popPose();
            timeline.render(graphics, canvas, Integer.MIN_VALUE, Integer.MIN_VALUE, palette, look);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        if (linear >= 1f) {
            if (!current.opening()) {
                editing = null;
            }
            transition = null;
        }
    }

    private void drawArmatureNotes(GuiGraphics graphics, ThreadCanvas canvas, int id, LoomPalette palette) {
        if (weave.thread(id) == null) {
            return;
        }
        boolean entered = weave.entered(id);
        List<WeaveLink> out = weave.linksFrom(id);
        graphics.enableScissor(left, top, right, bottom);
        drawNote(graphics, canvas, -1, entered
                ? Component.translatable("gui.hexwright.hex_loom.note.from", WeaveOverview.entrants(weave, id))
                : Component.translatable("gui.hexwright.hex_loom.note.from_none"),
            (entered ? 0xB0000000 : 0x70000000) | palette.eye());
        drawNote(graphics, canvas, 1, out.isEmpty()
                ? Component.translatable("gui.hexwright.hex_loom.note.to_none")
                : Component.translatable("gui.hexwright.hex_loom.note.to", WeaveOverview.ids(out, false)),
            (out.isEmpty() ? 0x70000000 : 0xB0000000) | palette.tail());
        graphics.disableScissor();
    }

    private static void drawNote(GuiGraphics graphics, ThreadCanvas canvas, int side, Component text, int colour) {
        Font font = Minecraft.getInstance().font;
        Vec2 tip = canvas.frameTip(side);
        int x = side < 0 ? (int) tip.x - 4 - font.width(text) : (int) tip.x + 4;
        graphics.drawString(font, text, x, (int) tip.y + 1, colour);
    }


    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition != null) {
            return true;
        }
        if (picker != null) {
            CopyPicker.Source source = picker.sourceAt(mouseX, mouseY);
            if (source != null && button == 0) {
                copy(source, Screen.hasShiftDown());
            } else if (!picker.contains(mouseX, mouseY)) {
                picker = null;
            }
            return true;
        }
        Component before = status;
        status = Component.empty();
        ThreadCanvas canvas = canvas();
        boolean handled = canvas != null
            ? timeline.mouseClicked(canvas, mouseX, mouseY, button) || canvas.mouseClicked(mouseX, mouseY, button)
            : overview.mouseClicked(mouseX, mouseY, button);
        if (!handled) {
            status = before;
        }
        return handled;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (transition != null) {
            return true;
        }
        ThreadCanvas canvas = canvas();
        return canvas != null ? canvas.mouseDragged(mouseX, mouseY, button, dragX, dragY)
            : overview.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (transition != null) {
            return true;
        }
        ThreadCanvas canvas = canvas();
        return canvas != null ? canvas.mouseReleased(mouseX, mouseY, button)
            : overview.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (transition != null) {
            return true;
        }
        ThreadCanvas canvas = canvas();
        if (canvas != null) {
            if (timeline.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
            if (delta < 0 && canvas.atWidestZoom() && canvas.contains(mouseX, mouseY) && !canvas.drawing()) {
                back();
                return true;
            }
            return canvas.mouseScrolled(mouseX, mouseY, delta);
        }
        return overview.mouseScrolled(mouseX, mouseY, delta);
    }

    private static int reachKey(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            return keyCode - GLFW.GLFW_KEY_0;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_5) {
            return keyCode - GLFW.GLFW_KEY_KP_0;
        }
        return 0;
    }

    private static boolean moveInsertionPoint(ThreadCanvas canvas, int keyCode) {
        int point = canvas.insertionPoint();
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> canvas.setInsertionPoint(point - 1);
            case GLFW.GLFW_KEY_RIGHT -> canvas.setInsertionPoint(point + 1);
            case GLFW.GLFW_KEY_HOME -> canvas.setInsertionPoint(0);
            case GLFW.GLFW_KEY_END -> canvas.setInsertionPoint(canvas.draft().size());
            default -> {
                return false;
            }
        }
        return true;
    }

    boolean capturing() {
        return overview.capturing();
    }

    void captureMouse(int button) {
        if (button > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            overview.capture(InputConstants.Type.MOUSE.getOrCreate(button));
        } else {
            overview.cancel();
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (transition != null) {
            return true;
        }
        ThreadCanvas canvas = canvas();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (picker != null) {
                picker = null;
            } else if (canvas != null) {
                if (canvas.drawing()) {
                    canvas.cancelStroke();
                } else if (canvas.carrying()) {
                    setStatus(Component.translatable("gui.hexwright.hex_loom.carry_stopped", canvas.stopCarrying()));
                } else {
                    back();
                }
            } else if (overview.busy()) {
                overview.cancel();
            } else {
                requestClose();
            }
            return true;
        }
        if (overview.capturing()) {
            overview.capture(InputConstants.getKey(keyCode, scanCode));
            return true;
        }
        if (overview.choosingReach() && reachKey(keyCode) > 0) {
            overview.chooseReach(reachKey(keyCode));
            return true;
        }
        if (Screen.hasControlDown() && (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_Y)) {
            press(keyCode == GLFW.GLFW_KEY_Y || Screen.hasShiftDown() ? LoomAction.REDO : LoomAction.UNDO);
            return true;
        }
        boolean deleting = keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE;
        if (canvas != null) {
            if (keyCode == GLFW.GLFW_KEY_R && canvas.carrying()) {
                canvas.turnCarried();
                return true;
            }
            if (deleting) {
                removeSelectedPattern();
                return true;
            }
            if (moveInsertionPoint(canvas, keyCode)) {
                return true;
            }
        } else {
            if (deleting) {
                deleteSelected();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                openSelected();
                return true;
            }
        }
        return false;
    }
}

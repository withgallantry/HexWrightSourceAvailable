package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.server.hexweave.WeaveDraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

@Environment(EnvType.CLIENT)
final class LoomHistory {

    private static final int LIMIT = 100;

    private final WeaveDraft weave;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private Step current;
    private int committedEdits;

    record Step(WeaveDraft.Snapshot weave, Map<ThreadCanvas, Integer> insertion) {
    }

    LoomHistory(WeaveDraft weave, Map<ThreadCanvas, Integer> insertion) {
        this.weave = weave;
        this.current = new Step(weave.snapshot(), insertion);
        this.committedEdits = weave.edits();
    }

    Step current() {
        return current;
    }

    boolean differsFrom(Step step) {
        return weave.edits() != committedEdits || current.weave() != step.weave();
    }

    void commit(Map<ThreadCanvas, Integer> insertion) {
        if (weave.edits() == committedEdits) {
            if (!insertion.equals(current.insertion())) {
                current = new Step(current.weave(), insertion);
            }
            return;
        }
        undo.push(current);
        if (undo.size() > LIMIT) {
            undo.removeLast();
        }
        redo.clear();
        current = new Step(weave.snapshot(), insertion);
        committedEdits = weave.edits();
    }

    boolean canUndo() {
        return !undo.isEmpty() || weave.edits() != committedEdits;
    }

    boolean canRedo() {
        return !redo.isEmpty() && weave.edits() == committedEdits;
    }

    @Nullable Step undo() {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current);
        return restore(undo.pop());
    }

    @Nullable Step redo() {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current);
        return restore(redo.pop());
    }

    private Step restore(Step step) {
        current = step;
        weave.restore(step.weave());
        committedEdits = weave.edits();
        return step;
    }
}

package com.bluup.hexwright.server.hexweave;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record WeaveLayout(Map<Integer, Position> positions, Map<Integer, Position> triggers) {

    public static final float EXTENT = 1024f;
    public static final float COLUMN_STEP = 64f;
    public static final float ROW_STEP = 48f;
    public static final float TRIGGER_STEP = 44f;

    public static final float THREAD_HALF_WIDTH = 25f;
    public static final float THREAD_HALF_HEIGHT = 22f;
    public static final float TRIGGER_HALF_WIDTH = 13f;
    public static final float TRIGGER_HALF_HEIGHT = 14f;

    public static final WeaveLayout NONE = new WeaveLayout(Map.of(), Map.of());

    public record Position(float x, float y) {

        public static Position clamped(float x, float y) {
            return new Position(Mth.clamp(x, -EXTENT, EXTENT), Mth.clamp(y, -EXTENT, EXTENT));
        }
    }

    public record Footprint(Position at, float halfWidth, float halfHeight) {

        public static Footprint thread(Position at) {
            return new Footprint(at, THREAD_HALF_WIDTH, THREAD_HALF_HEIGHT);
        }

        public static Footprint trigger(Position at) {
            return new Footprint(at, TRIGGER_HALF_WIDTH, TRIGGER_HALF_HEIGHT);
        }

        boolean overlaps(Footprint other) {
            return Math.abs(at.x() - other.at.x()) < halfWidth + other.halfWidth
                && Math.abs(at.y() - other.at.y()) < halfHeight + other.halfHeight;
        }
    }

    public WeaveLayout {
        positions = Map.copyOf(positions);
        triggers = Map.copyOf(triggers);
    }

    public @Nullable Position position(int id) {
        return positions.get(id);
    }

    public @Nullable Position triggerPosition(int id) {
        return triggers.get(id);
    }

    public WeaveLayout forWeave(Hexweave weave) {
        Map<Integer, Position> threads = new HashMap<>();
        for (WeaveThread thread : weave.threads()) {
            Position at = positions.get(thread.id());
            if (at != null) {
                threads.put(thread.id(), at);
            }
        }
        Map<Integer, Position> kept = new HashMap<>();
        for (WeaveTrigger trigger : weave.triggers()) {
            Position at = triggers.get(trigger.id());
            if (at != null) {
                kept.put(trigger.id(), at);
            }
        }
        return new WeaveLayout(threads, kept);
    }

    public static WeaveLayout arrange(Hexweave weave) {
        Map<Integer, Integer> depth = new HashMap<>();
        Deque<Integer> frontier = new ArrayDeque<>();
        for (WeaveTrigger trigger : weave.triggers()) {
            for (int target : trigger.targets()) {
                if (depth.putIfAbsent(target, 0) == null) {
                    frontier.add(target);
                }
            }
        }
        int deepest = 0;
        while (!frontier.isEmpty()) {
            int id = frontier.poll();
            for (int target : weave.targetsOf(id)) {
                if (!depth.containsKey(target)) {
                    depth.put(target, depth.get(id) + 1);
                    deepest = Math.max(deepest, depth.get(id) + 1);
                    frontier.add(target);
                }
            }
        }

        TreeMap<Integer, List<Integer>> columns = new TreeMap<>();
        for (WeaveThread thread : weave.threads()) {
            int column = depth.getOrDefault(thread.id(), depth.isEmpty() ? 0 : deepest + 1);
            columns.computeIfAbsent(column, c -> new ArrayList<>()).add(thread.id());
        }
        Map<Integer, Position> threads = new HashMap<>();
        columns.forEach((column, ids) -> {
            for (int row = 0; row < ids.size(); row++) {
                threads.put(ids.get(row), Position.clamped(column * COLUMN_STEP,
                    (row - (ids.size() - 1) / 2f) * ROW_STEP));
            }
        });

        Map<Integer, Position> triggers = new HashMap<>();
        List<Footprint> placed = new ArrayList<>(threads.values().stream().map(Footprint::thread).toList());
        int idle = 0;
        for (WeaveTrigger trigger : weave.triggers()) {
            Position first = trigger.targets().isEmpty() ? null : threads.get(trigger.targets().get(0));
            Position start = first != null ? new Position(first.x() - TRIGGER_STEP, first.y())
                : new Position(-TRIGGER_STEP - COLUMN_STEP, idle++ * ROW_STEP);
            Footprint at = freeSpot(placed, Footprint.trigger(start));
            triggers.put(trigger.id(), at.at());
            placed.add(at);
        }
        return new WeaveLayout(threads, triggers);
    }

    public static Footprint freeSpot(Collection<Footprint> taken, Footprint wanted) {
        Position start = wanted.at();
        for (int column = 0; column < 4; column++) {
            for (int step = 0; step < 16; step++) {
                int row = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
                Footprint candidate = new Footprint(Position.clamped(start.x() + column * COLUMN_STEP,
                    start.y() + row * (2 * wanted.halfHeight() + 4)), wanted.halfWidth(), wanted.halfHeight());
                if (taken.stream().noneMatch(candidate::overlaps)) {
                    return candidate;
                }
            }
        }
        return new Footprint(Position.clamped(start.x(), start.y()), wanted.halfWidth(), wanted.halfHeight());
    }
}

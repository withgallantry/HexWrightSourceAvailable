package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ThreadDraft {

    public static final int RADIUS = 10;

    private static final Comparator<HexCoord> READING =
        Comparator.comparingInt(HexCoord::getR).thenComparingInt(HexCoord::getQ);
    private static final List<HexCoord> READING_ORDER = readingOrder();

    private int id;
    private final List<ThreadEntry> entries = new ArrayList<>();
    private final Map<HexCoord, Integer> owners = new HashMap<>();
    private @Nullable List<ThreadEntry> frozen;
    private int edits;

    private ThreadDraft(int id) {
        this.id = id;
    }

    static ThreadDraft empty(int id) {
        return new ThreadDraft(id);
    }

    public static ThreadDraft of(WeaveThread thread) {
        ThreadDraft draft = new ThreadDraft(thread.id());
        draft.entries.addAll(thread.entries());
        draft.reindex();
        return draft;
    }

    public static boolean inBounds(HexCoord point) {
        return point.distanceTo(HexCoord.getOrigin()) <= RADIUS;
    }

    public static boolean inBounds(ThreadEntry entry) {
        for (HexCoord point : entry.points()) {
            if (!inBounds(point)) {
                return false;
            }
        }
        return true;
    }

    public int id() {
        return id;
    }

    void renumber(int id) {
        this.id = id;
    }

    public int edits() {
        return edits;
    }

    public List<ThreadEntry> entries() {
        if (frozen == null) {
            frozen = List.copyOf(entries);
        }
        return frozen;
    }

    public int size() {
        return entries.size();
    }

    public boolean occupied(HexCoord point) {
        return owners.containsKey(point);
    }

    public @Nullable Integer entryAt(HexCoord point) {
        return owners.get(point);
    }

    public boolean accepts(ThreadEntry entry) {
        return entries.size() < Hexweave.MAX_PATTERNS_PER_THREAD && fits(entry, owners.keySet(), false);
    }

    public boolean add(HexPattern pattern, HexCoord origin) {
        return insert(entries.size(), new PlacedPattern(pattern, origin));
    }

    public boolean insert(int index, ThreadEntry entry) {
        if (index < 0 || index > entries.size() || !accepts(entry)) {
            return false;
        }
        entries.add(index, entry);
        reindex();
        changed();
        return true;
    }

    public boolean insertAll(int index, List<ThreadEntry> run) {
        if (index < 0 || index > entries.size() || entries.size() + run.size() > Hexweave.MAX_PATTERNS_PER_THREAD) {
            return false;
        }
        Set<HexCoord> used = new HashSet<>(owners.keySet());
        for (ThreadEntry entry : run) {
            if (!fits(entry, used, false)) {
                return false;
            }
            used.addAll(entry.points());
        }
        entries.addAll(index, run);
        reindex();
        changed();
        return true;
    }

    public void remove(int index) {
        entries.remove(index);
        reindex();
        changed();
    }

    public void clear() {
        entries.clear();
        owners.clear();
        changed();
    }

    public @Nullable List<ThreadEntry> fit(List<ThreadEntry> run) {
        if (entries.size() + run.size() > Hexweave.MAX_PATTERNS_PER_THREAD) {
            return null;
        }
        Set<HexCoord> used = new HashSet<>(owners.keySet());
        List<ThreadEntry> placed = new ArrayList<>(run.size());
        for (ThreadEntry entry : run) {
            ThreadEntry at = firstPlace(entry, used, true);
            if (at == null) {
                at = firstPlace(entry, used, false);
            }
            if (at == null) {
                return null;
            }
            placed.add(at);
            used.addAll(at.points());
        }
        return placed;
    }

    public List<Integer> outOfBounds() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!inBounds(entries.get(i))) {
                out.add(i);
            }
        }
        return out;
    }

    public WeaveThread toThread() {
        return new WeaveThread(id, entries);
    }

    void restore(int id, List<ThreadEntry> entries) {
        this.id = id;
        this.entries.clear();
        this.entries.addAll(entries);
        reindex();
        changed();
    }

    private void changed() {
        frozen = null;
        edits++;
    }

    private void reindex() {
        owners.clear();
        for (int i = 0; i < entries.size(); i++) {
            for (HexCoord point : entries.get(i).points()) {
                owners.putIfAbsent(point, i);
            }
        }
    }

    private static boolean fits(ThreadEntry entry, Set<HexCoord> used, boolean spaced) {
        if (entry instanceof PlacedPattern placed && !drawable(placed.pattern())) {
            return false;
        }
        for (HexCoord point : entry.points()) {
            if (!inBounds(point) || used.contains(point)) {
                return false;
            }
            if (spaced) {
                if (point.distanceTo(HexCoord.getOrigin()) >= RADIUS) {
                    return false;
                }
                for (HexDir dir : HexDir.values()) {
                    if (used.contains(point.plus(dir))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean drawable(HexPattern pattern) {
        if (pattern.getAngles().size() > Hexweave.MAX_PATTERN_LENGTH) {
            return false;
        }
        try {
            HexPattern.fromAngles(pattern.anglesSignature(), pattern.getStartDir());
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    private static @Nullable ThreadEntry firstPlace(ThreadEntry entry, Set<HexCoord> used, boolean spaced) {
        List<ThreadEntry> shapes = new ArrayList<>();
        if (entry instanceof PlacedPattern placed) {
            for (HexAngle turn : HexAngle.values()) {
                HexPattern pattern = placed.pattern();
                shapes.add(new PlacedPattern(new HexPattern(pattern.getStartDir().rotatedBy(turn),
                    new ArrayList<>(pattern.getAngles())), HexCoord.getOrigin()));
            }
        } else {
            shapes.add(entry.at(HexCoord.getOrigin()));
        }
        List<HexCoord> leads = new ArrayList<>(shapes.size());
        for (ThreadEntry shape : shapes) {
            leads.add(shape.points().stream().min(READING).orElse(HexCoord.getOrigin()));
        }
        for (HexCoord place : READING_ORDER) {
            for (int i = 0; i < shapes.size(); i++) {
                ThreadEntry candidate = shapes.get(i).at(place.minus(leads.get(i)));
                if (fits(candidate, used, spaced)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static List<HexCoord> readingOrder() {
        List<HexCoord> points = new ArrayList<>();
        for (int q = -RADIUS; q <= RADIUS; q++) {
            for (int r = Math.max(-RADIUS, -q - RADIUS); r <= Math.min(RADIUS, -q + RADIUS); r++) {
                points.add(new HexCoord(q, r));
            }
        }
        points.sort(READING);
        return List.copyOf(points);
    }
}

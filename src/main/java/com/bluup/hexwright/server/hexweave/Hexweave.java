package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

public final class Hexweave {

    public static final int MAX_THREADS = 64;
    public static final int MAX_TRIGGERS = 16;
    public static final int MAX_LINKS = 256;
    public static final int MAX_PATTERNS_PER_THREAD = 1024;
    public static final int MAX_PATTERNS = 4096;
    public static final int MAX_PATTERN_LENGTH = 256;
    public static final int MAX_ORIGIN = 65536;

    private final Map<Integer, WeaveThread> threads;
    private final List<WeaveLink> links;
    private final Map<Integer, WeaveTrigger> triggers;
    private final Map<Integer, List<Integer>> targets;

    private Hexweave(Map<Integer, WeaveThread> threads, List<WeaveLink> links, Map<Integer, WeaveTrigger> triggers,
                     Map<Integer, List<Integer>> targets) {
        this.threads = threads;
        this.links = links;
        this.triggers = triggers;
        this.targets = targets;
    }

    public static Hexweave of(Collection<WeaveThread> threads, Collection<WeaveLink> links,
                              Collection<WeaveTrigger> triggers) {
        if (threads.size() > MAX_THREADS) {
            throw new IllegalArgumentException(threads.size() + " Threads; a weave holds at most " + MAX_THREADS + ".");
        }
        if (triggers.size() > MAX_TRIGGERS) {
            throw new IllegalArgumentException(triggers.size() + " triggers; a weave holds at most " + MAX_TRIGGERS + ".");
        }
        int linkCount = links.size() + triggers.stream().mapToInt(trigger -> trigger.targets().size()).sum();
        if (linkCount > MAX_LINKS) {
            throw new IllegalArgumentException(linkCount + " links; a weave holds at most " + MAX_LINKS + ".");
        }
        int entries = 0;
        for (WeaveThread thread : threads) {
            entries += thread.entries().size();
            if (thread.entries().size() > MAX_PATTERNS_PER_THREAD || entries > MAX_PATTERNS) {
                throw new IllegalArgumentException("Too many entries: a Thread holds at most "
                    + MAX_PATTERNS_PER_THREAD + " and a weave " + MAX_PATTERNS + ".");
            }
        }

        TreeMap<Integer, WeaveThread> byId = new TreeMap<>();
        for (WeaveThread thread : threads) {
            if (byId.putIfAbsent(thread.id(), thread) != null) {
                throw new IllegalArgumentException("Two Threads share the id " + thread.id() + ".");
            }
            checkGeometry(thread);
        }

        Set<WeaveLink> seen = new HashSet<>();
        Map<Integer, TreeSet<Integer>> targetSets = new HashMap<>();
        for (WeaveLink link : links) {
            if (!byId.containsKey(link.from()) || !byId.containsKey(link.to())) {
                throw new IllegalArgumentException("The link " + link.from() + ">" + link.to() + " names Thread "
                    + (byId.containsKey(link.from()) ? link.to() : link.from()) + ", which is not in the weave.");
            }
            if (!seen.add(link)) {
                throw new IllegalArgumentException("The link " + link.from() + ">" + link.to() + " appears twice.");
            }
            targetSets.computeIfAbsent(link.from(), id -> new TreeSet<>()).add(link.to());
        }

        TreeMap<Integer, WeaveTrigger> triggersById = new TreeMap<>();
        for (WeaveTrigger trigger : triggers) {
            Objects.requireNonNull(trigger.kind());
            if (trigger.kind().keyed() == trigger.key().isEmpty() || trigger.key().length() > WeaveTrigger.MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("Trigger " + trigger.id() + " (" + trigger.kind().id() + ") "
                    + (trigger.kind().keyed() ? "needs a key of at most " + WeaveTrigger.MAX_KEY_LENGTH
                    + " characters." : "takes no key."));
            }
            boolean reachOk = trigger.kind().ranged()
                ? trigger.reach() >= WeaveTrigger.MIN_REACH && trigger.reach() <= WeaveTrigger.MAX_REACH
                : trigger.reach() == 0;
            if (!reachOk) {
                throw new IllegalArgumentException("Trigger " + trigger.id() + " (" + trigger.kind().id() + ") "
                    + (trigger.kind().ranged() ? "needs a reach of " + WeaveTrigger.MIN_REACH + " to "
                    + WeaveTrigger.MAX_REACH + " blocks." : "has no reach."));
            }
            TreeSet<Integer> started = new TreeSet<>();
            for (int target : trigger.targets()) {
                if (!byId.containsKey(target)) {
                    throw new IllegalArgumentException("Trigger " + trigger.id() + " starts Thread " + target
                        + ", which is not in the weave.");
                }
                if (!started.add(target)) {
                    throw new IllegalArgumentException("Trigger " + trigger.id() + " starts Thread " + target
                        + " twice.");
                }
            }
            WeaveTrigger sorted = new WeaveTrigger(trigger.id(), trigger.kind(), trigger.key(), trigger.reach(),
                List.copyOf(started));
            if (triggersById.putIfAbsent(trigger.id(), sorted) != null) {
                throw new IllegalArgumentException("Two triggers share the id " + trigger.id() + ".");
            }
        }

        Map<Integer, List<Integer>> targets = new HashMap<>();
        targetSets.forEach((id, set) -> targets.put(id, List.copyOf(set)));

        return new Hexweave(Collections.unmodifiableSortedMap(byId), List.copyOf(links),
            Collections.unmodifiableSortedMap(triggersById), Map.copyOf(targets));
    }

    public Collection<WeaveThread> threads() {
        return threads.values();
    }

    public List<WeaveLink> links() {
        return links;
    }

    public Collection<WeaveTrigger> triggers() {
        return triggers.values();
    }

    public WeaveThread thread(int id) {
        WeaveThread thread = threads.get(id);
        if (thread == null) {
            throw new IllegalArgumentException("No Thread " + id + " in this weave.");
        }
        return thread;
    }

    public List<Integer> starts(WeaveTrigger.Kind kind) {
        return starts(trigger -> trigger.kind() == kind);
    }

    public List<Integer> starts(WeaveTrigger.Kind kind, String key) {
        return starts(trigger -> trigger.kind() == kind && trigger.key().equals(key));
    }

    public List<Integer> starts(WeaveTrigger.Kind kind, int reach) {
        return starts(trigger -> trigger.kind() == kind && trigger.reach() == reach);
    }

    private List<Integer> starts(Predicate<WeaveTrigger> which) {
        List<Integer> starts = new ArrayList<>();
        for (WeaveTrigger trigger : triggers.values()) {
            if (which.test(trigger)) {
                starts.addAll(trigger.targets());
            }
        }
        return starts;
    }

    public List<Integer> targetsOf(int id) {
        return targets.getOrDefault(id, List.of());
    }

    private static void checkGeometry(WeaveThread thread) {
        Set<HexCoord> used = new HashSet<>();
        for (int i = 0; i < thread.entries().size(); i++) {
            ThreadEntry entry = thread.entries().get(i);
            HexCoord origin = entry.origin();
            String where = "Entry " + (i + 1) + " of Thread " + thread.id();
            if (Math.abs(origin.getQ()) > MAX_ORIGIN || Math.abs(origin.getR()) > MAX_ORIGIN) {
                throw new IllegalArgumentException(where + " sits at " + origin.getQ() + ", " + origin.getR()
                    + ", beyond " + MAX_ORIGIN + " from the Thread's centre.");
            }
            if (entry instanceof PlacedPattern placed) {
                HexPattern pattern = placed.pattern();
                if (pattern.getAngles().size() > MAX_PATTERN_LENGTH) {
                    throw new IllegalArgumentException(where + " has " + pattern.getAngles().size()
                        + " angles; the limit is " + MAX_PATTERN_LENGTH + ".");
                }
                try {
                    HexPattern.fromAngles(pattern.anglesSignature(), pattern.getStartDir());
                } catch (IllegalArgumentException | IllegalStateException e) {
                    throw new IllegalArgumentException(where + " is not a pattern Hex can draw: " + e.getMessage());
                }
            } else if (entry instanceof EmbeddedIota embedded
                && !embedded.iota().contains(HexIotaTypes.KEY_TYPE, Tag.TAG_STRING)) {
                throw new IllegalArgumentException(where + " embeds something that isn't an iota.");
            }
            List<HexCoord> points = entry.points();
            for (HexCoord point : points) {
                if (used.contains(point)) {
                    throw new IllegalArgumentException(where + " crosses another entry at "
                        + point.getQ() + ", " + point.getR() + ".");
                }
            }
            used.addAll(points);
        }
    }
}

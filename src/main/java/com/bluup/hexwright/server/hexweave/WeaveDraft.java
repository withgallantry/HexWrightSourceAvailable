package com.bluup.hexwright.server.hexweave;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class WeaveDraft {

    public enum LinkCheck { OK, MISSING, DUPLICATE, FULL }

    public record Snapshot(List<ThreadState> threads, List<WeaveLink> links, Map<Integer, WeaveTrigger.Kind> triggers,
                           Map<Integer, String> keys, Map<Integer, Integer> reaches, List<TriggerLink> triggerLinks,
                           Map<Integer, WeaveLayout.Position> triggerPositions, long nextId, long nextTriggerId) {
    }

    private record ThreadState(ThreadDraft thread, int id, List<ThreadEntry> entries, WeaveLayout.Position position) {
    }

    private final TreeMap<Integer, ThreadDraft> threads = new TreeMap<>();
    private final List<WeaveLink> links = new ArrayList<>();
    private final TreeMap<Integer, WeaveTrigger.Kind> triggers = new TreeMap<>();
    private final Map<Integer, String> keys = new HashMap<>();
    private final Map<Integer, Integer> reaches = new HashMap<>();
    private final List<TriggerLink> triggerLinks = new ArrayList<>();
    private final Map<Integer, WeaveLayout.Position> positions = new HashMap<>();
    private final Map<Integer, WeaveLayout.Position> triggerPositions = new HashMap<>();
    private long nextId = 1;
    private long nextTriggerId = 1;
    private int edits;

    private WeaveDraft() {
    }

    public static WeaveDraft blank() {
        WeaveDraft draft = new WeaveDraft();
        ThreadDraft thread = draft.addThread(new WeaveLayout.Position(0, 0));
        Integer trigger = draft.addTrigger(new WeaveLayout.Position(-WeaveLayout.TRIGGER_STEP, 0));
        if (thread != null && trigger != null) {
            draft.linkTrigger(trigger, thread.id());
        }
        draft.edits = 0;
        return draft;
    }

    public static WeaveDraft of(Hexweave weave, WeaveLayout layout) {
        WeaveDraft draft = new WeaveDraft();
        WeaveLayout arranged = null;
        for (WeaveThread thread : weave.threads()) {
            draft.threads.put(thread.id(), ThreadDraft.of(thread));
            WeaveLayout.Position at = layout.position(thread.id());
            if (at == null) {
                arranged = arranged == null ? WeaveLayout.arrange(weave) : arranged;
                at = arranged.position(thread.id());
            }
            draft.positions.put(thread.id(), at);
            draft.nextId = Math.max(draft.nextId, thread.id() + 1L);
        }
        draft.links.addAll(weave.links());
        for (WeaveTrigger trigger : weave.triggers()) {
            draft.triggers.put(trigger.id(), trigger.kind());
            if (!trigger.key().isEmpty()) {
                draft.keys.put(trigger.id(), trigger.key());
            }
            if (trigger.kind().ranged()) {
                draft.reaches.put(trigger.id(), trigger.reach());
            }
            for (int target : trigger.targets()) {
                draft.triggerLinks.add(new TriggerLink(trigger.id(), target));
            }
            WeaveLayout.Position at = layout.triggerPosition(trigger.id());
            if (at == null) {
                arranged = arranged == null ? WeaveLayout.arrange(weave) : arranged;
                at = arranged.triggerPosition(trigger.id());
            }
            draft.triggerPositions.put(trigger.id(), at);
            draft.nextTriggerId = Math.max(draft.nextTriggerId, trigger.id() + 1L);
        }
        return draft;
    }

    public Collection<ThreadDraft> threads() {
        return Collections.unmodifiableCollection(threads.values());
    }

    public @Nullable ThreadDraft thread(int id) {
        return threads.get(id);
    }

    public List<WeaveLink> links() {
        return Collections.unmodifiableList(links);
    }

    public List<WeaveLink> linksFrom(int id) {
        return links.stream().filter(link -> link.from() == id).toList();
    }

    public List<WeaveLink> linksInto(int id) {
        return links.stream().filter(link -> link.to() == id).toList();
    }

    public Set<Integer> triggers() {
        return Collections.unmodifiableSet(triggers.keySet());
    }

    public WeaveTrigger.@Nullable Kind kind(int trigger) {
        return triggers.get(trigger);
    }

    public String key(int trigger) {
        return keys.getOrDefault(trigger, "");
    }

    public int reach(int trigger) {
        return reaches.getOrDefault(trigger, 0);
    }

    public void setReach(int trigger, int reach) {
        WeaveTrigger.Kind kind = triggers.get(trigger);
        int held = Math.max(WeaveTrigger.MIN_REACH, Math.min(WeaveTrigger.MAX_REACH, reach));
        if (kind == null || !kind.ranged() || reach(trigger) == held) {
            return;
        }
        reaches.put(trigger, held);
        edits++;
    }

    public List<TriggerLink> triggerLinks() {
        return Collections.unmodifiableList(triggerLinks);
    }

    public List<TriggerLink> triggerLinksFrom(int trigger) {
        return triggerLinks.stream().filter(link -> link.trigger() == trigger).toList();
    }

    public List<TriggerLink> triggerLinksInto(int thread) {
        return triggerLinks.stream().filter(link -> link.thread() == thread).toList();
    }

    public boolean entered(int thread) {
        return links.stream().anyMatch(link -> link.to() == thread)
            || triggerLinks.stream().anyMatch(link -> link.thread() == thread);
    }

    public @Nullable WeaveLayout.Position position(int id) {
        return positions.get(id);
    }

    public @Nullable WeaveLayout.Position triggerPosition(int trigger) {
        return triggerPositions.get(trigger);
    }

    public void move(int id, float x, float y) {
        if (threads.containsKey(id)) {
            positions.put(id, WeaveLayout.Position.clamped(x, y));
            edits++;
        }
    }

    public void moveTrigger(int trigger, float x, float y) {
        if (triggers.containsKey(trigger)) {
            triggerPositions.put(trigger, WeaveLayout.Position.clamped(x, y));
            edits++;
        }
    }

    public @Nullable ThreadDraft addThread(WeaveLayout.Position at) {
        if (threads.size() >= Hexweave.MAX_THREADS || nextId > Integer.MAX_VALUE) {
            return null;
        }
        int id = (int) nextId++;
        ThreadDraft thread = ThreadDraft.empty(id);
        threads.put(id, thread);
        positions.put(id, WeaveLayout.Position.clamped(at.x(), at.y()));
        edits++;
        return thread;
    }

    public void removeThread(int id) {
        ThreadDraft removed = threads.remove(id);
        if (removed == null) {
            return;
        }
        positions.remove(id);
        links.removeIf(link -> link.from() == id || link.to() == id);
        triggerLinks.removeIf(link -> link.thread() == id);
        edits += removed.edits() + 1;
        renumberThreads();
    }

    private void renumberThreads() {
        Map<Integer, Integer> numbers = numbering(threads.keySet());
        List<ThreadDraft> kept = new ArrayList<>(threads.values());
        Map<Integer, WeaveLayout.Position> moved = new HashMap<>();
        threads.clear();
        for (ThreadDraft thread : kept) {
            int number = numbers.get(thread.id());
            moved.put(number, positions.get(thread.id()));
            thread.renumber(number);
            threads.put(number, thread);
        }
        positions.clear();
        positions.putAll(moved);
        links.replaceAll(link -> new WeaveLink(numbers.get(link.from()), numbers.get(link.to())));
        triggerLinks.replaceAll(link -> new TriggerLink(link.trigger(), numbers.get(link.thread())));
        nextId = threads.size() + 1L;
    }

    private static Map<Integer, Integer> numbering(Set<Integer> ids) {
        Map<Integer, Integer> numbers = new HashMap<>();
        for (int id : ids) {
            numbers.put(id, numbers.size() + 1);
        }
        return numbers;
    }

    public @Nullable Integer addTrigger(WeaveLayout.Position at) {
        if (triggers.size() >= Hexweave.MAX_TRIGGERS || nextTriggerId > Integer.MAX_VALUE) {
            return null;
        }
        int id = (int) nextTriggerId++;
        triggers.put(id, WeaveTrigger.Kind.ON_USE);
        triggerPositions.put(id, WeaveLayout.Position.clamped(at.x(), at.y()));
        edits++;
        return id;
    }

    public void removeTrigger(int trigger) {
        if (triggers.remove(trigger) != null) {
            keys.remove(trigger);
            reaches.remove(trigger);
            triggerPositions.remove(trigger);
            triggerLinks.removeIf(link -> link.trigger() == trigger);
            edits++;
            renumberTriggers();
        }
    }

    private void renumberTriggers() {
        Map<Integer, Integer> numbers = numbering(triggers.keySet());
        Map<Integer, WeaveTrigger.Kind> kinds = new TreeMap<>();
        Map<Integer, String> movedKeys = new HashMap<>();
        Map<Integer, Integer> movedReaches = new HashMap<>();
        Map<Integer, WeaveLayout.Position> moved = new HashMap<>();
        numbers.forEach((id, number) -> {
            kinds.put(number, triggers.get(id));
            moved.put(number, triggerPositions.get(id));
            if (keys.containsKey(id)) {
                movedKeys.put(number, keys.get(id));
            }
            if (reaches.containsKey(id)) {
                movedReaches.put(number, reaches.get(id));
            }
        });
        triggers.clear();
        triggers.putAll(kinds);
        keys.clear();
        keys.putAll(movedKeys);
        reaches.clear();
        reaches.putAll(movedReaches);
        triggerPositions.clear();
        triggerPositions.putAll(moved);
        triggerLinks.replaceAll(link -> new TriggerLink(numbers.get(link.trigger()), link.thread()));
        nextTriggerId = triggers.size() + 1L;
    }

    public void setKind(int trigger, WeaveTrigger.Kind kind, String key) {
        if (!triggers.containsKey(trigger) || kind.keyed() == key.isEmpty()
            || (triggers.get(trigger) == kind && key(trigger).equals(key))) {
            return;
        }
        triggers.put(trigger, kind);
        if (kind.keyed()) {
            keys.put(trigger, key);
        } else {
            keys.remove(trigger);
        }
        if (kind.ranged()) {
            reaches.putIfAbsent(trigger, kind.defaultReach());
        } else {
            reaches.remove(trigger);
        }
        edits++;
    }

    public LinkCheck checkLink(int from, int to) {
        if (!threads.containsKey(from) || !threads.containsKey(to)) {
            return LinkCheck.MISSING;
        }
        if (links.contains(new WeaveLink(from, to))) {
            return LinkCheck.DUPLICATE;
        }
        return full() ? LinkCheck.FULL : LinkCheck.OK;
    }

    public boolean link(int from, int to) {
        if (checkLink(from, to) != LinkCheck.OK) {
            return false;
        }
        links.add(new WeaveLink(from, to));
        edits++;
        return true;
    }

    public boolean unlink(WeaveLink link) {
        boolean removed = links.remove(link);
        if (removed) {
            edits++;
        }
        return removed;
    }

    public LinkCheck checkTriggerLink(int trigger, int thread) {
        if (!triggers.containsKey(trigger) || !threads.containsKey(thread)) {
            return LinkCheck.MISSING;
        }
        if (triggerLinks.contains(new TriggerLink(trigger, thread))) {
            return LinkCheck.DUPLICATE;
        }
        return full() ? LinkCheck.FULL : LinkCheck.OK;
    }

    public boolean linkTrigger(int trigger, int thread) {
        if (checkTriggerLink(trigger, thread) != LinkCheck.OK) {
            return false;
        }
        triggerLinks.add(new TriggerLink(trigger, thread));
        edits++;
        return true;
    }

    public boolean unlink(TriggerLink link) {
        boolean removed = triggerLinks.remove(link);
        if (removed) {
            edits++;
        }
        return removed;
    }

    private boolean full() {
        return links.size() + triggerLinks.size() >= Hexweave.MAX_LINKS;
    }

    public boolean starts(WeaveTrigger.Kind kind) {
        return triggerLinks.stream().anyMatch(link -> triggers.get(link.trigger()) == kind);
    }

    public Set<Integer> reachable() {
        Set<Integer> reached = new HashSet<>();
        Deque<Integer> frontier = new ArrayDeque<>();
        for (TriggerLink link : triggerLinks) {
            if (reached.add(link.thread())) {
                frontier.add(link.thread());
            }
        }
        while (!frontier.isEmpty()) {
            int id = frontier.poll();
            for (WeaveLink link : links) {
                if (link.from() == id && reached.add(link.to())) {
                    frontier.add(link.to());
                }
            }
        }
        return reached;
    }

    public Hexweave toWeave() {
        List<WeaveThread> built = new ArrayList<>(threads.size());
        for (ThreadDraft thread : threads.values()) {
            built.add(thread.toThread());
        }
        List<WeaveTrigger> builtTriggers = new ArrayList<>(triggers.size());
        triggers.forEach((id, kind) -> builtTriggers.add(new WeaveTrigger(id, kind, key(id), reach(id),
            triggerLinks.stream().filter(link -> link.trigger() == id).map(TriggerLink::thread).sorted().toList())));
        return Hexweave.of(built, links, builtTriggers);
    }

    public WeaveLayout layout() {
        return new WeaveLayout(positions, triggerPositions);
    }

    public Snapshot snapshot() {
        List<ThreadState> states = new ArrayList<>(threads.size());
        for (ThreadDraft thread : threads.values()) {
            states.add(new ThreadState(thread, thread.id(), thread.entries(), positions.get(thread.id())));
        }
        return new Snapshot(List.copyOf(states), List.copyOf(links), Map.copyOf(triggers), Map.copyOf(keys),
            Map.copyOf(reaches), List.copyOf(triggerLinks), Map.copyOf(triggerPositions), nextId, nextTriggerId);
    }

    public void restore(Snapshot snapshot) {
        int before = edits();
        threads.clear();
        positions.clear();
        for (ThreadState state : snapshot.threads()) {
            state.thread().restore(state.id(), state.entries());
            threads.put(state.id(), state.thread());
            positions.put(state.id(), state.position());
        }
        links.clear();
        links.addAll(snapshot.links());
        triggers.clear();
        triggers.putAll(snapshot.triggers());
        keys.clear();
        keys.putAll(snapshot.keys());
        reaches.clear();
        reaches.putAll(snapshot.reaches());
        triggerLinks.clear();
        triggerLinks.addAll(snapshot.triggerLinks());
        triggerPositions.clear();
        triggerPositions.putAll(snapshot.triggerPositions());
        nextId = snapshot.nextId();
        nextTriggerId = snapshot.nextTriggerId();
        edits += Math.max(1, before - edits() + 1);
    }

    public int edits() {
        int total = edits;
        for (ThreadDraft thread : threads.values()) {
            total += thread.edits();
        }
        return total;
    }
}

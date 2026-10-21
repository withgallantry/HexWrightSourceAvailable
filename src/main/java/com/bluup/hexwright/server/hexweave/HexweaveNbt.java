package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HexweaveNbt {

    public static final int VERSION = 3;

    private static final String VERSION_KEY = "Version";
    private static final String THREADS = "Threads";
    private static final String LINKS = "Links";
    private static final String TRIGGERS = "Triggers";
    private static final String ID = "Id";
    private static final String KIND = "Kind";
    private static final String KEY = "Key";
    private static final String REACH = "Reach";
    private static final String INGRESS = "Ingress";
    private static final String EGRESS = "Egress";
    private static final String PATTERNS = "Patterns";
    private static final String PATTERN = "Pattern";
    private static final String IOTA = "Iota";
    private static final String ORIGIN_Q = "OriginQ";
    private static final String ORIGIN_R = "OriginR";
    private static final String FROM = "From";
    private static final String TO = "To";
    private static final String LAYOUT = "Layout";
    private static final String X = "X";
    private static final String Y = "Y";

    public record Summary(int version, int threads, int links) {

        public boolean readable() {
            return version >= 1 && version <= VERSION;
        }
    }

    private HexweaveNbt() {
    }

    public static Summary summarize(CompoundTag tag) {
        int version = tag.contains(VERSION_KEY, Tag.TAG_INT) ? tag.getInt(VERSION_KEY) : -1;
        ListTag threads = tag.getList(THREADS, Tag.TAG_COMPOUND);
        int links = tag.getList(LINKS, Tag.TAG_COMPOUND).size();
        if (version == 1) {
            for (int i = 0; i < threads.size(); i++) {
                links += threads.getCompound(i).getBoolean(INGRESS) ? 0 : 1;
            }
        } else {
            ListTag triggers = tag.getList(TRIGGERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < triggers.size(); i++) {
                links += triggers.getCompound(i).getIntArray(THREADS).length;
            }
        }
        return new Summary(version, threads.size(), links);
    }

    public static List<WeaveTrigger> peekTriggers(CompoundTag tag) {
        List<WeaveTrigger> found = new ArrayList<>();
        ListTag triggers = tag.getList(TRIGGERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < triggers.size() && i < Hexweave.MAX_TRIGGERS; i++) {
            CompoundTag entry = triggers.getCompound(i);
            WeaveTrigger.Kind kind = WeaveTrigger.Kind.byId(entry.getString(KIND));
            int[] targets = entry.getIntArray(THREADS);
            if (kind != null && targets.length > 0) {
                int reach = !kind.ranged() ? 0 : entry.contains(REACH, Tag.TAG_INT)
                    ? Math.max(WeaveTrigger.MIN_REACH, Math.min(WeaveTrigger.MAX_REACH, entry.getInt(REACH)))
                    : WeaveTrigger.DEFAULT_REACH;
                found.add(new WeaveTrigger(entry.getInt(ID), kind, entry.getString(KEY), reach,
                    Arrays.stream(targets).boxed().toList()));
            }
        }
        return found;
    }

    public static int peekPatternCount(CompoundTag tag) {
        ListTag threads = tag.getList(THREADS, Tag.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < threads.size(); i++) {
            count += threads.getCompound(i).getList(PATTERNS, Tag.TAG_COMPOUND).size();
        }
        return count;
    }

    public static @Nullable HexPattern peekPattern(CompoundTag tag, int index) {
        ListTag threads = tag.getList(THREADS, Tag.TAG_COMPOUND);
        for (int i = 0; i < threads.size(); i++) {
            ListTag patterns = threads.getCompound(i).getList(PATTERNS, Tag.TAG_COMPOUND);
            if (index < patterns.size()) {
                try {
                    return readPlaced(patterns.getCompound(index), "A shown pattern").pattern();
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            index -= patterns.size();
        }
        return null;
    }

    public static CompoundTag displaySample(CompoundTag tag, int limit) {
        ListTag sample = new ListTag();
        ListTag threads = tag.getList(THREADS, Tag.TAG_COMPOUND);
        for (int i = 0; i < threads.size() && sample.size() < limit; i++) {
            ListTag patterns = threads.getCompound(i).getList(PATTERNS, Tag.TAG_COMPOUND);
            for (int j = 0; j < patterns.size() && sample.size() < limit; j++) {
                if (patterns.getCompound(j).contains(PATTERN, Tag.TAG_COMPOUND)) {
                    sample.add(patterns.getCompound(j).copy());
                }
            }
        }
        CompoundTag thread = new CompoundTag();
        thread.put(PATTERNS, sample);
        ListTag sampleThreads = new ListTag();
        sampleThreads.add(thread);
        CompoundTag stored = new CompoundTag();
        stored.put(THREADS, sampleThreads);
        return stored;
    }

    public static CompoundTag write(Hexweave weave) {
        ListTag threads = new ListTag();
        for (WeaveThread thread : weave.threads()) {
            ListTag patterns = new ListTag();
            for (ThreadEntry placed : thread.entries()) {
                CompoundTag entry = new CompoundTag();
                if (placed instanceof PlacedPattern pattern) {
                    entry.put(PATTERN, patternTag(pattern.pattern()));
                } else if (placed instanceof EmbeddedIota embedded) {
                    entry.put(IOTA, embedded.iota().copy());
                }
                entry.putInt(ORIGIN_Q, placed.origin().getQ());
                entry.putInt(ORIGIN_R, placed.origin().getR());
                patterns.add(entry);
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(ID, thread.id());
            entry.put(PATTERNS, patterns);
            threads.add(entry);
        }
        ListTag links = new ListTag();
        for (WeaveLink link : weave.links()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(FROM, link.from());
            entry.putInt(TO, link.to());
            links.add(entry);
        }
        ListTag triggers = new ListTag();
        for (WeaveTrigger trigger : weave.triggers()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(ID, trigger.id());
            entry.putString(KIND, trigger.kind().id());
            if (trigger.kind().keyed()) {
                entry.putString(KEY, trigger.key());
            }
            if (trigger.kind().ranged()) {
                entry.putInt(REACH, trigger.reach());
            }
            entry.putIntArray(THREADS, trigger.targets());
            triggers.add(entry);
        }

        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, VERSION);
        tag.put(THREADS, threads);
        tag.put(LINKS, links);
        tag.put(TRIGGERS, triggers);
        return tag;
    }

    public static CompoundTag write(Hexweave weave, WeaveLayout layout) {
        ListTag threads = new ListTag();
        for (WeaveThread thread : weave.threads()) {
            positionEntry(threads, thread.id(), layout.position(thread.id()));
        }
        ListTag triggers = new ListTag();
        for (WeaveTrigger trigger : weave.triggers()) {
            positionEntry(triggers, trigger.id(), layout.triggerPosition(trigger.id()));
        }
        CompoundTag layoutTag = new CompoundTag();
        layoutTag.put(THREADS, threads);
        layoutTag.put(TRIGGERS, triggers);
        CompoundTag tag = write(weave);
        tag.put(LAYOUT, layoutTag);
        return tag;
    }

    private static void positionEntry(ListTag list, int id, WeaveLayout.Position at) {
        if (at != null) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(ID, id);
            entry.putFloat(X, at.x());
            entry.putFloat(Y, at.y());
            list.add(entry);
        }
    }

    public static WeaveLayout readLayout(CompoundTag tag) {
        if (!tag.contains(LAYOUT, Tag.TAG_COMPOUND)) {
            return WeaveLayout.NONE;
        }
        CompoundTag layout = tag.getCompound(LAYOUT);
        Map<Integer, WeaveLayout.Position> threads = new HashMap<>();
        Map<Integer, WeaveLayout.Position> triggers = new HashMap<>();
        int skipped = readPositions(layout.getList(THREADS, Tag.TAG_COMPOUND), Hexweave.MAX_THREADS, threads)
            + readPositions(layout.getList(TRIGGERS, Tag.TAG_COMPOUND), Hexweave.MAX_TRIGGERS, triggers);
        if (skipped > 0) {
            HexwrightDebug.log(HexwrightDebug.WEAVE, "[weave] skipped {} unusable Loom layout entries", skipped);
        }
        return new WeaveLayout(threads, triggers);
    }

    private static int readPositions(ListTag entries, int limit, Map<Integer, WeaveLayout.Position> positions) {
        int skipped = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (i < limit && entry.contains(ID, Tag.TAG_INT) && entry.contains(X, Tag.TAG_FLOAT)
                && entry.contains(Y, Tag.TAG_FLOAT)) {
                float x = entry.getFloat(X);
                float y = entry.getFloat(Y);
                if (Float.isFinite(x) && Float.isFinite(y)
                    && positions.putIfAbsent(entry.getInt(ID), WeaveLayout.Position.clamped(x, y)) == null) {
                    continue;
                }
            }
            skipped++;
        }
        return skipped;
    }

    private static CompoundTag patternTag(HexPattern pattern) {
        List<HexAngle> angles = pattern.getAngles();
        byte[] angleBytes = new byte[angles.size()];
        for (int i = 0; i < angleBytes.length; i++) {
            angleBytes[i] = (byte) angles.get(i).ordinal();
        }
        CompoundTag tag = new CompoundTag();
        tag.putByte(HexPattern.TAG_START_DIR, (byte) pattern.getStartDir().ordinal());
        tag.putByteArray(HexPattern.TAG_ANGLES, angleBytes);
        return tag;
    }

    public static Hexweave read(CompoundTag tag) {
        try {
            if (!tag.contains(VERSION_KEY, Tag.TAG_INT)) {
                throw new IllegalArgumentException("The stored weave has no Version.");
            }
            int version = tag.getInt(VERSION_KEY);
            return switch (version) {
                case 1 -> readV1(tag);
                case 2, 3 -> readV2(tag);
                default -> throw new IllegalArgumentException("The stored weave is Version " + version
                    + "; this build reads Versions 1 to " + VERSION + ".");
            };
        } catch (IllegalArgumentException e) {
            Hexwright.LOGGER.warn("Refusing a stored Hexweave: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Refusing an unreadable stored Hexweave", e);
            throw new IllegalArgumentException("The stored weave could not be read: " + e, e);
        }
    }

    private static Hexweave readV2(CompoundTag tag) {
        List<WeaveThread> threads = readThreads(tag);
        List<WeaveLink> links = readLinks(tag);
        ListTag triggerTags = compounds(tag, TRIGGERS, Hexweave.MAX_TRIGGERS, "The weave");
        List<WeaveTrigger> triggers = new ArrayList<>(triggerTags.size());
        for (int i = 0; i < triggerTags.size(); i++) {
            CompoundTag entry = triggerTags.getCompound(i);
            String where = "Trigger entry " + (i + 1);
            if (!entry.contains(KIND, Tag.TAG_STRING)) {
                throw new IllegalArgumentException(where + " has no Kind.");
            }
            WeaveTrigger.Kind kind = WeaveTrigger.Kind.byId(entry.getString(KIND));
            if (kind == null) {
                throw new IllegalArgumentException(where + " waits for '" + entry.getString(KIND)
                    + "', which this build doesn't know.");
            }
            if (!entry.contains(THREADS, Tag.TAG_INT_ARRAY)) {
                throw new IllegalArgumentException(where + " has no list of Threads.");
            }
            int[] targets = entry.getIntArray(THREADS);
            if (targets.length > Hexweave.MAX_LINKS) {
                throw new IllegalArgumentException(where + " starts " + targets.length + " Threads; the limit is "
                    + Hexweave.MAX_LINKS + ".");
            }
            if (entry.contains(KEY) && !entry.contains(KEY, Tag.TAG_STRING)) {
                throw new IllegalArgumentException(where + " has a Key that isn't text.");
            }
            int reach = entry.contains(REACH) ? integer(entry, REACH, where) : kind.defaultReach();
            triggers.add(new WeaveTrigger(integer(entry, ID, where), kind, entry.getString(KEY), reach,
                Arrays.stream(targets).boxed().toList()));
        }
        return Hexweave.of(threads, links, triggers);
    }

    private static Hexweave readV1(CompoundTag tag) {
        List<WeaveThread> threads = readThreads(tag);
        ListTag threadTags = tag.getList(THREADS, Tag.TAG_COMPOUND);
        Set<Integer> eye = new HashSet<>();
        Set<Integer> tail = new HashSet<>();
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < threadTags.size(); i++) {
            CompoundTag entry = threadTags.getCompound(i);
            String where = "Thread entry " + (i + 1);
            int id = threads.get(i).id();
            if (flag(entry, INGRESS, where)) {
                eye.add(id);
            } else {
                roots.add(id);
            }
            if (flag(entry, EGRESS, where)) {
                tail.add(id);
            }
        }
        Set<Integer> known = new HashSet<>();
        threads.forEach(thread -> known.add(thread.id()));
        List<WeaveLink> links = readLinks(tag);
        for (WeaveLink link : links) {
            if (known.contains(link.from()) && !tail.contains(link.from())) {
                throw new IllegalArgumentException("The link " + link.from() + ">" + link.to() + " leaves Thread "
                    + link.from() + ", which has no Tail.");
            }
            if (known.contains(link.to()) && !eye.contains(link.to())) {
                throw new IllegalArgumentException("The link " + link.from() + ">" + link.to() + " enters Thread "
                    + link.to() + ", which has no Eye.");
            }
        }
        List<WeaveTrigger> triggers = roots.isEmpty() ? List.of()
            : List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, roots));
        return Hexweave.of(threads, links, triggers);
    }

    private static List<WeaveThread> readThreads(CompoundTag tag) {
        ListTag threadTags = compounds(tag, THREADS, Hexweave.MAX_THREADS, "The weave");
        List<WeaveThread> threads = new ArrayList<>(threadTags.size());
        int patterns = 0;
        for (int i = 0; i < threadTags.size(); i++) {
            CompoundTag entry = threadTags.getCompound(i);
            String where = "Thread entry " + (i + 1);
            ListTag patternTags = compounds(entry, PATTERNS, Hexweave.MAX_PATTERNS_PER_THREAD, where);
            patterns += patternTags.size();
            if (patterns > Hexweave.MAX_PATTERNS) {
                throw new IllegalArgumentException("The weave holds more than " + Hexweave.MAX_PATTERNS
                    + " patterns.");
            }
            List<ThreadEntry> placed = new ArrayList<>(patternTags.size());
            for (int j = 0; j < patternTags.size(); j++) {
                CompoundTag entryTag = patternTags.getCompound(j);
                String entryWhere = where + ", entry " + (j + 1);
                placed.add(entryTag.contains(IOTA) ? readEmbedded(entryTag, entryWhere) : readPlaced(entryTag, entryWhere));
            }
            threads.add(new WeaveThread(integer(entry, ID, where), placed));
        }
        return threads;
    }

    private static List<WeaveLink> readLinks(CompoundTag tag) {
        ListTag linkTags = compounds(tag, LINKS, Hexweave.MAX_LINKS, "The weave");
        List<WeaveLink> links = new ArrayList<>(linkTags.size());
        for (int i = 0; i < linkTags.size(); i++) {
            CompoundTag entry = linkTags.getCompound(i);
            String where = "Link entry " + (i + 1);
            links.add(new WeaveLink(integer(entry, FROM, where), integer(entry, TO, where)));
        }
        return links;
    }

    private static PlacedPattern readPlaced(CompoundTag entry, String where) {
        if (!entry.contains(PATTERN, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(where + " has no Pattern.");
        }
        CompoundTag pattern = entry.getCompound(PATTERN);
        if (!pattern.contains(HexPattern.TAG_START_DIR, Tag.TAG_BYTE)
            || !pattern.contains(HexPattern.TAG_ANGLES, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException(where + " is not a pattern tag.");
        }
        byte[] angleBytes = pattern.getByteArray(HexPattern.TAG_ANGLES);
        if (angleBytes.length > Hexweave.MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException(where + " has " + angleBytes.length + " angles; the limit is "
                + Hexweave.MAX_PATTERN_LENGTH + ".");
        }
        HexDir startDir = ordinal(HexDir.values(), pattern.getByte(HexPattern.TAG_START_DIR), where);
        List<HexAngle> angles = new ArrayList<>(angleBytes.length);
        for (byte angle : angleBytes) {
            angles.add(ordinal(HexAngle.values(), angle, where));
        }
        HexCoord origin = new HexCoord(integer(entry, ORIGIN_Q, where), integer(entry, ORIGIN_R, where));
        return new PlacedPattern(new HexPattern(startDir, angles), origin);
    }

    private static EmbeddedIota readEmbedded(CompoundTag entry, String where) {
        if (!entry.contains(IOTA, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(where + " embeds something that isn't an iota.");
        }
        HexCoord origin = new HexCoord(integer(entry, ORIGIN_Q, where), integer(entry, ORIGIN_R, where));
        return new EmbeddedIota(entry.getCompound(IOTA), origin);
    }

    private static ListTag compounds(CompoundTag parent, String key, int limit, String where) {
        if (!(parent.get(key) instanceof ListTag list)
            || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(where + " has no list of " + key + ".");
        }
        if (list.size() > limit) {
            throw new IllegalArgumentException(where + " lists " + list.size() + " " + key + "; the limit is "
                + limit + ".");
        }
        return list;
    }

    private static int integer(CompoundTag entry, String key, String where) {
        if (!entry.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException(where + " has no whole-number " + key + ".");
        }
        return entry.getInt(key);
    }

    private static boolean flag(CompoundTag entry, String key, String where) {
        if (!entry.contains(key, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException(where + " does not say whether it has an " + key + ".");
        }
        return entry.getBoolean(key);
    }

    private static <T> T ordinal(T[] values, byte ordinal, String where) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(where + " holds " + ordinal + ", which is not a direction.");
        }
        return values[ordinal];
    }
}
